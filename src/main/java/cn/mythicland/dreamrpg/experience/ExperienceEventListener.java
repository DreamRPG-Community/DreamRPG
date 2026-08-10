package cn.mythicland.dreamrpg.experience;

import cn.mythicland.dreamrpg.api.ExperienceGrantRequest;
import cn.mythicland.lib.bootstrap.PluginTaskScope;
import cn.mythicland.lib.bootstrap.annotation.InjectComponent;
import cn.mythicland.lib.bootstrap.annotation.ListenerComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Locale;
import java.util.Objects;

/**
 * Converts native experience changes into DreamRPG grants and repairs external level mutations.
 * Experience-bottle systems can change {@code ExpBottleEvent#setExperience}; the resulting orbs
 * enter the same native pickup handler below.
 */
@InjectComponent
@ListenerComponent
public final class ExperienceEventListener implements Listener {

    private final ExperienceService experience;
    private final PluginTaskScope tasks;

    /**
     * Creates the native experience bridge.
     *
     * @param experience authoritative experience service
     * @param tasks      plugin task scope
     */
    public ExperienceEventListener(ExperienceService experience, PluginTaskScope tasks) {
        this.experience = Objects.requireNonNull(experience, "experience");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
    }

    /**
     * Replaces Bukkit's native player experience gain with one DreamRPG grant.
     *
     * @param event native experience change
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNativeExperience(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        int amount = event.getAmount();
        event.setAmount(0);
        if (amount <= 0) return;
        if (experience.isReady(player.getUniqueId())) {
            experience.grant(new ExperienceGrantRequest(
                    player.getUniqueId(),
                    amount,
                    "orb",
                    "native"
            ));
        } else {
            experience.queueNativeExperience(player.getUniqueId(), amount);
        }
    }

    /**
     * Reasserts the RPG projection if another plugin or command changes Bukkit's level.
     *
     * @param event native level change event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onNativeLevelChange(PlayerLevelChangeEvent event) {
        Player player = event.getPlayer();
        if (!experience.isReady(player.getUniqueId())) return;
        long level = experience.snapshot(player.getUniqueId()).level();
        int visibleLevel = level > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) level;
        if (player.getLevel() != visibleLevel) experience.syncPresentation(player);
    }

    /**
     * Reapplies the bar after Bukkit respawn handling has reset native presentation fields.
     *
     * @param event player respawn event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        tasks.runLater(1L, () -> {
            if (player.isOnline() && experience.isReady(player.getUniqueId())) {
                experience.syncPresentation(player);
            }
        });
    }

    /**
     * Prevents Bukkit's native {@code /xp} and {@code /experience} commands from changing the
     * presentation without changing DreamRPG's persisted state.
     *
     * @param event player command event
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void blockNativeExperienceCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().trim();
        if (command.startsWith("/")) command = command.substring(1);
        String label = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (label.equals("xp") || label.equals("experience")
                || label.equals("minecraft:xp") || label.equals("minecraft:experience")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("请使用 DreamRPG 经验接口修改经验。");
        }
    }
}
