package cn.mythicland.dreamrpg.mob;

/** Detached coordinates of one native MythicMobs spawner. */
public record SpawnPoint(
        String name,
        String internalName,
        String worldName,
        double x,
        double y,
        double z
) {
}
