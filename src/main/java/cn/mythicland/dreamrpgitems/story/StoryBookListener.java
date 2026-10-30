package cn.mythicland.dreamrpgitems.story;

import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamlib.bootstrap.annotation.InjectComponent;
import cn.mythicland.dreamlib.bootstrap.annotation.ListenerComponent;
import cn.mythicland.dreamlib.text.LegacyText;
import cn.mythicland.dreamrpgitems.service.MythicItemService;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Opens configured stories for identity-marked MM items.
 */
@InjectComponent
@ListenerComponent
public final class StoryBookListener implements Listener {
    private final MythicItemService items;
    private final DreamLibApi lib;
    private final Logger logger;

    public StoryBookListener(MythicItemService items, DreamLibApi lib, Logger logger) {
        this.items = Objects.requireNonNull(items, "items");
        this.lib = Objects.requireNonNull(lib, "lib");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Bukkit can deny block use independently of item use, including right-clicking air.
        if (event.useItemInHand() == Event.Result.DENY || lib.playerLoadingGate().isLoading(event.getPlayer())) {
            return;
        }
        ItemStack held = event.getItem();
        if (held == null || held.getType() == Material.AIR) {
            return;
        }
        var identity = items.identify(held);
        if (identity.isEmpty()) {
            return;
        }
        var definition = items.configuration(identity.orElseThrow().internalName());
        if (definition.isEmpty()) {
            return;
        }
        try {
            StoryConfiguration story = StoryConfiguration.from(definition.orElseThrow());
            if (story.pages().isEmpty()) {
                return;
            }
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta meta = (BookMeta) book.getItemMeta();
            meta.setTitle("Story");
            meta.setAuthor("DreamRPG");
            meta.setPages(story.pages().stream().map(LegacyText::colorize).toList());
            book.setItemMeta(meta);
            lib.bookDisplayService().open(event.getPlayer(), book);
            event.setCancelled(true);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            logger.log(Level.WARNING, "Cannot open story for " + identity.orElseThrow().internalName(), exception);
        }
    }
}
