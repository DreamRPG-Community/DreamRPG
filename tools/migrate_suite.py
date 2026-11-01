"""Version 1 offline suite migration. No player rows, item IDs or chest keys are rewritten."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import yaml

VERSION = 1
JARS = ['DreamLib', 'DreamWorld', 'DreamNPC', 'DreamChest', 'DreamRPG']
RENAMES = {'DreamDepend': 'DreamLib', 'WorldManager': 'DreamWorld', 'CustomNPC': 'DreamNPC', 'MagicChest': 'DreamChest'}
RETIRED = ['CombatSystem', 'MythicMobsAddon', 'WorldProtect', 'WorldRegion']

def read(path):
    return yaml.safe_load(path.read_text(encoding='utf8')) or {}

def encode(value):
    return yaml.safe_dump(value, allow_unicode=True, sort_keys=False).encode('utf8')

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def differences(value, defaults):
    result = {}
    for key, entry in value.items():
        if isinstance(entry, dict) and isinstance(defaults.get(key), dict):
            changed = differences(entry, defaults[key])
            if changed:
                result[key] = changed
        elif key in {'block-ids', 'names'} and isinstance(entry, list) and isinstance(defaults.get(key), list):
            if set(entry) != set(defaults[key]):
                result[key] = entry
        elif key not in defaults or entry != defaults[key]:
            result[key] = entry
    return result

def merge(*configs):
    result = {}
    for config in configs:
        overlap = result.keys() & config.keys()
        if overlap:
            raise ValueError('Duplicate configuration roots: ' + ', '.join(overlap))
        result.update(config)
    return result

def plan(root, release):
    plugins = root / 'plugins'
    writes, deletes, moves = {}, [], []
    for old, new in RENAMES.items():
        if (plugins / new).exists():
            raise ValueError(f'Destination already exists: {new}; migration will not overwrite data')
        if (plugins / old).exists():
            moves.append((plugins / old, plugins / new))
    core = read(plugins / 'DreamRPG/config.yml')
    world = read(plugins / 'WorldManager/config.yml')
    protection = read(plugins / 'WorldProtect/config.yml')
    spawns = read(plugins / 'WorldManager/spawns.yml')['spawns']
    main = core['spawn']['world']
    if main not in spawns:
        raise ValueError('DreamWorld spawn is missing for main city: ' + main)
    for key in ['x', 'y', 'z', 'yaw', 'pitch']:
        if core['spawn'].get(key) != spawns[main][key]:
            raise ValueError('Conflicting main city spawn: ' + key)
        del core['spawn'][key]
    core = merge(core, read(plugins / 'CombatSystem/config.yml'), read(plugins / 'MythicMobsAddon/config.yml'))
    writes[plugins / 'DreamRPG/config.yml'] = encode(core)
    world = merge(world, protection, read(plugins / 'WorldRegion/config.yml'))
    world['world-config-directory'] = 'protection'
    writes[plugins / 'DreamWorld/config.yml'] = encode(world)
    exception_count = 0
    for path in (plugins / 'WorldProtect/worlds').rglob('*.yml'):
        delta = differences(read(path), protection['world-defaults'])
        if delta:
            writes[plugins / 'DreamWorld/protection' / path.relative_to(plugins / 'WorldProtect/worlds')] = encode(delta)
            exception_count += 1
    for old, new in [('WorldRegion', 'DreamWorld'), ('MythicMobsAddon', 'DreamRPG')]:
        for path in (plugins / old).rglob('*'):
            if not path.is_file() or path.name == 'config.yml':
                continue
            target = plugins / new / path.relative_to(plugins / old)
            if target in writes or target.exists():
                raise ValueError('Conflicting merged resource: ' + str(target))
            writes[target] = path.read_bytes()
    waypoints = read(plugins / 'DreamRPG/waypoints.yml')
    inherited = 0
    for point in waypoints['waypoints'].values():
        spawn = spawns.get(point['world'])
        if spawn and all(point.get(key) == spawn[key] for key in ['x', 'y', 'z', 'yaw', 'pitch']):
            for key in ['x', 'y', 'z', 'yaw', 'pitch']:
                del point[key]
            inherited += 1
    writes[plugins / 'DreamRPG/waypoints.yml'] = encode(waypoints)
    writes[plugins / 'DreamRPG/stages.yml'] = (release / 'stages.yml').read_bytes()
    removed_exp_drops = 0
    for file in (plugins / 'MythicMobs/Mobs/main_city').glob('*.yml'):
        definitions = read(file)
        changed = False
        for mob in definitions.values():
            drops = mob.get('Drops', [])
            retained = [drop for drop in drops if not str(drop).lower().startswith('exp ')]
            if len(retained) != len(drops):
                removed_exp_drops += len(drops) - len(retained)
                mob['Drops'] = retained
                changed = True
        if changed:
            writes[file] = encode(definitions)
    shop_config = plugins / 'Shopkeepers/config.yml'
    # Keep the existing English message catalog; localize only the real editor window title.
    shop_text = shop_config.read_text(encoding='utf8')
    shop_text = shop_text.replace('editor-title: "Shopkeeper Editor"', 'editor-title: "交易编辑"')
    writes[shop_config] = shop_text.encode('utf8')
    deletes.append(plugins / 'DreamRPG/campaign.yml')
    for name in JARS:
        source = release / (name + '.jar')
        if not source.is_file():
            raise ValueError('Missing release artifact: ' + name)
        writes[plugins / (name + '.jar')] = source.read_bytes()
    for name in list(RENAMES) + RETIRED:
        deletes.append(plugins / (name + '.jar'))
    # Serialized item NBT keeps the MythicMobsAddon namespace; only the source directory changes.
    item_source = plugins / 'MythicMobs/Items/MythicMobsAddon'
    item_target = plugins / 'MythicMobs/Items/DreamRPG'
    if item_target.exists():
        raise ValueError('MM item destination exists; refusing to shadow the current library')
    moves.append((item_source, item_target))
    # World names are logical wherever possible; raw Bukkit runtime paths must follow the rename.
    for owner in ['DreamRPG', 'CustomNPC', 'MagicChest', 'Shopkeepers', 'MythicMobs', 'WorldRegion']:
        for file in (plugins / owner).rglob('*.yml'):
            target = file
            for old, new in RENAMES.items():
                if file.is_relative_to(plugins / old):
                    target = plugins / new / file.relative_to(plugins / old)
            if owner == 'WorldRegion':
                target = plugins / 'DreamWorld' / file.relative_to(plugins / owner)
            if file.is_relative_to(item_source):
                target = item_target / file.relative_to(item_source)
            raw = writes.get(target, file.read_bytes())
            changed = raw.replace(b'plugins/WorldManager/', b'plugins/DreamWorld/')
            if changed != raw:
                writes[target] = changed
    for name in ['bukkit.yml', 'spigot.yml', 'paper.yml', 'server.properties']:
        file = root / name
        if file.exists():
            raw = file.read_bytes()
            changed = raw.replace(b'plugins/WorldManager/', b'plugins/DreamWorld/')
            if changed != raw:
                writes[file] = changed
    report = {'version': VERSION, 'worlds': len(spawns), 'protection_exceptions': exception_count,
              'waypoints_using_spawn': inherited, 'removed_redundant_mm_exp_entries': removed_exp_drops,
              'jars': {name: digest(release / (name + '.jar')) for name in JARS}}
    return writes, deletes, moves, report

def migrate(root, release, apply=False):
    root, release = root.resolve(), release.resolve()
    writes, deletes, moves, report = plan(root, release)
    for target in list(writes) + deletes + [p for pair in moves for p in pair]:
        if not target.resolve().is_relative_to(root):
            raise ValueError('Migration target escaped instance directory: ' + str(target))
    if not apply:
        return {'dry_run': True, **report}
    # Keep only the bytes required for an immediate failed-operation rollback in memory.
    # No persistent configuration/database backups are created.
    originals = {}
    completed_moves = []
    obsolete_dirs = []
    databases = [root / 'plugins/DreamRPG/profiles.db', root / 'plugins/MagicChest/claims.db']
    db_hashes = {p.relative_to(root).as_posix(): digest(p) for p in databases if p.exists()}
    try:
        for source, target in moves:
            target.parent.mkdir(parents=True, exist_ok=True)
            source.rename(target)
            completed_moves.append((source, target))
        for path in set(writes) | set(deletes):
            originals[path] = path.read_bytes() if path.is_file() else None
        for path, content in writes.items():
            path.parent.mkdir(parents=True, exist_ok=True)
            temporary = path.with_name(path.name + '.suite-new')
            temporary.write_bytes(content)
            os.replace(temporary, path)
        for path in deletes:
            path.unlink(missing_ok=True)
        for old in RETIRED:
            directory = root / 'plugins' / old
            if directory.exists():
                # All retained domain resources have already been merged; keep original bytes in
                # memory until the migration completes, never touching claims or profiles.
                obsolete_dirs.append((directory, {p.relative_to(directory): p.read_bytes() for p in directory.rglob('*') if p.is_file()}))
                shutil.rmtree(directory)
        for relative, expected in db_hashes.items():
            current = root / relative.replace('/MagicChest/', '/DreamChest/')
            if digest(current) != expected:
                raise ValueError('Player database changed during suite migration: ' + relative)
        report['database_hashes_before_migration'] = db_hashes
    except BaseException:
        for directory, files in obsolete_dirs:
            for relative, content in files.items():
                path = directory / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(content)
        for path, content in originals.items():
            if content is None:
                path.unlink(missing_ok=True)
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(content)
        for source, target in reversed(completed_moves):
            target.rename(source)
        raise
    return report

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('root', type=Path)
    parser.add_argument('release', type=Path)
    parser.add_argument('--apply', action='store_true', help='Only run after the instance stopped normally')
    arguments = parser.parse_args()
    print(json.dumps(migrate(arguments.root, arguments.release, arguments.apply), ensure_ascii=False, indent=2))
