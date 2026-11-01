package cn.mythicland.dreamrpg.enderchest;

import cn.mythicland.dreamrpg.api.PlayerStorageSnapshot;
import cn.mythicland.dreamrpg.storage.PlayerStorageService;
import cn.mythicland.framework.bukkit.container.ContainerAnimationHandle;
import cn.mythicland.framework.bukkit.container.ContainerAnimationSpec;
import cn.mythicland.framework.bukkit.menu.InventoryMenu;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import cn.mythicland.framework.bukkit.text.LegacyText;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Mutable one-page 54-slot custom ender-chest view.
 */
final class EnderChestMenu extends InventoryMenu {

    static final int SIZE = PlayerStorageSnapshot.ENDER_CHEST_SIZE;
    private static final String ENDER_CHEST_TITLE = "&8末影箱";
    private final UUID uniqueId;
    private final PlayerStorageService storage;
    private final ContainerAnimationSpec soundSpecification;
    private final boolean localSound;
    private final Runnable closeCallback;
    private final ItemStack[] initialContents;
    private ContainerAnimationHandle animation;
    private boolean closed;

    EnderChestMenu(
            UUID uniqueId,
            PlayerStorageService storage,
            PlayerStorageSnapshot snapshot,
            boolean localSound,
            Runnable closeCallback
    ) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.storage = Objects.requireNonNull(storage, "storage");
        PlayerStorageSnapshot value = Objects.requireNonNull(snapshot, "snapshot");
        this.initialContents = value.enderChest();
        this.animation = null;
        this.soundSpecification = ContainerAnimationSpec.enderChest();
        this.localSound = localSound;
        this.closeCallback = Objects.requireNonNull(closeCallback, "closeCallback");
    }

    private static ItemStack[] copyContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            ItemStack item = contents[index];
            copy[index] = item == null ? null : item.clone();
        }
        return copy;
    }

    void attachAnimation(ContainerAnimationHandle animation) {
        if (this.animation != null) throw new IllegalStateException("Ender-chest animation is already attached");
        this.animation = Objects.requireNonNull(animation, "animation");
    }

    UUID ownerId() {
        return uniqueId;
    }

    @Override
    public String buildTitle(Player player) {
        Objects.requireNonNull(player, "player");
        return LegacyText.colorize(ENDER_CHEST_TITLE);
    }

    @Override
    public int size(Player player) {
        Objects.requireNonNull(player, "player");
        return SIZE;
    }

    @Override
    public void render(Player player, Inventory inventory) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(inventory, "inventory").setContents(copyContents(initialContents));
    }

    @Override
    public void handleClick(Player player, InventoryClickEvent event, MenuManager menuService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(menuService, "menuService");
        event.setCancelled(false);
    }

    @Override
    public void handleDrag(Player player, InventoryDragEvent event, MenuManager menuService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(menuService, "menuService");
        event.setCancelled(false);
    }

    @Override
    public void onClose(Player player, Inventory inventory) {
        persistAndClose(player, inventory, true);
    }

    @Override
    public void onQuit(Player player, Inventory inventory) {
        persistAndClose(player, inventory, false);
    }

    private void persistAndClose(Player player, Inventory inventory, boolean playCloseSound) {
        if (closed) return;
        closed = true;
        if (inventory.getSize() != SIZE) {
            throw new IllegalStateException("DreamRPG ender-chest inventory size changed unexpectedly");
        }
        storage.updateEnderChest(uniqueId, inventory.getContents());
        if (animation != null) animation.close();
        if (localSound && playCloseSound && player.isOnline()) {
            player.playSound(
                    player.getLocation(),
                    soundSpecification.closeSound(),
                    soundSpecification.closeVolume(),
                    soundSpecification.closePitch()
            );
        }
        closeCallback.run();
    }
}
