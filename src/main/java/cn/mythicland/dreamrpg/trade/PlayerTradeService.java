package cn.mythicland.dreamrpg.trade;

import cn.mythicland.dreamrpg.storage.PlayerStorageService;
import cn.mythicland.framework.bukkit.menu.AutoRefreshMenu;
import cn.mythicland.framework.bukkit.menu.InventoryMenu;
import cn.mythicland.framework.bukkit.menu.MenuManager;
import cn.mythicland.framework.bukkit.text.LegacyText;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Coordinates item-only player trades and their in-memory invitations.
 */
@InjectComponent
@ListenerComponent
public final class PlayerTradeService implements Listener, AutoCloseable {

    private static final long INVITATION_MILLIS = 15_000L;
    private static final long REVIEW_MILLIS = 5_000L;
    private static final int MENU_SIZE = 54;
    private static final int OFFER_SLOTS = 45;

    private final MenuManager menus;
    private final PlayerStorageService storage;
    private final Map<UUID, Invitation> invitations = new HashMap<>();
    private final Map<UUID, LiveTrade> activeTrades = new HashMap<>();
    private boolean enabled;

    public PlayerTradeService(MenuManager menus, PlayerStorageService storage) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneakRightClickPlayer(PlayerInteractEntityEvent event) {
        if (!event.getPlayer().isSneaking() || !(event.getRightClicked() instanceof Player target)) return;
        event.setCancelled(true);
        request(event.getPlayer(), target);
    }

