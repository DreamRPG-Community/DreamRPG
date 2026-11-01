package cn.mythicland.dreamrpg.campaign;

import cn.mythicland.dreamrpg.exchange.ExchangeOffer;
import cn.mythicland.dreamrpg.exchange.ItemAmount;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CampaignCatalogTest {
    private static final UUID NPC = UUID.fromString("72a62f0f-a93a-3a40-a581-b05f345f94d1");

    @Test
    void editingAnExchangeKeepsItsOwnerAndRejectsStaleData() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
                version: 1
                exchanges:
                  starter:
                    name: starter
                    npc: 72a62f0f-a93a-3a40-a581-b05f345f94d1
                    offers:
                      sword:
                        requires:
                          - {item: copper, amount: 10}
                        reward: {item: sword, amount: 1}
                """);
        ExchangeOffer original = CampaignCatalog.parseGroups(yaml).getFirst().offers().getFirst();
        ExchangeOffer changed = new ExchangeOffer("sword", NPC,
                List.of(new ItemAmount("copper", 12), new ItemAmount("iron", 2)), new ItemAmount("sword", 3));
        CampaignCatalog.updateExchange(yaml, original, changed);
        assertEquals(changed, CampaignCatalog.parseGroups(yaml).getFirst().offers().getFirst());
        var reloaded = new YamlConfiguration();
        reloaded.loadFromString(yaml.saveToString());
        assertEquals(changed, CampaignCatalog.parseGroups(reloaded).getFirst().offers().getFirst());
        assertThrows(IllegalStateException.class, () -> CampaignCatalog.updateExchange(yaml, original, changed));
    }

    @Test
    void preservesTaskOrderAndItemQuantitiesWithoutCopyingItemDefinitions() throws Exception {
        var yaml = stages();
        var stages = CampaignCatalog.parse(yaml);
        assertEquals("origin", stages.getFirst().id());
        var task = stages.getFirst().tasks().getFirst();
        assertEquals("story_origin", task.id());
        assertEquals("origin_book", task.requires().getFirst().item());
        assertEquals(1, task.requires().getFirst().amount());
        assertEquals("daily_life_book", task.reward().item());
        assertEquals(NPC, task.npc());
        assertThrows(UnsupportedOperationException.class, () -> task.requires().clear());
        assertThrows(UnsupportedOperationException.class, () -> stages.getFirst().tasks().clear());
    }

    @Test
    void rejectsRetiredDirectoryFieldsAndLegacyVersions() throws Exception {
        var yaml = stages();
        yaml.set("version", 1);
        assertThrows(IllegalArgumentException.class, () -> CampaignCatalog.parse(yaml));
        yaml.set("version", 2);
        assertThrows(IllegalArgumentException.class, () -> CampaignCatalog.parse(yaml));
        for (String retired : List.of("name", "description", "mobs")) {
            var invalid = stages();
            invalid.set("stages.origin." + retired, "retired");
            assertThrows(IllegalArgumentException.class, () -> CampaignCatalog.parse(invalid));
        }
    }

    @Test
    void rejectsFreeExchangesAndNonIntegerOrOutOfRangeAmounts() throws Exception {
        var yaml = stages();
        String path = "stages.origin.tasks.story_origin";
        yaml.set(path + ".requires", List.of());
        assertThrows(IllegalArgumentException.class, () -> CampaignCatalog.parse(yaml));
        for (Object invalid : List.of(0, -1, 2305, 1.5, "16")) {
            var invalidConfiguration = stages();
            invalidConfiguration.set(path + ".reward.amount", invalid);
            assertThrows(IllegalArgumentException.class, () -> CampaignCatalog.parse(invalidConfiguration));
        }
    }

    @Test
    void npcGroupsSupplyTheNpcWithoutRepeatingItInEveryOffer() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
                version: 1
                exchanges:
                  starter:
                    name: 新手装备
                    npc: 72a62f0f-a93a-3a40-a581-b05f345f94d1
                    offers:
                      starter_sword:
                        requires:
                          - item: copper
                            amount: 10
                        reward:
                          item: wooden_sword
                """);
        var groups = CampaignCatalog.parseGroups(yaml);
        ExchangeOffer offer = groups.getFirst().offers().getFirst();
        assertEquals(NPC, offer.npc());
        assertEquals(10, offer.requires().getFirst().amount());
        assertEquals(1, offer.reward().amount());
    }

    @Test
    void taskCreationEditingDeletionAndConflictCheckSurviveReload() throws Exception {
        var yaml = stages();
        ExchangeOffer original = CampaignCatalog.parse(yaml).getFirst().tasks().getFirst();
        ExchangeOffer changed = new ExchangeOffer(original.id(), NPC,
                List.of(new ItemAmount("origin_book", 2)), new ItemAmount("daily_life_book", 3));
        CampaignCatalog.changeYaml(yaml, original, changed, true, "origin");
        assertEquals(changed, CampaignCatalog.parse(yaml).getFirst().tasks().getFirst());
        assertThrows(IllegalStateException.class, () -> CampaignCatalog.changeYaml(yaml, original, null, true, null));
        ExchangeOffer added = new ExchangeOffer("new_task", NPC,
                List.of(new ItemAmount("origin_book", 1)), new ItemAmount("daily_life_book", 1));
        CampaignCatalog.changeYaml(yaml, null, added, true, "origin");
        CampaignCatalog.changeYaml(yaml, changed, null, true, null);
        var reloaded = new YamlConfiguration();
        reloaded.loadFromString(yaml.saveToString());
        assertEquals(List.of(added), CampaignCatalog.parse(reloaded).getFirst().tasks());
        CampaignCatalog.changeYaml(reloaded, added, null, true, null);
        var empty = new YamlConfiguration();
        empty.loadFromString(reloaded.saveToString());
        assertTrue(CampaignCatalog.parse(empty).getFirst().tasks().isEmpty());
    }

    @Test
    void createsAndDeletesAnExchangeForANewNpc() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("version: 1\nexchanges: {}\n");
        ExchangeOffer offer = new ExchangeOffer("new_offer", NPC,
                List.of(new ItemAmount("copper", 2)), new ItemAmount("sword", 1));
        CampaignCatalog.changeYaml(yaml, null, offer, false, null);
        var loaded = new YamlConfiguration();
        loaded.loadFromString(yaml.saveToString());
        assertEquals(offer, CampaignCatalog.parseGroups(loaded).getFirst().offers().getFirst());
        assertFalse(loaded.contains("exchanges.npc_" + NPC.toString().replace("-", "") + ".name"));
        CampaignCatalog.changeYaml(loaded, offer, null, false, null);
        var empty = new YamlConfiguration();
        empty.loadFromString(loaded.saveToString());
        assertTrue(CampaignCatalog.parseGroups(empty).getFirst().offers().isEmpty());
    }

    @Test
    void createsNpcTaskWithoutRequiringAnExistingStage() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("version: 3\nstages: {}\n");
        ExchangeOffer offer = new ExchangeOffer("new_task", NPC,
                List.of(new ItemAmount("origin_book", 1)), new ItemAmount("daily_life_book", 1));
        CampaignCatalog.changeYaml(yaml, null, offer, true, CampaignCatalog.NPC_TASK_STAGE);
        var loaded = new YamlConfiguration();
        loaded.loadFromString(yaml.saveToString());
        assertEquals(List.of(offer), CampaignCatalog.parse(loaded).getFirst().tasks());
        assertEquals(java.util.Set.of("tasks"), loaded.getConfigurationSection("stages." + CampaignCatalog.NPC_TASK_STAGE).getKeys(false));
    }

    private static YamlConfiguration stages() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("""
                version: 3
                stages:
                  origin:
                    tasks:
                      story_origin:
                        npc: 72a62f0f-a93a-3a40-a581-b05f345f94d1
                        requires:
                          - item: origin_book
                            amount: 1
                        reward:
                          item: daily_life_book
                          amount: 1
                """);
        return yaml;
    }
}
