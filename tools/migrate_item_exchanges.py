"""Explicit v1 stage/Shopkeepers conversion. Does not read or write player databases."""
from collections import Counter
from pathlib import Path
import argparse
import base64
import gzip
import json
import os
import struct
import yaml

GROUPS = {
    '67f105db-0314-3dbc-bd98-8309e0830a74': 'village_cooking',
    '3c17e229-9785-3f1e-82b0-ece0f981a78d': 'ocean_heart_boots',
    '93d43b0f-386e-343e-9671-31ad3048e6c0': 'ocean_heart_leggings',
    '83030f68-6a05-3e54-9989-7f9cf21147fd': 'ocean_heart_chestplate',
    '63ad8b62-0b34-34bb-bd21-9cdd2ced0381': 'ocean_heart_helmet',
    'f15764de-3d9b-344d-bc1e-5e37d81c8da3': 'demon_slayer_weapons',
    'a62c201d-a0af-39d9-90bc-24f15ae3fccb': 'starter_equipment',
    'de2ded2a-8d3e-3263-b892-ecc3e9a3a684': 'village_currency',
}


def reference(stack, items):
    blob = gzip.decompress(base64.b64decode(stack['meta']['internal'], validate=True))
    needle = b'\x08\x00\x07item-id'
    if blob.count(needle) != 1:
        raise ValueError('Every exchange stack must have exactly one RPG item identity')
    offset = blob.index(needle) + len(needle)
    length = struct.unpack('>H', blob[offset:offset + 2])[0]
    identifier = blob[offset + 2:offset + 2 + length].decode()
    if identifier not in items:
        raise ValueError('Unknown RPG item: ' + identifier)
    amount = stack.get('amount', 1)
    if type(amount) is not int or not 1 <= amount <= 2304:
        raise ValueError('Invalid exchange amount: ' + identifier)
    return {'item': identifier, 'amount': amount}


def normalized_shops(shops, items):
    result = []
    for key, shop in shops.items():
        recipes = []
        for recipe_id, recipe in shop['recipes'].items():
            recipes.append({'key': str(recipe_id),
                            'requires': [reference(recipe[field], items) for field in ['item1', 'item2'] if isinstance(recipe.get(field), dict)],
                            'reward': reference(recipe['resultItem'], items)})
        result.append({'id': str(key), 'name': shop['name'], 'npc': shop['object']['customnpc-id'], 'recipes': recipes})
    return result


def signature(npc, offer):
    return (npc, tuple((cost['item'], cost['amount']) for cost in offer['requires']), offer['reward']['item'], offer['reward']['amount'])


