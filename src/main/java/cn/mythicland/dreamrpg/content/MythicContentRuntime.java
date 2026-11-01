package cn.mythicland.dreamrpg.content;

import cn.mythicland.framework.bukkit.MainThread;
import cn.mythicland.framework.bukkit.content.ContentUpdate;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.api.bukkit.events.MythicReloadedEvent;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/** MM 4.12 adapter: reloads definitions without saving spawners or rebinding system settings. */
public final class MythicContentRuntime {
    private MythicContentRuntime() {
    }

    /**
     * Captures accepted registries for rollback without rewriting source files.
     * @param expected expected IDs per resource kind, or empty for a standalone native reload
     * @return native reload and its inverse; both run on the server thread
     * @throws IllegalStateException if the pinned MM registry layout is incompatible
     */
    public static ContentUpdate prepare(Map<String, Set<String>> expected) {
        MainThread.require("Mythic content reload requires the server thread");
        MythicMobs mm = MythicMobs.inst();
        List<Runnable> restores = new ArrayList<>();
        try {
            Class<?> adapter = mm.getClass().getClassLoader().loadClass("cn.mythicland.mythicmobs.content.ContentPaths");
            restores.add((Runnable) adapter.getMethod("snapshotSpawnerSources").invoke(null));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Incompatible MM content source adapter", failure);
        }
        capture(mm.getItemManager(), restores, "items", "itemFiles", "itemLoaders");
        capture(mm.getSkillManager(), restores, "skills", "skillFiles", "skillLoaders", "secondPass");
        capture(mm.getMobManager(), restores, "mmList", "mmHashcodeLookup", "mmDisplayLookup", "listMobStacks", "mmDefaultList");
        capture(mm.getDropManager(), restores, "dropTables", "legacyDropTables", "secondPass");
        capture(mm.getSpawnerManager(), restores, "listSpawners", "mmSpawners", "mmChunkSpawnerLookup", "mmSpawnerHashcodeLookup", "spawnerFiles", "spawnerLoaders", "secondPass");
        return new ContentUpdate(() -> {
            reload(mm);
            Map<String, Set<String>> loaded = Map.of(
                    "items", Set.copyOf(mm.getItemManager().getItemNames()),
                    "mobs", Set.copyOf(mm.getMobManager().getMobNames()),
                    "skills", Set.copyOf(mm.getSkillManager().getSkillNames()),
                    "droptables", mm.getDropManager().getDropTables().stream().map(table -> table.getInternalName()).collect(Collectors.toSet()),
                    "spawners", mm.getSpawnerManager().getSpawners().stream().map(spawner -> spawner.getName()).collect(Collectors.toSet())
            );
            expected.forEach((kind, ids) -> {
                if (ids.equals(loaded.get(kind))) return;
                Set<String> missing = new HashSet<>(ids);
                missing.removeAll(loaded.get(kind));
                Set<String> unexpected = new HashSet<>(loaded.get(kind));
                unexpected.removeAll(ids);
                throw new IllegalStateException("MM registry mismatch for " + kind + "; missing " + missing + ", unexpected " + unexpected);
            });
        }, () -> {
            restores.forEach(Runnable::run);
            remount(mm);
            Bukkit.getPluginManager().callEvent(new MythicReloadedEvent(mm));
        });
    }

    private static void reload(MythicMobs mm) {
        mm.getItemManager().loadItems();
        mm.getSkillManager().loadSkills();
        mm.getMobManager().loadMobs();
        mm.getDropManager().loadDropTables();
        mm.getSpawnerManager().loadSpawners();
        mm.getSkillManager().runSecondPass();
        mm.getDropManager().runSecondPass();
        remount(mm);
        Bukkit.getPluginManager().callEvent(new MythicReloadedEvent(mm));
    }

    private static void remount(MythicMobs mm) {
        for (var mob : mm.getMobManager().getActiveMobs()) {
            mob.remountSpawner();
            mob.remountType();
        }
    }

    // MM has no snapshot API. Keep version-specific reflection at this adapter boundary.
    @SuppressWarnings("unchecked")
    private static void capture(Object owner, List<Runnable> restores, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object original = field.get(owner);
                Runnable restoreValues;
                if (original instanceof Map<?, ?> map) {
                    Map<Object, Object> accepted = new LinkedHashMap<>(map);
                    restoreValues = () -> {
                        var target = (Map<Object, Object>) original;
                        target.clear();
                        target.putAll(accepted);
                    };
                } else if (original instanceof Collection<?> collection) {
                    List<Object> accepted = new ArrayList<>(collection);
                    restoreValues = () -> {
                        var target = (Collection<Object>) original;
                        target.clear();
                        target.addAll(accepted);
                    };
                } else {
                    throw new IllegalStateException("Unsupported MM registry field: " + name);
                }
                restores.add(() -> {
                    restoreValues.run();
                    try {
                        field.set(owner, original);
                    } catch (IllegalAccessException failure) {
                        throw new IllegalStateException("Cannot restore MM registry " + name, failure);
                    }
                });
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Incompatible MM 4.12 registry " + name, failure);
            }
        }
    }
}
