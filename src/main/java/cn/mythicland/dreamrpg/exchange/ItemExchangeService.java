package cn.mythicland.dreamrpg.exchange;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamnpc.api.DreamNPCApi;
import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpg.campaign.CampaignCatalog;
import cn.mythicland.dreamrpg.storage.PlayerStorageService;
import cn.mythicland.dreamrpgitems.api.DreamRPGItemApi;
import cn.mythicland.dreamrpgitems.service.MythicItemException;
import cn.mythicland.framework.bukkit.MainThread;
import cn.mythicland.framework.bukkit.component.ListenerComponent;
import cn.mythicland.framework.bukkit.item.InventoryExchange;
import cn.mythicland.framework.bukkit.menu.MenuItems;
import cn.mythicland.framework.bukkit.text.LegacyText;
import cn.mythicland.framework.component.InjectComponent;
import cn.mythicland.framework.component.ManagedLifecycle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.logging.Level;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.scheduler.BukkitTask;

/**
 * Item-only task and shop exchanges, committed through the existing player storage session.
 */
@InjectComponent
@ListenerComponent
public final class ItemExchangeService implements ManagedLifecycle, Listener {
    private final DreamRPGPlugin plugin;
    private final CampaignCatalog catalog;
    private final PlayerStorageService storage;
    private final DreamLibApi lib;
    private final Set<Player> pending = new HashSet<>();
    private final Map<Player, MerchantSession> merchants = new HashMap<>();
    private BukkitTask merchantRefresh;
    private boolean enabled;

