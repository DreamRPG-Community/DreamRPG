package cn.mythicland.dreamrpg.storage;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.database.PlayerStorageStore;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.logging.Logger;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class PlayerStorageServiceTest {

    @Test
    void restoresFullVitalsAfterMaximumHealthHasBeenConfigured() {
        Player player = mock(Player.class);
        when(player.getMaxHealth()).thenReturn(40.0D);
        PlayerStorageService storage = new PlayerStorageService(
                mock(DreamLibApi.class),
                mock(PluginTaskScope.class),
                mock(PlayerStorageStore.class),
                Logger.getLogger(PlayerStorageServiceTest.class.getName())
        );

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

            storage.restoreFullVitals(player);

            InOrder calls = inOrder(player);
            calls.verify(player).setHealth(40.0D);
            calls.verify(player).setFoodLevel(20);
            calls.verify(player).setSaturation(20.0F);
            calls.verify(player).setExhaustion(0.0F);
        }
    }
}
