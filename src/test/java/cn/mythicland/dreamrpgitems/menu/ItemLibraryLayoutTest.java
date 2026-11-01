package cn.mythicland.dreamrpgitems.menu;

import cn.mythicland.framework.pagination.FolderLayout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemLibraryLayoutTest {
    @TempDir
    Path directory;

    @Test
    void loadsMultipleFilesAndExpandsFoldersWithoutChangingTheirOrder() throws Exception {
        Files.writeString(directory.resolve("armor.yml"), """
                entries:
                  armor:
                    order: 20
                    items: [starter, level0, level1]
                """);
        Files.writeString(directory.resolve("currency.yml"), """
                entries:
                  currency:
                    order: 10
                    items: [copper, silver]
                  sword:
                    order: 30
                    item: sword
                """);
        FolderLayout layout = ItemLibraryConfiguration.load(directory);
        assertEquals(List.of(
                new FolderLayout.Row("copper", "currency", 2),
                new FolderLayout.Row("starter", "armor", 3),
                new FolderLayout.Row("sword", null, 1)
        ), layout.rows(Set.of(), ignored -> true));
        assertEquals(List.of(
                new FolderLayout.Row("copper", "currency", 2),
                new FolderLayout.Row("starter", "armor", 1),
                new FolderLayout.Row("level0", "armor", 1),
                new FolderLayout.Row("level1", "armor", 1),
                new FolderLayout.Row("sword", null, 1)
        ), layout.rows(Set.of("armor"), ignored -> true));
        assertEquals(1, layout.rows(Set.of(), id -> !id.equals("level0") && !id.equals("level1")).get(1).iconAmount());
    }

    @Test
    void rejectsDuplicateItemsAcrossConfigurationFiles() throws Exception {
        Files.writeString(directory.resolve("a.yml"), "entries:\n  a:\n    order: 1\n    item: shared\n");
        Files.writeString(directory.resolve("b.yml"), "entries:\n  b:\n    order: 2\n    items: [shared]\n");
        assertThrows(IllegalArgumentException.class, () -> ItemLibraryConfiguration.load(directory));
    }
}
