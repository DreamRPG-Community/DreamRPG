package cn.mythicland.dreamrpg.campaign;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpg.exchange.ExchangeOffer;
import cn.mythicland.dreamrpg.exchange.ItemAmount;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.bukkit.menu.MenuItems;
import cn.mythicland.framework.bukkit.text.LegacyText;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.component.LifecycleComponent;
import cn.mythicland.framework.component.ManagedLifecycle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Native-inventory editor for one exchange.
 */
@InjectComponent
@LifecycleComponent
@ListenerComponent
public final class ExchangeEditor implements ManagedLifecycle, Listener {
    private static final int BACK_SLOT = 45;
    private static final int REWARD_SLOT = 49;
    private static final int SAVE_SLOT = 53;
    private final DreamRPGPlugin plugin;
    private final CampaignCatalog catalog;
    private final DreamLibApi lib;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public ExchangeEditor(DreamRPGPlugin plugin, CampaignCatalog catalog, DreamLibApi lib) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.lib = lib;
    }

    @Override
    public void load() {
    }

    @Override
    public void reload() {
        closeEditors();
    }

    public void open(Player player, ExchangeOffer offer, Consumer<Player> back) {
        String stage = catalog.stages().stream().filter(value -> value.tasks().contains(offer))
                .map(CampaignStage::id).findFirst().orElse(null);
        open(player, new Session(offer, offer.id(), offer.npc(), stage, back));
    }

    public void create(Player player, UUID npc, String stage, Consumer<Player> back) {
        String id = "entry_" + UUID.randomUUID().toString().replace("-", "");
        open(player, new Session(null, id, npc, stage, back));
    }

    private void open(Player player, Session session) {
        InventoryHolder previousHolder = player.getOpenInventory().getTopInventory().getHolder();
        lib.runLater(1, () -> {
            if (!player.isOnline() || !player.hasPermission("dreamrpg.admin")) return;
            if (player.getOpenInventory().getTopInventory().getHolder() != previousHolder) return;
            Inventory inventory = Bukkit.createInventory(session, 54, LegacyText.colorize("&8编辑兑换"));
            session.inventory = inventory;
            try {
                if (session.offer != null) populate(session);
                else updateRewardPlaceholder(session);
            } catch (IllegalArgumentException exception) {
                player.sendMessage("§c配方物品数量超出编辑器容量。");
                return;
            }
            ItemStack filler = MenuItems.button(Material.STAINED_GLASS_PANE, (short) 15, "&8 ", List.of());
            for (int slot = 45; slot < 54; slot++) {
                if (slot != REWARD_SLOT) inventory.setItem(slot, filler);
            }
            inventory.setItem(BACK_SLOT, MenuItems.button(Material.ARROW, "&c返回", List.of("&7放弃本次更改并返回")));
            inventory.setItem(SAVE_SLOT, MenuItems.button(Material.EMERALD, "&a保存兑换", List.of("&7保存材料和奖励")));
            sessions.put(player.getUniqueId(), session);
            player.openInventory(inventory);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = session(event.getView().getTopInventory(), player.getUniqueId());
        if (session == null) return;
        if (session.saving || session.pending) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        if (raw == BACK_SLOT) {
            event.setCancelled(true);
            if (event.getClick() == ClickType.LEFT) {
                lib.runLater(1L, () -> {
                    if (player.isOnline() && session(session.inventory, player.getUniqueId()) == session) {
                        session.back.accept(player);
                    }
                });
            }
            return;
        }
        if (raw == SAVE_SLOT) {
            event.setCancelled(true);
            ItemStack cursor = event.getCursor();
            if (event.getClick() == org.bukkit.event.inventory.ClickType.LEFT && empty(cursor)) save(player, session);
            return;
        }
        if (raw < 0) {
            return;
        }
        if (raw < 54 && !editable(raw)) {
            event.setCancelled(true);
            return;
        }
        if (event.isCancelled()) return;
        if (event.getAction() == InventoryAction.DROP_ALL_SLOT || event.getAction() == InventoryAction.DROP_ONE_SLOT
                || event.getAction() == InventoryAction.DROP_ALL_CURSOR ||
                event.getAction() == InventoryAction.DROP_ONE_CURSOR) {
            if (raw < 54) session.samples.remove(raw);
            lib.runLater(1L, () -> {
                if (session(session.inventory, player.getUniqueId()) == session) updateRewardPlaceholder(session);
            });
            return;
        }
        if ((event.getClick() == ClickType.DOUBLE_CLICK && matchesControl(session, event.getCursor()))
                || (event.isShiftClick() && raw >= 54 && matchesControl(session, event.getCurrentItem()))) {
            event.setCancelled(true);
            return;
        }
        ItemStack[] before = copy(session.inventory.getContents());
        if (raw == REWARD_SLOT && session.rewardPlaceholder) {
            session.inventory.setItem(REWARD_SLOT, null);
            session.rewardPlaceholder = false;
        }
        recordChange(player, session, before);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = session(event.getView().getTopInventory(), player.getUniqueId());
        if (session == null) return;
        if (session.saving || session.pending || event.getRawSlots().stream().anyMatch(
                slot -> slot < 54 && (!editable(slot) || (slot == REWARD_SLOT && session.rewardPlaceholder))
        )) {
            event.setCancelled(true);
            return;
        }
        if (!event.isCancelled()) recordChange(player, session, copy(session.inventory.getContents()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session session = session(event.getInventory(), player.getUniqueId());
        if (session == null) return;
        sessions.remove(player.getUniqueId(), session);
        returnItems(player, takeInputs(session));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())) event.getPlayer().closeInventory();
    }

    @Override
    public void unload() {
        closeEditors();
    }

    private void closeEditors() {
        for (UUID id : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.closeInventory();
        }
    }

    private void save(Player player, Session session) {
        if (!player.hasPermission("dreamrpg.admin")) return;
        List<ItemAmount> costs = new ArrayList<>();
        try {
            for (int slot = 0; slot < 45; slot++) {
                ItemStack stack = session.inventory.getItem(slot);
                if (stack != null && stack.getType() != Material.AIR) costs.add(identify(stack));
            }
            ItemStack reward = session.inventory.getItem(REWARD_SLOT);
            if (costs.isEmpty() || session.rewardPlaceholder || empty(reward)) {
                player.sendMessage("§c请放入兑换材料和奖励。");
                return;
            }
            ExchangeOffer replacement = new ExchangeOffer(session.id, session.npc, costs, identify(reward));
            session.saving = true;
            lib.runLater(1L, () -> {
                if (session(session.inventory, player.getUniqueId()) != session) return;
                List<ItemStack> inputs = takeInputs(session);
                player.closeInventory();
                catalog.saveOffer(session.offer, replacement, session.stage).whenComplete((ignored, error) -> {
                    if (error != null) {
                        returnItems(player, inputs);
                        plugin.getLogger().log(Level.SEVERE, "Failed to save exchange " + session.id, error);
                        if (player.isOnline()) player.sendMessage("§c保存失败, 物品已返还。");
                    } else if (player.isOnline()) {
                        player.sendMessage("§a已保存。");
                        session.back.accept(player);
                    }
                });
            });
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c请使用物品库中的有效物品。");
        }
    }

    private void populate(Session session) {
        int slot = 0;
        for (ItemAmount cost : session.offer.requires()) {
            ItemStack item = plugin.items().getItemStack(cost.item(), 1);
            int remaining = cost.amount();
            while (remaining > 0) {
                if (slot >= 45) throw new IllegalArgumentException("Exchange materials exceed editor capacity");
                int amount = Math.min(remaining, item.getMaxStackSize());
                ItemStack sample = item.clone();
                sample.setAmount(amount);
                session.inventory.setItem(slot, sample);
                session.samples.add(slot++);
                remaining -= amount;
            }
        }
        ItemAmount reward = session.offer.reward();
        ItemStack item = plugin.items().getItemStack(reward.item(), 1);
        if (reward.amount() > item.getMaxStackSize()) {
            throw new IllegalArgumentException("Exchange reward exceeds editor capacity");
        }
        item.setAmount(reward.amount());
        session.inventory.setItem(REWARD_SLOT, item);
        session.samples.add(REWARD_SLOT);
    }

    private void recordChange(Player player, Session session, ItemStack[] before) {
        session.pending = true;
        lib.runLater(1L, () -> {
            if (session(session.inventory, player.getUniqueId()) != session) return;
            updateRewardPlaceholder(session);
            for (int slot : Set.copyOf(session.samples)) {
                if (!Objects.equals(before[slot], session.inventory.getItem(slot))) session.samples.remove(slot);
            }
            session.pending = false;
        });
    }

    private static boolean matchesControl(Session session, ItemStack cursor) {
        if (empty(cursor)) return false;
        for (int slot = 45; slot < 54; slot++) {
            if (slot == REWARD_SLOT && !session.rewardPlaceholder) continue;
            ItemStack item = session.inventory.getItem(slot);
            if (!empty(item) && cursor.isSimilar(item)) return true;
        }
        return false;
    }

    private static void updateRewardPlaceholder(Session session) {
        if (!session.rewardPlaceholder && empty(session.inventory.getItem(REWARD_SLOT))) {
            session.rewardPlaceholder = true;
            session.inventory.setItem(REWARD_SLOT, MenuItems.button(
                    Material.STAINED_GLASS_PANE, (short) 14, "&c请放入兑换奖励", List.of("&7在此放入最终兑换奖励")
            ));
        }
    }

    private static ItemStack[] copy(ItemStack[] items) {
        return Arrays.stream(items).map(ExchangeEditor::cloneItem).toArray(ItemStack[]::new);
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private ItemAmount identify(ItemStack stack) {
        var identity =
                plugin.items().identify(stack).orElseThrow(() -> new IllegalArgumentException("Unidentified item"));
        if (plugin.items().find(identity.internalName()).isEmpty()) throw new IllegalArgumentException("Unknown item");
        return new ItemAmount(identity.internalName(), stack.getAmount());
    }

    private static boolean editable(int slot) {
        return slot >= 0 && (slot < 45 || slot == REWARD_SLOT);
    }

    private Session session(Inventory inventory, UUID player) {
        Session session = sessions.get(player);
        return session != null && inventory.getHolder() == session ? session : null;
    }

    private static List<ItemStack> takeInputs(Session session) {
        Inventory inventory = session.inventory;
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < 54; slot++) {
            if (!editable(slot)) continue;
            ItemStack item = inventory.getItem(slot);
            if (!session.samples.contains(slot) && !(slot == REWARD_SLOT && session.rewardPlaceholder) &&
                    !empty(item)) {
                items.add(item.clone());
            }
            inventory.setItem(slot, null);
        }
        return items;
    }

    private static void returnItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (!player.isOnline()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                continue;
            }
            player.getInventory().addItem(item).values()
                    .forEach(remaining -> player.getWorld().dropItemNaturally(player.getLocation(), remaining));
        }
    }

    private static final class Session implements InventoryHolder {
        private final ExchangeOffer offer;
        private final String id;
        private final UUID npc;
        private final String stage;
        private final Consumer<Player> back;
        private final Set<Integer> samples = new HashSet<>();
        private boolean rewardPlaceholder;
        private boolean pending;
        private Inventory inventory;
        private boolean saving;

        private Session(
                ExchangeOffer offer, String id, UUID npc, String stage, Consumer<Player> back
        ) {
            this.offer = offer;
            this.id = id;
            this.npc = npc;
            this.stage = stage;
            this.back = back;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
