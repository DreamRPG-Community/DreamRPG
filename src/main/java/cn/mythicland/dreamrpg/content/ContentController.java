package cn.mythicland.dreamrpg.content;

import cn.mythicland.framework.bukkit.content.ContentUpdate;
import cn.mythicland.dreamlib.api.DreamLibApi;
import cn.mythicland.dreamrpg.DreamRPGPlugin;
import cn.mythicland.framework.component.InjectComponent;
import org.bukkit.command.CommandSender;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.ArrayList;
import java.util.List;

/** Owns explicit content validation and reload independently of system configuration. */
@InjectComponent
public final class ContentController {
    static final List<String> KINDS = List.of("items", "mobs", "skills", "droptables", "spawners");
    private final DreamRPGPlugin plugin;
    private final DreamLibApi lib;
    private boolean busy;

    public ContentController(DreamRPGPlugin plugin, DreamLibApi lib) {
        this.plugin = plugin;
        this.lib = lib;
    }

    /**
     * Validates or reloads all editable content. Must be invoked on the server thread.
     *
     * @param sender administrator receiving the completion result
     * @param reload whether to reload and refresh consumers after validation
     */
    public void execute(CommandSender sender, boolean reload) {
        if (busy) {
            sender.sendMessage("§c内容检查或重载正在进行。");
            return;
        }
        busy = true;
        var applied = new ArrayList<ContentUpdate>();
        CompletableFuture<ContentValidation.Result> work = lib.supplyAsync(() -> {
            lib.content().reloadManifest();
            return ContentValidation.validate(lib.content(), lib.content().definitions(KINDS));
        });
        if (reload) work = work.thenCompose(result -> lib.runOnMain(() -> {
            if (!plugin.isEnabled()) throw new IllegalStateException("DreamRPG stopped before content reload");
            var prepared = plugin.prepareContent();
            var mythic = MythicContentRuntime.prepare(result.ids());
            applied.add(mythic);
            mythic.apply().run();
            for (var update : prepared) {
                applied.add(update);
                update.apply().run();
            }
            plugin.synchronizeContentItems();
        }).thenCompose(ignored -> plugin.reloadNpcContent()).thenApply(ignored -> result));
        work.whenComplete((result, error) -> lib.runOnMain(() -> {
            try {
                if (!plugin.isEnabled()) return;
                if (error == null) {
                    sender.sendMessage("§a内容" + (reload ? "重载" : "校验") + "完成: " + result.summary());
                    return;
                }
                for (int index = applied.size() - 1; index >= 0; index--) {
                    try {
                        applied.get(index).rollback().run();
                    } catch (RuntimeException restore) {
                        error.addSuppressed(restore);
                    }
                }
                Throwable cause = error;
                while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                sender.sendMessage("§c内容操作失败: " + cause.getMessage());
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Content operation failed", error);
            } finally {
                busy = false;
            }
        }));
    }
}
