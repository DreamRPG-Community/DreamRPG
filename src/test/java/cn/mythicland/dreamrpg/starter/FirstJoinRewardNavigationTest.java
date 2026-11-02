package cn.mythicland.dreamrpg.starter;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.database.FirstJoinRewardClaimRepository;
import cn.mythicland.framework.bukkit.menu.AbstractMenu;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import cn.mythicland.framework.bukkit.task.PluginTaskScope;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FirstJoinRewardNavigationTest {
    @Test
    void reopeningEditorPreservesUnsavedContentsWithoutClosingTheScreen() throws Exception {
        DreamLibApi lib = mock(DreamLibApi.class);
        when(lib.runAsync(any(Runnable.class))).thenReturn(CompletableFuture.completedFuture(null));
        MenuManager menus = mock(MenuManager.class);
        FirstJoinRewardService service = new FirstJoinRewardService(
                lib, mock(PluginTaskScope.class), menus, mock(FirstJoinRewardStore.class),
                mock(FirstJoinRewardClaimRepository.class), Logger.getAnonymousLogger()
        );
        Field contents = FirstJoinRewardService.class.getDeclaredField("contents");
        contents.setAccessible(true);
        contents.set(service, FirstJoinRewardContents.empty());
        UUID playerId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(FirstJoinRewardContents.SIZE);
        ItemStack[] edited = new ItemStack[FirstJoinRewardContents.SIZE];
        edited[0] = new ItemStack(Material.DIAMOND, 7);
        when(inventory.getContents()).thenReturn(edited);
        InventoryView view = mock(InventoryView.class);
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        AbstractMenu[] current = new AbstractMenu[1];
        doAnswer(call -> {
            if (current[0] != null) current[0].onClose(player, inventory);
            current[0] = call.getArgument(1);
            return null;
        }).when(menus).open(eq(player), any(AbstractMenu.class));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            service.openEditor(player);
            service.openEditor(player);
            verify(menus, never()).close(player);

            Inventory reopened = mock(Inventory.class);
            ((FirstJoinRewardMenu) current[0]).render(player, reopened);
            ArgumentCaptor<ItemStack[]> rendered = ArgumentCaptor.forClass(ItemStack[].class);
            verify(reopened).setContents(rendered.capture());
            assertEquals(Material.DIAMOND, rendered.getValue()[0].getType());
            assertEquals(7, rendered.getValue()[0].getAmount());

            service.close();
            verify(menus).close(player);
        }
    }
}
