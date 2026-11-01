package cn.mythicland.dreamrpg.command;

import cn.mythicland.framework.bukkit.command.CommandArgument;
import cn.mythicland.framework.bukkit.command.CommandBinding;
import cn.mythicland.framework.bukkit.command.CommandContext;
import cn.mythicland.framework.bukkit.command.CommandRoute;
import cn.mythicland.framework.bukkit.command.SuggestionSource;
import cn.mythicland.framework.bukkit.command.VanillaCommandMessages;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Restores a player's health to their current maximum health. */
@CommandBinding(value = "heal", permission = "dreamrpg.admin")
public final class HealCommand {

    @CommandRoute(
            path = {"heal"},
            usages = "/heal [在线玩家]",
            minArguments = 0,
            maxArguments = 1,
            arguments = @CommandArgument(index = 0, source = SuggestionSource.ONLINE_PLAYERS)
    )
    void heal(CommandContext context) {
        CommandSender sender = context.sender();
        Player target;
        if (context.argumentCount() == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(VanillaCommandMessages.red("控制台请指定在线玩家。"));
                return;
            }
            target = player;
        } else {
            target = Bukkit.getPlayerExact(context.argument(0));
            if (target == null) {
                sender.sendMessage(VanillaCommandMessages.red("目标玩家必须在线。"));
                return;
            }
        }

        target.setHealth(target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        if (target == sender) sender.sendMessage(VanillaCommandMessages.green("你的生命值已恢复至满值。"));
        else {
            target.sendMessage(VanillaCommandMessages.green("你的生命值已恢复至满值。"));
            sender.sendMessage(VanillaCommandMessages.green("已恢复玩家 " + target.getName() + " 的满生命值。"));
        }
    }
}
