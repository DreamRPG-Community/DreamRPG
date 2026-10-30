package cn.mythicland.dreamrpg.starter;

import cn.mythicland.dreamlib.menu.MenuService;
import cn.mythicland.dreamlib.menu.StatefulMenuView;
import cn.mythicland.dreamlib.text.LegacyText;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Fixed-size first-join reward menu. The editor is mutable, while the player view only permits
 * taking items from the top inventory.
 */
final class FirstJoinRewardMenu implements StatefulMenuView {

    private static final String PLAYER_TITLE = "&8首次进入奖励";
    private static final String EDITOR_TITLE = "&8首次进入奖励管理";

    private final UUID uniqueId;
    private final boolean editor;
    private final FirstJoinRewardContents initialContents;
    private final Consumer<FirstJoinRewardContents> saveCallback;
    private final Runnable closeCallback;
    private boolean closed;

    FirstJoinRewardMenu(
            UUID uniqueId,
            boolean editor,
            FirstJoinRewardContents initialContents,
            Consumer<FirstJoinRewardContents> saveCallback,
            Runnable closeCallback
    ) {
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        this.editor = editor;
        this.initialContents = Objects.requireNonNull(initialContents, "initialContents");
        this.saveCallback = Objects.requireNonNull(saveCallback, "saveCallback");
        this.closeCallback = Objects.requireNonNull(closeCallback, "closeCallback");
    }

    static boolean isRewardTakeAction(InventoryAction action) {
        return switch (Objects.requireNonNull(action, "action")) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                 MOVE_TO_OTHER_INVENTORY, DROP_ALL_SLOT, DROP_ONE_SLOT,
                 COLLECT_TO_CURSOR, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> true;
            default -> false;
        };
    }

    @Override
    public String title(Player player) {
        Objects.requireNonNull(player, "player");
        return LegacyText.colorize(editor ? EDITOR_TITLE : PLAYER_TITLE);
    }

    @Override
    public int size(Player player) {
        Objects.requireNonNull(player, "player");
        return FirstJoinRewardContents.SIZE;
    }

    @Override
    public void render(Player player, Inventory inventory) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(inventory, "inventory").setContents(initialContents.copyContents());
    }

    @Override
    public void handleClick(Player player, InventoryClickEvent event, MenuService menuService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(menuService, "menuService");
        if (editor) {
            event.setCancelled(false);
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() == top
                && isRewardTakeAction(event.getAction())) {
            event.setCancelled(false);
        }
    }

    @Override
    public void handleDrag(Player player, InventoryDragEvent event, MenuService menuService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(menuService, "menuService");
        if (editor) event.setCancelled(false);
    }

    @Override
    public void onClose(Player player, Inventory inventory) {
        finish(player, inventory);
    }

    @Override
    public void onQuit(Player player, Inventory inventory) {
        finish(player, inventory);
    }

    private void finish(Player player, Inventory inventory) {
        if (closed) return;
        closed = true;
        if (editor) {
            if (inventory.getSize() != FirstJoinRewardContents.SIZE) {
                throw new IllegalStateException("First-join reward menu size changed unexpectedly");
            }
            saveCallback.accept(new FirstJoinRewardContents(inventory.getContents()));
        }
        closeCallback.run();
    }
}
