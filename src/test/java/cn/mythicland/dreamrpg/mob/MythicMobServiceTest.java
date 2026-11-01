package cn.mythicland.dreamrpg.mob;

import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpgitems.service.MythicItemService;
import cn.mythicland.framework.bukkit.content.ContentWorkspace;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.lumine.xikage.mythicmobs.io.MythicConfig;
import io.lumine.xikage.mythicmobs.mobs.MythicMob;
import io.lumine.xikage.mythicmobs.skills.placeholders.parsers.PlaceholderDouble;
import io.lumine.xikage.mythicmobs.skills.placeholders.parsers.PlaceholderString;
import io.lumine.xikage.mythicmobs.utils.config.file.YamlConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MythicMobServiceTest {
    @TempDir Path root;

    @Test
    void readsLoadedAttributesAndEquipmentWithoutSpawningOrRollingDrops() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
                goblin:
                  Type: ZOMBIE
                  Health: 30
                  Options: {MovementSpeed: 0.2}
                  Skills: ['skill{s=attack} @Self ~onTimer:20']
                  Equipment: ['DIAMOND_HELMET:4', 'example_sword:0']
                """);
        var source = root.resolve("dungeons/village/mobs/forest.yml");
        var config = new MythicConfig("goblin", source.toFile(), yaml);
        var mob = mock(MythicMob.class);
        when(mob.getConfig()).thenReturn(config);
        when(mob.getInternalName()).thenReturn("goblin");
        when(mob.getEntityType()).thenReturn("ZOMBIE");
        var name = mock(PlaceholderString.class);
        when(name.get()).thenReturn("&a哥布林");
        when(mob.getDisplayName()).thenReturn(name);
        var number = mock(PlaceholderDouble.class);
        when(number.toString()).thenReturn("RandomDouble{30.0 to 30.0}");
        when(mob.getHealth()).thenReturn(number);
        when(mob.getDamage()).thenReturn(number);
        when(mob.getArmor()).thenReturn(number);
        var plugin = mock(DreamRPGPlugin.class);
        var items = mock(MythicItemService.class);
        when(plugin.items()).thenReturn(items);
        when(items.configuration("example_sword")).thenReturn(Optional.of(Map.of("Id", "WOOD_SWORD")));
        when(items.getItemStack("example_sword", 1)).thenReturn(new ItemStack(Material.WOOD_SWORD));
        try (var bukkit = mockStatic(Bukkit.class); var mythic = mockStatic(MythicMobs.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var mm = mock(MythicMobs.class, RETURNS_DEEP_STUBS);
            mythic.when(MythicMobs::inst).thenReturn(mm);
            when(mm.getMobManager().getMythicMob("goblin")).thenReturn(mob);
            var service = new MythicMobService(plugin, new ContentWorkspace(root));
            var details = service.details("goblin").orElseThrow();
            assertTrue(details.attributes().contains(new MobDetails.Attribute("基础生命值", "30")));
            assertEquals("village", details.summary().folderName());
            assertTrue(details.attributes().contains(new MobDetails.Attribute("Options.MovementSpeed", "0.2")));
            assertTrue(details.attributes().stream().anyMatch(attribute -> attribute.name().equals("Skills[0]")));
            assertEquals(List.of("头盔", "主手"), details.equipment().stream().map(MobDetails.Equipment::slot).toList());
            assertEquals(Material.DIAMOND_HELMET, details.equipment().getFirst().preview().getType());
            assertEquals(Material.WOOD_SWORD, details.equipment().getLast().preview().getType());
            ItemStack preview = details.equipment().getFirst().preview();
            preview.setAmount(12);
            assertEquals(1, details.equipment().getFirst().preview().getAmount());
            when(number.toString()).thenReturn("RandomDouble{20.0 to 30.0}");
            assertTrue(service.details("goblin").orElseThrow().attributes().contains(new MobDetails.Attribute("基础生命值", "20 - 30")));
            verify(mob, never()).spawn(any(), anyDouble());
            verify(mm, never()).getDropManager();
        }
    }

    @Test
    void recognizesLegacyAndNamedEquipmentSlotsWithoutLosingMaterialData() {
        assertEquals(new MythicMobService.EquipmentReference("268:3", "主手"), MythicMobService.equipmentReference("268:3:0"));
        assertEquals(new MythicMobService.EquipmentReference("IRON_HELMET", "头盔"), MythicMobService.equipmentReference("IRON_HELMET HEAD"));
        assertEquals(new MythicMobService.EquipmentReference("WOOL:3", "头盔"), MythicMobService.equipmentReference("WOOL:3 HEAD"));
        assertEquals(new MythicMobService.EquipmentReference("equipment_table", "装备表 / 动态装备"), MythicMobService.equipmentReference("equipment_table"));
    }

    @Test
    void groupsMobSourcesAndKeepsFoldersDistinctAcrossPackages() {
        var first = new MobSummary("one", "One", "ZOMBIE", "dungeons/one/mobs/bosses/a.yml");
        var second = new MobSummary("two", "Two", "ZOMBIE", "dungeons/two/mobs/bosses/b.yml");
        var layout = MythicMobService.layout(List.of(second, first));
        assertEquals(List.of("one", "two"), layout.rows(Set.of(), ignored -> true).stream().map(row -> row.entryId()).toList());
        assertNotEquals(first.folder(), second.folder());
    }
}
