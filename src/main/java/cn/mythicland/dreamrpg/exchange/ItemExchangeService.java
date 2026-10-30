package cn.mythicland.dreamrpg.exchange;

import cn.mythicland.dreamnpc.api.DreamNPCApi;
import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.dreamrpg.campaign.CampaignCatalog;
import cn.mythicland.dreamrpg.storage.PlayerStorageService;
import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.bootstrap.DreamLibPluginLifecycle;
import cn.mythicland.dreamlib.bootstrap.annotation.InjectComponent;
import cn.mythicland.dreamlib.item.InventoryExchange;
import cn.mythicland.dreamlib.menu.MenuItems;
import cn.mythicland.dreamlib.text.LegacyText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Item-only task and shop exchanges, committed through the existing player storage session.
 */
@InjectComponent
public final class ItemExchangeService implements DreamLibPluginLifecycle {
    private final DreamRPGPlugin plugin;
    private final CampaignCatalog catalog;
    private final PlayerStorageService storage;
    private final DreamLibApi lib;
    private final Set<Player> pending = new HashSet<>();
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
    public void enable() {
        enabled = true;
    }

    @Override
    public void reload() {
        // CampaignCatalog owns reloadable definitions. Pending SQL commits must finish unchanged.
    }

    @Override
    public void disable() {
        enabled = false;
        for (Player player : pending) lib.playerLoadingGate().cancel(player);
        pending.clear();
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
        boolean task = catalog.isTask(offer.id());
        lore.add(task ? "§7任务所需:" : "§7兑换所需:");
        for (ItemAmount cost : offer.requires()) {
            String display = plugin.items().find(cost.item()).orElseThrow().summary().displayName();
            lore.add(LegacyText.colorize("&8- " + display + " &fx" + cost.amount()));
        }
        if (offer.reward().amount() > 64) lore.add("§7奖励数量: §f" + offer.reward().amount());
        lore.add("");
        lore.add(task ? "§e点击提交!" : "§e点击兑换!");
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    /**
     * Applies one rendered offer without yielding, then restricts transfers until its SQL flush completes.
     * Rechecks the current configuration, session readiness, item identities and reward capacity.
     */
    public void submit(Player player, ExchangeOffer rendered, boolean bulk) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Item exchanges require the primary thread");
        if (!enabled || !storage.isReady(player.getUniqueId()) || lib.playerLoadingGate().isRestricted(player)) return;
        if (catalog.find(rendered.id()).orElse(null) != rendered) {
            player.sendMessage("§e任务或兑换配置已更新, 请查看刷新后的内容再提交。");
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
            player.sendMessage("§c所需物品不足!");
            return;
        }
        if (result.status() == InventoryExchange.Status.NO_SPACE) {
            player.sendMessage("§c背包没有足够空间领取奖励!");
            return;
        }
        lib.playerLoadingGate().beginQuietly(player);
        pending.add(player);
        player.getInventory().setStorageContents(result.contents());
        storage.capture(player);
        storage.flush(player.getUniqueId()).whenComplete((ignored, failure) -> lib.runOnMain(() -> {
            pending.remove(player);
            if (failure != null)
                plugin.getLogger().log(Level.SEVERE, "Item exchange persistence failed for " + player.getUniqueId(), failure);
            if (!enabled || Bukkit.getPlayer(player.getUniqueId()) != player) return;
            if (failure != null) {
                player.kickPlayer("物品数据保存失败, 请联系管理员后重新登录。");
                return;
            }
            lib.playerLoadingGate().ready(player);
            player.sendMessage("§a兑换成功!");
            lib.menuService().refresh(player);
        }));
    }

    private ItemStack materialize(ItemAmount reference) {
        ItemStack item = plugin.items().getItemStack(reference.item(), 1);
        item.setAmount(reference.amount());
        return item;
    }
}
