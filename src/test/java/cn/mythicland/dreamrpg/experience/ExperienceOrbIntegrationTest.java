package cn.mythicland.dreamrpg.experience;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpg.api.ExperienceApi;
import cn.mythicland.dreamrpg.config.ExperienceConfiguration;
import cn.mythicland.dreamrpg.config.ExperienceSettings;
import cn.mythicland.dreamrpg.database.ExperienceStore;
import cn.mythicland.dreamrpgcombat.api.CombatStatsSnapshot;
import cn.mythicland.dreamrpgcombat.integration.dreamrpg.DreamRPGExperienceIntegration;
import cn.mythicland.dreamrpgcombat.listener.CombatListener;
import cn.mythicland.dreamrpgcombat.stats.CombatStatsService;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExperienceOrbIntegrationTest {
    @Test
    void pickedUpOrbUsesItemBonusAndAdvancesRpgLevel() throws Exception {
        UUID id = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        DreamLibApi lib = mock(DreamLibApi.class);
        when(lib.supplyAsync(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return CompletableFuture.completedFuture(supplier.get());
        });
        ExperienceStore store = mock(ExperienceStore.class);
        when(store.loadOrCreate(id)).thenReturn(ExperienceState.initial(id));
        ExperienceConfiguration config = mock(ExperienceConfiguration.class);
        when(config.snapshot()).thenReturn(new ExperienceSettings(-1, true, 20, 5));
        PluginTaskScope tasks = mock(PluginTaskScope.class);
        ExperienceService experience = new ExperienceService(lib, tasks, store, config, Logger.getAnonymousLogger());

        DreamRPGPlugin plugin = mock(DreamRPGPlugin.class);
        Server server = mock(Server.class);
        ServicesManager services = mock(ServicesManager.class);
        PluginManager plugins = mock(PluginManager.class);
        @SuppressWarnings("unchecked")
        RegisteredServiceProvider<ExperienceApi> registration = mock(RegisteredServiceProvider.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(server.getServicesManager()).thenReturn(services);
        when(server.getPluginManager()).thenReturn(plugins);
        when(services.getRegistration(ExperienceApi.class)).thenReturn(registration);
        when(registration.getProvider()).thenReturn(experience);
        CombatStatsService stats = mock(CombatStatsService.class);
        when(stats.getStats(player)).thenReturn(CombatStatsSnapshot.builder().experienceBonusPercent(50).build());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            experience.load(id).join();
            experience.activate(player);
            try (DreamRPGExperienceIntegration integration = DreamRPGExperienceIntegration.detect(plugin)) {
                integration.enable(mock(CombatListener.class), stats);
                PlayerExpChangeEvent event = new PlayerExpChangeEvent(player, 10);
                new ExperienceEventListener(experience, tasks).onNativeExperience(event);
                assertEquals(0, event.getAmount());
                assertEquals(1, experience.snapshot(id).level());
                assertEquals(0, BigDecimal.valueOf(8).compareTo(experience.snapshot(id).currentExperience()));
                verify(player, atLeastOnce()).setLevel(1);
            }
        }
    }
}
