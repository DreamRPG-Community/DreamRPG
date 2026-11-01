package cn.mythicland.dreamrpg.content;

import cn.mythicland.framework.bukkit.content.ContentWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ContentValidationTest {
    @TempDir Path server;

    @Test
    void validatesDirectDefinitionsTasksAndAliasesWithoutCopies() throws Exception {
        write("items/currency.yml", "copper:\n  Id: GOLD_NUGGET\n");
        write("mobs/main-village/chicken.yml", "chicken:\n  Type: CHICKEN\n  Drops: ['copper 1 1']\n");
        write("stages.yml", "version: 4\nstages: []\n");
        write("common/quests.yml", "version: 3\nstages: {}\n");
        write("exchanges.yml", "version: 1\nexchanges: {}\noffer-order: {}\n");
        writePlugin("DreamNPC/npcs.yml", "npcs: {}\n");
        writePlugin("DreamChest/chests.yml", "version: 3\nchests: {}\n");
        write("item-library/currency.yml", "entries:\n  copper:\n    order: 1\n    item: copper\n");
        write("landmarks.yml", "landmarks: {}\n");
        write("regions.yml", "regions: {}\n");
        write("waypoints.yml", "waypoints:\n  home:\n    name: Home\n    world: main_city\n");
        ContentWorkspace content = new ContentWorkspace(server.resolve("plugins/DreamRPG-content"), "dungeons", "dungeon.yml");
        assertTrue(ContentValidation.validate(content, content.definitions(java.util.List.of("items", "mobs", "skills", "droptables", "spawners"))).summary().contains("物品 1"));
        assertFalse(Files.exists(server.resolve("plugins/MythicMobs")), "Checking must never publish runtime files");
        write("mobs/main-village/chicken.yml", "chicken:\n  Type: CHICKEN\n  Drops: ['unknown_material 1 1']\n");
        assertThrows(IllegalArgumentException.class, () -> ContentValidation.validate(content, content.definitions(java.util.List.of("items", "mobs", "skills", "droptables", "spawners"))));
        write("mobs/main-village/chicken.yml", "chicken:\n  Type: CHICKEN\n");
        write("waypoints.yml", "waypoints:\n  home:\n    name: Home\n    landmark: missing\n");
        assertThrows(IllegalArgumentException.class, () -> ContentValidation.validate(content, content.definitions(java.util.List.of("items", "mobs", "skills", "droptables", "spawners"))));
    }

    private void writePlugin(String relative, String text) throws Exception {
        Path file = server.resolve("plugins").resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    private void write(String relative, String text) throws Exception {
        Path file = server.resolve("plugins/DreamRPG-content").resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