    public ItemExchangeService(
            DreamRPGPlugin plugin,
            CampaignCatalog catalog,
            PlayerStorageService storage,
            DreamLibApi lib
    ) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.storage = storage;
        this.lib = lib;
    }

    @Override
    public void load() {
        enabled = true;
    }

    @Override
    public void reload() {
        // CampaignCatalog owns reloadable definitions. Pending SQL commits must finish unchanged.
    }

    @Override
    public void unload() {
        enabled = false;
        for (Player player : List.copyOf(merchants.keySet())) {
            if (player.getOpenInventory() == merchants.get(player).view()) player.closeInventory();
            removeMerchant(player);
        }
        for (Player player : pending) lib.playerLoadingGate().cancel(player);
        pending.clear();
    }

    public void openMerchant(Player player, UUID npc, String title) {
        if (!enabled || !catalog.usesMerchant(npc) || !storage.isReady(player.getUniqueId())
                || lib.playerLoadingGate().isRestricted(player)) return;
        if (!empty(player.getItemOnCursor())) {
            player.sendMessage("§c请先放下鼠标上的物品。");
            return;
        }
        List<ExchangeOffer> offers = catalog.placementsForNpc(npc).stream()
                .filter(Objects::nonNull).toList();
        List<MerchantRecipe> recipes;
        try {
            CampaignCatalog.validateMerchantShape(offers);
            recipes = offers.stream().map(offer -> merchantRecipe(plugin.items(), offer)).toList();
        } catch (IllegalArgumentException error) {
            player.sendMessage("§c商店暂不可用: " + error.getMessage());
            return;
        }
        // Same native merchant window that Shopkeepers opens on 1.12.
        var merchant = Bukkit.createMerchant(LegacyText.colorize(title));
        merchant.setRecipes(recipes);
        InventoryView view = player.openMerchant(merchant, true);
        if (view == null || !(view.getTopInventory() instanceof MerchantInventory inventory)) return;
        MerchantSession session = new MerchantSession(npc, view, inventory, offers, recipes);
        merchants.put(player, session);
        storage.trackMerchant(player, inventory);
        updateMerchantPreview(session);
        if (merchantRefresh == null) merchantRefresh = lib.runTimer(1L, 1L, this::refreshMerchants);
    }

    public static MerchantRecipe merchantRecipe(DreamRPGItemApi items, ExchangeOffer offer) {
        ItemStack result = merchantItem(items, offer.reward().item());
        if (offer.reward().amount() > result.getMaxStackSize()) {
            throw new IllegalArgumentException("奖励 " + offer.reward().item() + " 无法放入一个交易格。");
        }
        result.setAmount(offer.reward().amount());
        MerchantRecipe recipe = new MerchantRecipe(result, 0, 10000, false);
        Map<String, Integer> amounts = new LinkedHashMap<>();
        for (ItemAmount cost : offer.requires()) amounts.merge(cost.item(), cost.amount(), Integer::sum);
        int slots = 0;
        for (var entry : amounts.entrySet()) {
            ItemStack item = merchantItem(items, entry.getKey());
            int remaining = entry.getValue();
            while (remaining > 0) {
                if (++slots > 2) throw new IllegalArgumentException(offer.id() + " 需要超过两个材料格。");
                ItemStack ingredient = item.clone();
                ingredient.setAmount(Math.min(remaining, item.getMaxStackSize()));
                recipe.addIngredient(ingredient);
                remaining -= ingredient.getAmount();
            }
        }
        return recipe;
    }

    private static ItemStack merchantItem(DreamRPGItemApi items, String key) {
        ItemStack stack;
        try {
            stack = items.getItemStack(key, 1);
        } catch (MythicItemException error) {
            throw new IllegalArgumentException("无法生成物品 " + key, error);
        }
        if (empty(stack)) throw new IllegalArgumentException("无法生成物品 " + key);
        return stack;
    }

    private void refreshMerchants() {
        for (var entry : List.copyOf(merchants.entrySet())) {
            Player player = entry.getKey();
            MerchantSession session = entry.getValue();
            if (player.getOpenInventory() != session.view()) removeMerchant(player);
            else if (!catalog.usesMerchant(session.npc())) player.closeInventory();
            else updateMerchantPreview(session);
        }
    }

    private void updateMerchantPreview(MerchantSession session) {
        if (hasInputs(session.inventory())) return;
        int index = session.inventory().getSelectedRecipeIndex();
        ItemStack result = index >= 0 && index < session.recipes().size()
                ? session.recipes().get(index).getResult() : null;
        // When inputs are empty this is a preview for the backpack shortcut, not a native result.
        if (!Objects.equals(result, session.inventory().getItem(2))) {
            session.inventory().setItem(2, result == null ? null : result.clone());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMerchantClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        MerchantSession session = merchants.get(player);
        if (session == null || session.view() != event.getView()) return;
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true); // A double-click elsewhere must not collect the synthetic result.
            return;
        }
        if (event.getRawSlot() != 2 || event.isCancelled()) return;
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) {
            event.setCancelled(true);
            return;
        }
        int index = session.inventory().getSelectedRecipeIndex();
        if (!isCurrentOffer(session, index)) {
            event.setCancelled(true);
            reject(player, "§e兑换配置已更新, 请重新打开商店。");
            return;
        }
        if (hasInputs(session.inventory())) {
            if (!nativeResultMatches(session, index, event.getCurrentItem())
                    || event.getAction() == InventoryAction.NOTHING
                    || (event.isShiftClick() && !hasRoom(player, event.getCurrentItem()))) {
                event.setCancelled(true);
                reject(player, null);
            }
            return; // Vanilla owns the real input slots, cursor and shift-click transaction.
        }
        event.setCancelled(true);
        if (click != ClickType.LEFT && click != ClickType.SHIFT_LEFT) return;
        if (!empty(event.getCursor())) {
            reject(player, null);
            return;
        }
        if (!session.recipes().get(index).getResult().equals(event.getCurrentItem())) return;
        lib.runLater(1L, () -> {
            if (merchants.get(player) != session || player.getOpenInventory() != session.view()
                    || session.inventory().getSelectedRecipeIndex() != index || !catalog.usesMerchant(session.npc())
                    || hasInputs(session.inventory()) || !empty(player.getItemOnCursor())) return;
            submit(player, session.offers().get(index), click == ClickType.SHIFT_LEFT);
        });
    }

    private boolean isCurrentOffer(MerchantSession session, int index) {
        if (index < 0 || index >= session.offers().size() || !catalog.usesMerchant(session.npc())) return false;
        ExchangeOffer offer = session.offers().get(index);
        return catalog.find(offer.id()).orElse(null) == offer;
    }

    private static boolean hasRoom(Player player, ItemStack reward) {
        int remaining = reward.getAmount();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (empty(item)) return true;
            if (item.isSimilar(reward)) remaining -= Math.max(0, reward.getMaxStackSize() - item.getAmount());
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static boolean hasInputs(MerchantInventory inventory) {
        return !empty(inventory.getItem(0)) || !empty(inventory.getItem(1));
    }

    private static boolean nativeResultMatches(MerchantSession session, int index, ItemStack result) {
        MerchantRecipe expected = session.recipes().get(index);
        if (!expected.getResult().equals(result)) return false;
        var ingredients = expected.getIngredients();
        ItemStack first = session.inventory().getItem(0);
        ItemStack second = session.inventory().getItem(1);
        ItemStack other = ingredients.size() == 2 ? ingredients.get(1) : null;
        return matchesIngredient(first, ingredients.getFirst()) && matchesIngredient(second, other)
                || matchesIngredient(second, ingredients.getFirst()) && matchesIngredient(first, other);
    }

    private static boolean matchesIngredient(ItemStack actual, ItemStack expected) {
        if (expected == null) return empty(actual);
        return !empty(actual) && actual.isSimilar(expected) && actual.getAmount() >= expected.getAmount();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMerchantDrag(InventoryDragEvent event) {
        MerchantSession session = merchants.get(event.getWhoClicked());
        if (session != null && session.view() == event.getView() && event.getRawSlots().contains(2)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMerchantClose(InventoryCloseEvent event) {
        MerchantSession session = merchants.get(event.getPlayer());
        if (session != null && session.view() == event.getView()) removeMerchant((Player) event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMerchantQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        MerchantSession session = merchants.get(player);
        if (session != null && player.getOpenInventory() == session.view()) player.closeInventory();
        removeMerchant(player);
    }

    private void removeMerchant(Player player) {
        if (merchants.remove(player) != null) storage.untrackMerchant(player);
        if (merchants.isEmpty()) stopMerchantRefresh();
    }

    private void stopMerchantRefresh() {
        if (merchantRefresh != null) merchantRefresh.cancel();
        merchantRefresh = null;
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private record MerchantSession(
            UUID npc,
            InventoryView view,
            MerchantInventory inventory,
            List<ExchangeOffer> offers,
            List<MerchantRecipe> recipes
    ) {
    }

    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        DreamNPCApi npcs = Bukkit.getServicesManager().load(DreamNPCApi.class);
        for (ExchangeOffer offer : catalog.offers()) {
            for (ItemAmount reference : Stream.concat(offer.requires().stream(), Stream.of(offer.reward())).toList()) {
                if (plugin.items().find(reference.item()).isEmpty())
                    problems.add(offer.id() + " / item: " + reference.item());
            }
            if (npcs == null || !npcs.exists(offer.npc())) problems.add(offer.id() + " / NPC: " + offer.npc());
        }
        return List.copyOf(problems);
    }

    public ItemStack icon(ExchangeOffer offer) {
        ItemStack icon = MenuItems.icon(plugin.items().getItemStack(offer.reward().item(), 1));
        icon.setAmount(Math.min(64, offer.reward().amount()));
        var meta = icon.getItemMeta();
        List<String> lore = new ArrayList<>();
        if (meta.hasLore()) lore.addAll(meta.getLore());
        lore.add("");
        lore.add("§7" + offer.type().requirements());
        for (ItemAmount cost : offer.requires()) {
            String display = plugin.items().find(cost.item()).orElseThrow().summary().displayName();
            lore.add(LegacyText.colorize("&8- " + display + " &fx" + cost.amount()));
        }
        if (offer.reward().amount() > 64) lore.add("§7奖励数量: §f" + offer.reward().amount());
        lore.add("");
        lore.add("§e" + offer.type().action());
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    /**
     * Applies one rendered offer without yielding, then restricts transfers until its SQL flush completes.
     * Rechecks the current configuration, session readiness, item identities and reward capacity.
     */
    public void submit(Player player, ExchangeOffer rendered, boolean bulk) {
        MainThread.require("Item exchanges require the primary thread");
        if (!enabled || !storage.isReady(player.getUniqueId()) || lib.playerLoadingGate().isRestricted(player)) return;
        if (catalog.find(rendered.id()).orElse(null) != rendered) {
            reject(player, "§e任务或兑换配置已更新, 请查看刷新后的内容再提交。");
            lib.menuService().refresh(player);
            return;
        }
        List<ItemStack> costs = rendered.requires().stream().map(this::materialize).toList();
        ItemStack reward = materialize(rendered.reward());
        BiPredicate<ItemStack, ItemStack> matches = (item, requirement) -> {
            var actual = plugin.items().identify(item);
            var expected = plugin.items().identify(requirement).orElseThrow();
            return actual.map(identity -> identity.internalName().equals(expected.internalName())).orElse(false);
        };
        ItemStack[] contents = player.getInventory().getStorageContents();
        InventoryExchange.Result result;
        if (bulk) result = InventoryExchange.planMaximum(contents, costs, List.of(reward), matches);
        else result = InventoryExchange.plan(contents, costs, List.of(reward), matches);
        if (result.status() == InventoryExchange.Status.MISSING_ITEMS) {
            reject(player, "§c所需物品不足!");
            return;
        }
        if (result.status() == InventoryExchange.Status.NO_SPACE) {
            reject(player, "§c背包没有足够空间领取奖励!");
            return;
        }
        lib.playerLoadingGate().beginQuietly(player);
        pending.add(player);
        player.getInventory().setStorageContents(result.contents());
        storage.capture(player);
        storage.flush(player.getUniqueId()).whenComplete((ignored, failure) -> lib.runOnMain(() -> {
            pending.remove(player);
            if (failure != null)
                plugin.getLogger()
                        .log(Level.SEVERE, "Item exchange persistence failed for " + player.getUniqueId(), failure);
            if (!enabled || Bukkit.getPlayer(player.getUniqueId()) != player) return;
            if (failure != null) {
                player.kickPlayer("物品数据保存失败, 请联系管理员后重新登录。");
                return;
            }
            lib.playerLoadingGate().ready(player);
            player.sendMessage("§a" + rendered.type().success());
            lib.menuService().refresh(player);
        }));
    }

    private static void reject(Player player, String message) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
        if (message != null) player.sendMessage(message);
    }

    private ItemStack materialize(ItemAmount reference) {
        ItemStack item = plugin.items().getItemStack(reference.item(), 1);
        item.setAmount(reference.amount());
        return item;
    }
}
