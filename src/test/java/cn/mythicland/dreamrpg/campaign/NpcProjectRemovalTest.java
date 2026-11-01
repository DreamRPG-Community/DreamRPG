package cn.mythicland.dreamrpg.campaign;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamnpc.api.DreamNPCRemovedEvent;
import cn.mythicland.dreamrpg.exchange.ExchangeOffer;
import cn.mythicland.dreamrpg.exchange.ItemAmount;
import cn.mythicland.framework.bukkit.storage.AtomicYamlStore;
import cn.mythicland.framework.bukkit.content.ContentWorkspace;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NpcProjectRemovalTest {
    private static final UUID DELETED = UUID.fromString("de2ded2a-8d3e-3263-b892-ecc3e9a3a684");
    private static final UUID KEPT = UUID.fromString("72a62f0f-a93a-3a40-a581-b05f345f94d1");
    @TempDir
    Path directory;

    @Test
    void deletionEventWaitsForEditorSaveAndRemovesAllOwnedProjectsAndSortOrder() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var fixture = new Fixture();
            var original = fixture.catalog.find("removed_task").orElseThrow();
            var changed = new ExchangeOffer(original.id(), DELETED,
                    List.of(new ItemAmount("book", 2)), original.reward());
            var edit = fixture.catalog.saveOffer(original, changed, "origin");
            new ExchangeNpcListener(mock(CampaignMenus.class), fixture.catalog, fixture.plugin)
                    .onNpcRemoved(new DreamNPCRemovedEvent(DELETED));
            assertEquals(1, fixture.work.size(), "Removal must wait for the administrator save");
            fixture.work.remove().run();
            assertTrue(edit.isDone());
            assertEquals(1, fixture.work.size());
            fixture.work.remove().run();
            assertEquals(List.of("kept_task", "kept_exchange"), fixture.catalog.offers().stream().map(ExchangeOffer::id).toList());
            assertTrue(fixture.catalog.placementsForNpc(DELETED).isEmpty());
            var stages = YamlConfiguration.loadConfiguration(fixture.stages.toFile());
            var exchanges = YamlConfiguration.loadConfiguration(fixture.exchanges.toFile());
            assertFalse(stages.contains("stages.origin.tasks.removed_task"));
            assertFalse(exchanges.contains("exchanges.removed"));
            assertFalse(exchanges.contains("offer-order." + DELETED));
            assertTrue(exchanges.contains("offer-order." + KEPT));
            assertFalse(Files.exists(directory.resolve("stages.yml")), "Cleanup must not recreate the retired plugin-local path");
            assertFalse(Files.exists(directory.resolve("exchanges.yml")));
            fixture.catalog.reload();
            assertEquals(2, fixture.catalog.offers().size(), "Removed projects must stay removed after reload");
            assertTrue(fixture.catalog.removeNpc(DELETED).isDone(), "Repeated removal must be harmless");
            fixture.catalog.unload();
            assertTrue(fixture.catalog.removeNpc(DELETED).isCompletedExceptionally());
        }
    }

    @Test
    void alsoRemovesAnEmptyOwnedExchangeGroupWithoutAnyTaskOrSortOrder() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var fixture = new Fixture();
            var stages = YamlConfiguration.loadConfiguration(fixture.stages.toFile());
            stages.set("stages.origin.tasks.removed_task", null);
            stages.save(fixture.stages.toFile());
            var exchanges = YamlConfiguration.loadConfiguration(fixture.exchanges.toFile());
            exchanges.set("exchanges.removed.offers", null);
            exchanges.createSection("exchanges.removed.offers");
            exchanges.set("offer-order." + DELETED, null);
            exchanges.save(fixture.exchanges.toFile());
            fixture.catalog.reload();
            var removed = fixture.catalog.removeNpc(DELETED);
            assertEquals(1, fixture.work.size(), "An empty group still belongs to its deleted NPC");
            fixture.work.remove().run();
            removed.join();
            var saved = YamlConfiguration.loadConfiguration(fixture.exchanges.toFile());
            assertFalse(saved.contains("exchanges.removed"));
            assertTrue(saved.contains("exchanges.kept"));
        }
    }

    @Test
    void failureWritingSecondFileRestoresBothFilesAndKeepsPublishedProjects() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var fixture = new Fixture();
            String originalStages = Files.readString(fixture.stages);
            String originalExchanges = Files.readString(fixture.exchanges);
            var removed = fixture.catalog.removeNpc(DELETED);
            try (var stores = mockStatic(AtomicYamlStore.class, CALLS_REAL_METHODS)) {
                stores.when(() -> AtomicYamlStore.write(eq(fixture.exchanges), any(YamlConfiguration.class)))
                        .thenThrow(new IOException("Simulated second-file failure"));
                fixture.work.remove().run();
            }
            assertThrows(CompletionException.class, removed::join);
            assertEquals(originalStages, Files.readString(fixture.stages));
            assertEquals(originalExchanges, Files.readString(fixture.exchanges));
            assertEquals(4, fixture.catalog.offers().size());
        }
    }

    @Test
    void externalEditsAreNotOverwrittenAndUnloadPreventsLatePublication() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var fixture = new Fixture();
            String original = Files.readString(fixture.stages);
            var removed = fixture.catalog.removeNpc(DELETED);
            String edited = original.replace("amount: 1", "amount: 2");
            Files.writeString(fixture.stages, edited);
            fixture.work.remove().run();
            assertThrows(CompletionException.class, removed::join);
            assertEquals(edited, Files.readString(fixture.stages));
            assertEquals(4, fixture.catalog.offers().size());
            Files.writeString(fixture.stages, original);
            var pending = fixture.catalog.removeNpc(DELETED);
            fixture.catalog.unload();
            fixture.work.remove().run();
            pending.join();
            assertTrue(fixture.catalog.offers().isEmpty(), "An old callback must not republish an unloaded catalog");
        }
    }

    private final class Fixture {
        private final JavaPlugin plugin = mock(JavaPlugin.class);
        private final DreamLibApi lib = mock(DreamLibApi.class);
        private final Queue<Runnable> work = new ArrayDeque<>();
        private final Path stages = directory.resolve("content/dungeons/village/quests.yml");
        private final Path exchanges = directory.resolve("content/dungeons/village/exchanges.yml");
        private final CampaignCatalog catalog;

        private Fixture() throws Exception {
            when(plugin.getDataFolder()).thenReturn(directory.toFile());
            when(plugin.getName()).thenReturn("DreamRPG");
            Files.createDirectories(stages.getParent());
            Files.writeString(stages.resolveSibling("dungeon.yml"), "name: Village\nenabled: true\n");
            Files.writeString(directory.resolve("content/stages.yml"), "version: 4\nstages: [origin]\n");
            when(lib.content()).thenReturn(new ContentWorkspace(directory.resolve("content"), "dungeons", "dungeon.yml"));
            when(lib.supplyAsync(any())).thenAnswer(call -> {
                Supplier<?> supplier = call.getArgument(0);
                var result = new CompletableFuture<Object>();
                work.add(() -> {
                    try { result.complete(supplier.get()); }
                    catch (RuntimeException failure) { result.completeExceptionally(failure); }
                });
                return result;
            });
            when(lib.runOnMain(any())).thenAnswer(call -> {
                call.getArgument(0, Runnable.class).run();
                return CompletableFuture.completedFuture(null);
            });
            Files.writeString(stages, """
                    version: 3
                    stages:
                      origin:
                        tasks:
                          removed_task:
                            npc: %s
                            requires: [{item: book, amount: 1}]
                            reward: {item: next_book, amount: 1}
                          kept_task:
                            npc: %s
                            requires: [{item: book, amount: 1}]
                            reward: {item: next_book, amount: 1}
                    """.formatted(DELETED, KEPT));
            Files.writeString(exchanges, """
                    version: 1
                    exchanges:
                      removed:
                        npc: %s
                        offers:
                          removed_exchange:
                            requires: [{item: copper, amount: 10}]
                            reward: {item: silver, amount: 1}
                      kept:
                        npc: %s
                        offers:
                          kept_exchange:
                            requires: [{item: copper, amount: 10}]
                            reward: {item: silver, amount: 1}
                    offer-order:
                      %s: [removed_task, '', removed_exchange]
                      %s: [kept_task, kept_exchange]
                    """.formatted(DELETED, KEPT, DELETED, KEPT));
            catalog = new CampaignCatalog(plugin, lib);
            catalog.load();
        }
    }
}