def convert(stages, shops):
    if stages['version'] != 1:
        raise ValueError('Source stages must be version 1')
    chapters = {'version': 2, 'stages': {}}
    exchanges = {'version': 1, 'exchanges': {}}
    book_stage = {}
    for stage_id, stage in stages['stages'].items():
        chapters['stages'][stage_id] = {'name': stage['name'], 'description': stage.get('description', [])[:1], 'tasks': {}}
        for book in stage.get('books', []):
            if book in book_stage:
                raise ValueError('Mainline book belongs to multiple stages: ' + book)
            book_stage[book] = stage_id
    source, converted = Counter(), Counter()
    identifiers = set()
    for shop in shops:
        npc = shop['npc']
        for recipe in shop['recipes']:
            source[signature(npc, recipe)] += 1
            input_books = [cost['item'] for cost in recipe['requires'] if cost['item'].startswith('mainline_book_')]
            reward = recipe['reward']['item']
            offer = {'requires': recipe['requires'], 'reward': recipe['reward']}
            if input_books and reward.startswith('mainline_book_'):
                if len(input_books) != 1 or input_books[0] not in book_stage:
                    raise ValueError('Task requires an unambiguous chapter: ' + input_books[0])
                identifier = input_books[0].removeprefix('mainline_book_')
                offer = {'npc': npc, **offer}
                chapter = chapters['stages'][book_stage[input_books[0]]]
                target = chapter['tasks']
            else:
                group_id = GROUPS[npc]
                group = exchanges['exchanges'].setdefault(group_id, {'name': shop['name'], 'npc': npc, 'offers': {}})
                target = group['offers']
                if group_id == 'village_currency':
                    identifier = recipe['requires'][0]['item'].removeprefix('main_village_currency_') + '_to_' + reward.removeprefix('main_village_currency_')
                elif group_id == 'village_cooking':
                    identifier = 'cook_white_chicken'
                elif reward == 'mainline_book_story_origin':
                    identifier = 'buy_story_origin'
                else:
                    identifier = reward.removeprefix('main_village_equipment_').removeprefix('main_village_weapon_')
                    if identifier in identifiers:
                        identifier += '_downgrade'
            if identifier in identifiers:
                raise ValueError('Duplicate converted offer ID: ' + identifier)
            identifiers.add(identifier)
            target[identifier] = offer
            converted[signature(npc, offer)] += 1
    for stage_id, chapter in chapters['stages'].items():
        order = {book: index for index, book in enumerate(stages['stages'][stage_id].get('books', []))}
        chapter['tasks'] = dict(sorted(chapter['tasks'].items(), key=lambda entry: order[next(cost['item'] for cost in entry[1]['requires'] if cost['item'] in order)]))
    if source != converted:
        raise ValueError('The conversion changed costs, rewards, quantities or NPC ownership')
    report = {'stages': len(chapters['stages']), 'tasks_by_stage': {key: len(value['tasks']) for key, value in chapters['stages'].items()},
              'exchange_groups': len(exchanges['exchanges']), 'exchanges': sum(len(group['offers']) for group in exchanges['exchanges'].values()),
              'source_offers': sum(source.values()), 'all_costs_and_rewards_preserved': True}
    return chapters, exchanges, report


def plan(root):
    plugins = Path(root) / 'plugins'
    stages = yaml.safe_load((plugins / 'DreamRPG/stages.yml').read_text(encoding='utf8'))
    if stages.get('version') == 2:
        raise ValueError('Already migrated to item exchanges; no changes applied')
    items = yaml.safe_load((plugins / 'MythicMobs/Items/DreamRPG/items.yml').read_text(encoding='utf8'))
    shops = normalized_shops(yaml.safe_load((plugins / 'Shopkeepers/save.yml').read_text(encoding='utf8')), items)
    chapters, exchanges, report = convert(stages, shops)
    npc_path = plugins / 'DreamNPC/npcs.yml'
    npcs = yaml.safe_load(npc_path.read_text(encoding='utf8'))
    bound = {shop['npc'] for shop in shops}
    for npc_id in bound:
        if npc_id not in npcs['npcs']:
            raise ValueError('Shop references unknown NPC: ' + npc_id)
    for npc_id, npc in npcs['npcs'].items():
        npc.pop('shopkeeper-id', None)
        npc.pop('shopkeeper-name', None)
        if npc_id in bound:
            command = {'mode': 'player', 'command': 'drpg npc ' + npc_id}
            commands = npc.setdefault('commands', [])
            if command not in commands:
                commands.append(command)
    npc_config = plugins / 'DreamNPC/config.yml'
    config = yaml.safe_load(npc_config.read_text(encoding='utf8'))
    config.pop('shopkeeper', None)
    writes = {plugins / 'DreamRPG/stages.yml': chapters, plugins / 'DreamRPG/exchanges.yml': exchanges,
              npc_path: npcs, npc_config: config}
    return {path: yaml.safe_dump(value, sort_keys=False, allow_unicode=True).encode() for path, value in writes.items()}, report


def atomic_write(path, content):
    temporary = path.with_name(path.name + '.exchange.tmp')
    try:
        temporary.write_bytes(content)
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def apply(writes):
    previous = {path: path.read_bytes() if path.exists() else None for path in writes}
    committed = []
    try:
        for path, content in writes.items():
            atomic_write(path, content)
            committed.append(path)
    except BaseException:
        for path in reversed(committed):
            if previous[path] is None:
                path.unlink()
            else:
                atomic_write(path, previous[path])
        raise


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('root', type=Path)
    parser.add_argument('--apply', action='store_true')
    arguments = parser.parse_args()
    writes, report = plan(arguments.root)
    if arguments.apply:
        apply(writes)
    print(json.dumps(report, ensure_ascii=False, indent=2))