    /**
     * Sends a request or opens the trade when the target has already requested this player.
     */
    public void request(Player requester, Player target) {
        if (!enabled) return;
        try {
            available(requester);
            available(target);
            if (requester.equals(target)) throw new IllegalArgumentException("&c你不能和自己交易。");

            long now = System.currentTimeMillis();
            invitations.values().removeIf(invitation -> invitation.expiresAt() <= now);
            Invitation reverse = invitations.get(target.getUniqueId());
            if (reverse != null && reverse.target().equals(requester.getUniqueId())) {
                start(target, requester);
                return;
            }
            Invitation previous = invitations.get(requester.getUniqueId());
            if (previous != null && previous.target().equals(target.getUniqueId())) {
                throw new IllegalArgumentException("&c你已向对方发送交易请求，请等待回应。");
            }

            invitations.put(requester.getUniqueId(), new Invitation(
                    target.getUniqueId(), now + INVITATION_MILLIS
            ));
            requester.sendMessage(LegacyText.colorize("&a已向 " + target.getName() + " &a发送交易请求。"));
            TextComponent message = new TextComponent(TextComponent.fromLegacyText(LegacyText.colorize(
                    "&e" + requester.getName() + " &7向你发送了交易请求，点击"
            )));
            TextComponent accept = new TextComponent(TextComponent.fromLegacyText(LegacyText.colorize("&a接受")));
            accept.setClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/trade " + requester.getName()
            ));
            message.addExtra(accept);
            for (var punctuation : TextComponent.fromLegacyText(LegacyText.colorize("&7。"))) {
                message.addExtra(punctuation);
            }
            target.spigot().sendMessage(message);
        } catch (IllegalArgumentException exception) {
            requester.sendMessage(LegacyText.colorize(exception.getMessage()));
        }
    }

    private void available(Player player) {
        if (!player.isOnline() || player.isDead()) throw new IllegalArgumentException("&c当前无法交易。");
        if (!storage.isReady(player.getUniqueId())) throw new IllegalArgumentException("&c玩家数据尚未加载完成。");
        if (activeTrades.containsKey(player.getUniqueId())) throw new IllegalArgumentException("&c该玩家正在交易中。");
    }

    private void start(Player first, Player second) {
        available(first);
        available(second);
        invitations.entrySet().removeIf(entry -> entry.getKey().equals(first.getUniqueId())
                || entry.getKey().equals(second.getUniqueId())
                || entry.getValue().target().equals(first.getUniqueId())
                || entry.getValue().target().equals(second.getUniqueId()));

        LiveTrade trade = new LiveTrade(first, second);
        activeTrades.put(first.getUniqueId(), trade);
        activeTrades.put(second.getUniqueId(), trade);
        try {
            menus.open(first, trade.firstMenu);
            menus.open(second, trade.secondMenu);
            first.sendMessage(LegacyText.colorize("&e与 " + second.getName() + " &e开始交易。"));
            second.sendMessage(LegacyText.colorize("&e与 " + first.getName() + " &e开始交易。"));
        } catch (RuntimeException exception) {
            finish(trade);
            first.sendMessage(LegacyText.colorize("&c交易界面打开失败。"));
            second.sendMessage(LegacyText.colorize("&c交易界面打开失败。"));
        }
    }

    private void toggleOffer(Player player, TradeMenu menu, int inventorySlot) {
        LiveTrade trade = currentTrade(player, menu);
        if (trade == null || inventorySlot < 0 || inventorySlot >= 36) return;
        LinkedHashMap<Integer, ItemStack> offers = trade.offers(player);
        if (offers.remove(inventorySlot) == null) {
            ItemStack item = player.getInventory().getStorageContents()[inventorySlot];
            if (item == null || item.getType() == Material.AIR) return;
            offers.put(inventorySlot, item.clone());
        }
        trade.changed();
        refresh(trade);
    }

    private void removeOffer(Player player, TradeMenu menu, int offerIndex) {
        LiveTrade trade = currentTrade(player, menu);
        if (trade == null) return;
        LinkedHashMap<Integer, ItemStack> offers = trade.offers(player);
        if (offerIndex < 0 || offerIndex >= offers.size()) return;
        offers.remove(new ArrayList<>(offers.keySet()).get(offerIndex));
        trade.changed();
        refresh(trade);
    }

    private void confirm(Player player, TradeMenu menu) {
        LiveTrade trade = currentTrade(player, menu);
        if (trade == null) return;
        if (trade.firstOffers.isEmpty() && trade.secondOffers.isEmpty()) {
            player.sendMessage(LegacyText.colorize("&c请先选择要交易的物品。"));
            return;
        }
        if (System.currentTimeMillis() < trade.confirmAfter) {
            player.sendMessage(LegacyText.colorize("&c请先核对交易内容，再进行确认。"));
            return;
        }
        if (!trade.confirmed.add(player.getUniqueId())) {
            trade.confirmed.remove(player.getUniqueId());
            refresh(trade);
            return;
        }
        if (trade.confirmed.contains(trade.first.getUniqueId())
                && trade.confirmed.contains(trade.second.getUniqueId())) {
            complete(trade);
            return;
        }
        refresh(trade);
    }

    private void complete(LiveTrade trade) {
        try {
            ItemStack[] firstInventory = copy(trade.first.getInventory().getStorageContents());
            ItemStack[] secondInventory = copy(trade.second.getInventory().getStorageContents());
            removeOffers(firstInventory, trade.firstOffers);
            removeOffers(secondInventory, trade.secondOffers);
            List<ItemStack> firstOverflow = insertOffers(firstInventory, trade.secondOffers.values());
            List<ItemStack> secondOverflow = insertOffers(secondInventory, trade.firstOffers.values());

            trade.first.getInventory().setStorageContents(firstInventory);
            trade.second.getInventory().setStorageContents(secondInventory);
            storage.capture(trade.first);
            storage.capture(trade.second);
            storage.flush(trade.first.getUniqueId());
            storage.flush(trade.second.getUniqueId());
            finish(trade);
            dropOverflow(trade.first, firstOverflow);
            dropOverflow(trade.second, secondOverflow);
            trade.first.sendMessage(LegacyText.colorize("&a与 " + trade.second.getName() + " &a交易完成。"));
            trade.second.sendMessage(LegacyText.colorize("&a与 " + trade.first.getName() + " &a交易完成。"));
        } catch (IllegalArgumentException exception) {
            trade.first.sendMessage(LegacyText.colorize(exception.getMessage()));
            trade.second.sendMessage(LegacyText.colorize(exception.getMessage()));
        }
    }

    private static void removeOffers(ItemStack[] inventory, LinkedHashMap<Integer, ItemStack> offers) {
        for (Map.Entry<Integer, ItemStack> offer : offers.entrySet()) {
            int slot = offer.getKey();
            if (slot >= inventory.length || !offer.getValue().equals(inventory[slot])) {
                throw new IllegalArgumentException("&c背包物品发生变化，请重新选择物品。");
            }
            inventory[slot] = null;
        }
    }

    private static List<ItemStack> insertOffers(ItemStack[] inventory, Iterable<ItemStack> offers) {
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack offered : offers) {
            ItemStack remaining = offered.clone();
            for (int slot = 0; slot < inventory.length && remaining.getAmount() > 0; slot++) {
                ItemStack current = inventory[slot];
                if (current == null || !current.isSimilar(remaining)) continue;
                int moved = Math.min(remaining.getAmount(), current.getMaxStackSize() - current.getAmount());
                if (moved <= 0) continue;
                current.setAmount(current.getAmount() + moved);
                remaining.setAmount(remaining.getAmount() - moved);
            }
            for (int slot = 0; slot < inventory.length && remaining.getAmount() > 0; slot++) {
                if (inventory[slot] != null && inventory[slot].getType() != Material.AIR) continue;
                int moved = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
                inventory[slot] = remaining.clone();
                inventory[slot].setAmount(moved);
                remaining.setAmount(remaining.getAmount() - moved);
            }
            if (remaining.getAmount() > 0) overflow.add(remaining);
        }
        return overflow;
    }

    private static void dropOverflow(Player recipient, List<ItemStack> overflow) {
        for (ItemStack item : overflow) recipient.getWorld().dropItemNaturally(recipient.getLocation(), item);
        if (!overflow.isEmpty()) {
            recipient.sendMessage(LegacyText.colorize("&e背包空间不足，部分交易物品已掉落在你脚下。"));
        }
    }

    private static ItemStack[] copy(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int slot = 0; slot < items.length; slot++) copy[slot] = items[slot] == null ? null : items[slot].clone();
        return copy;
    }

    private LiveTrade currentTrade(Player player, TradeMenu menu) {
        LiveTrade trade = activeTrades.get(player.getUniqueId());
        return trade != null && trade.menu(player) == menu && menus.currentMenu(player) == menu ? trade : null;
    }

    private void cancel(Player player) {
        LiveTrade trade = activeTrades.get(player.getUniqueId());
        if (trade == null) return;
        finish(trade);
        if (trade.first.isOnline()) trade.first.sendMessage(LegacyText.colorize("&c交易已取消。"));
        if (trade.second.isOnline()) trade.second.sendMessage(LegacyText.colorize("&c交易已取消。"));
    }

    private void finish(LiveTrade trade) {
        activeTrades.remove(trade.first.getUniqueId(), trade);
        activeTrades.remove(trade.second.getUniqueId(), trade);
        for (Player participant : List.of(trade.first, trade.second)) {
            if (menus.currentMenu(participant) == trade.menu(participant)) menus.close(participant);
        }
    }

    private void refresh(LiveTrade trade) {
        for (Player participant : List.of(trade.first, trade.second)) {
            if (menus.currentMenu(participant) == trade.menu(participant)) menus.refresh(participant);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        LiveTrade trade = activeTrades.get(player.getUniqueId());
        if (trade != null && menus.currentMenu(player) != trade.menu(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && activeTrades.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        LiveTrade trade = activeTrades.get(player.getUniqueId());
        if (trade != null && menus.currentMenu(player) != trade.menu(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !menus.isManagedInventory(event.getInventory())) return;
        if (activeTrades.containsKey(player.getUniqueId())) cancel(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (activeTrades.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPickup(PlayerPickupItemEvent event) {
        if (activeTrades.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (activeTrades.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (activeTrades.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (activeTrades.containsKey(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) cancel(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent event) {
        cancel(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cancel(player);
        removeInvitations(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onKick(PlayerKickEvent event) {
        cancel(event.getPlayer());
        removeInvitations(event.getPlayer().getUniqueId());
    }

    private void removeInvitations(UUID player) {
        invitations.remove(player);
        invitations.values().removeIf(invitation -> invitation.target().equals(player));
    }

    public void start() {
        enabled = true;
    }

    @Override
    public void close() {
        for (LiveTrade trade : new HashSet<>(activeTrades.values())) finish(trade);
        invitations.clear();
        enabled = false;
    }

    private record Invitation(UUID target, long expiresAt) {
    }

    private final class LiveTrade {
        private final Player first;
        private final Player second;
        private final TradeMenu firstMenu;
        private final TradeMenu secondMenu;
        private final LinkedHashMap<Integer, ItemStack> firstOffers = new LinkedHashMap<>();
        private final LinkedHashMap<Integer, ItemStack> secondOffers = new LinkedHashMap<>();
        private final Set<UUID> confirmed = new HashSet<>();
        private long confirmAfter;

        private LiveTrade(Player first, Player second) {
            this.first = first;
            this.second = second;
            this.confirmAfter = System.currentTimeMillis() + REVIEW_MILLIS;
            this.firstMenu = new TradeMenu(this, first, second);
            this.secondMenu = new TradeMenu(this, second, first);
        }

        private LinkedHashMap<Integer, ItemStack> offers(Player player) {
            if (player.equals(first)) return firstOffers;
            if (player.equals(second)) return secondOffers;
            throw new IllegalArgumentException("Player is not part of this trade");
        }

        private LinkedHashMap<Integer, ItemStack> otherOffers(Player player) {
            return player.equals(first) ? secondOffers : firstOffers;
        }

        private TradeMenu menu(Player player) {
            return player.equals(first) ? firstMenu : secondMenu;
        }

        private boolean confirmed(Player player) {
            return confirmed.contains(player.getUniqueId());
        }

        private boolean otherConfirmed(Player player) {
            return confirmed.contains((player.equals(first) ? second : first).getUniqueId());
        }

        private void changed() {
            confirmed.clear();
            confirmAfter = System.currentTimeMillis() + REVIEW_MILLIS;
        }
    }

    private final class TradeMenu extends InventoryMenu implements AutoRefreshMenu {
        private final LiveTrade trade;
        private final Player owner;
        private final Player other;
        private boolean showingSelf = true;

        private TradeMenu(LiveTrade trade, Player owner, Player other) {
            this.trade = trade;
            this.owner = owner;
            this.other = other;
        }

        @Override
        public String buildTitle(Player player) {
            return "&7与 " + other.getName() + " &7交易";
        }

        @Override
        public int size(Player player) {
            return MENU_SIZE;
        }

        @Override
        public void render(Player player, Inventory inventory) {
            LinkedHashMap<Integer, ItemStack> offers = trade.otherOffers(owner);
            if (showingSelf) offers = trade.offers(owner);
            int slot = 0;
            for (ItemStack item : offers.values()) inventory.setItem(slot++, item.clone());
            inventory.setItem(48, confirmation());
            String viewName = "&e当前查看: &c对方的物品";
            String viewAction = "&e点击查看自己物品!";
            if (showingSelf) {
                viewName = "&e当前查看: &a自己的物品";
                viewAction = "&e点击查看对方物品!";
            }
            inventory.setItem(49, button(Material.BOOK, (short) 0, viewName,
                    List.of("&7看看交易内容是否符合你的预期...", "", viewAction)));
            boolean confirmed = trade.otherConfirmed(owner);
            String statusName = "&e对方暂未确认..";
            String statusLore = "&7对方当前没有确认交易内容...";
            if (confirmed) {
                statusName = "&a对方已确认!";
                statusLore = "&7正在等待你确认交易内容...";
            }
            inventory.setItem(50, button(Material.INK_SACK, confirmed ? (short) 10 : (short) 8,
                    statusName, List.of("&7你正在与玩家 " + other.getName() + " &7进行交易.", statusLore)));
        }

        private ItemStack confirmation() {
            if (trade.confirmed(owner)) {
                return button(Material.STAINED_CLAY, (short) 5, "&a已同意交易!",
                        List.of("&7你已同意了交易。", "&7现在请等待对方同意交易.", "", "&e点击以取消同意交易, 再思考一会..."));
            }
            long remaining = Math.max(0L, (trade.confirmAfter - System.currentTimeMillis() + 999L) / 1000L);
            if (remaining > 0) {
                ItemStack countdown = button(Material.STAINED_CLAY, (short) 4,
                        "&e请三思! &7(&e" + remaining + "&7)",
                        List.of("&7交易内容发生了变动,", "&7在同意交易前请检查交易内容。"));
                countdown.setAmount((int) remaining);
                return countdown;
            }
            if (trade.firstOffers.isEmpty() && trade.secondOffers.isEmpty()) {
                return button(Material.STAINED_CLAY, (short) 5, "&e交易!",
                        List.of("&7交易现在开始!,", "&7放上你想要交换的物品。"));
            }
            if (trade.otherOffers(owner).isEmpty()) {
                return button(Material.STAINED_CLAY, (short) 14, "&e警告!",
                        List.of("&7你正在对方没有提供任何物品", "&7的情况下进行交易。", " ",
                                "&c这真的是一份礼物?", " ", "&e点击同意此次交易!"));
            }
            if (trade.offers(owner).isEmpty()) {
                return button(Material.STAINED_CLAY, (short) 3, "&e是的,这是一份礼物!",
                        List.of("&7你正在没有提供任何物品,", "&7的情况下进行交易.", " ",
                                "&3你赚了! 他亏了!", " ", "&e点击同意此次交易!"));
            }
            return button(Material.STAINED_CLAY, (short) 5, "&e交易!",
                    List.of("&7交易成交后便无法修改,", "&7且无法被撤销.", " ",
                            "&a请确保在确认交易前", "&a仔细核对了所有交易物品。", " ", "&e点击同意此次交易!"));
        }

        @Override
        public void handleClick(Player player, InventoryClickEvent event, MenuManager menuManager) {
            event.setCancelled(true);
            if (!event.isLeftClick() && !event.isRightClick()) return;
            if (currentTrade(player, this) == null) return;
            int rawSlot = event.getRawSlot();
            if (rawSlot < 0) return;
            if (rawSlot >= MENU_SIZE) {
                toggleOffer(owner, this, event.getSlot());
                return;
            }
            if (rawSlot == 49) {
                showingSelf = !showingSelf;
                menuManager.refresh(owner);
                return;
            }
            if (rawSlot == 48) {
                confirm(owner, this);
                return;
            }
            if (showingSelf && rawSlot < OFFER_SLOTS) removeOffer(owner, this, rawSlot);
        }

        @Override
        public void handleDrag(Player player, org.bukkit.event.inventory.InventoryDragEvent event,
                               MenuManager menuManager) {
            event.setCancelled(true);
        }

        @Override
        public void onClose(Player player, Inventory inventory) {
            cancel(owner);
        }

        private ItemStack button(Material material, short durability, String name, List<String> lore) {
            ItemStack item = new ItemStack(material, 1, durability);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(LegacyText.colorize(name));
            if (!lore.isEmpty()) meta.setLore(lore.stream().map(LegacyText::colorize).toList());
            item.setItemMeta(meta);
            return item;
        }
    }
}
