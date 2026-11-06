package cn.mythicland.dreamrpg.command;

import cn.mythicland.framework.bukkit.command.CommandRoute;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadCommandRoutesTest {
    @Test
    void dreamRpgExposesSystemAndIndependentContentReloadCommands() {
        List<CommandRoute> handlers = Stream.of(DreamRPGCommand.class, ItemCommands.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(method -> method.getAnnotation(CommandRoute.class))
                .filter(Objects::nonNull)
                .filter(handler -> path(handler).equals("reload") || path(handler).endsWith(" reload"))
                .toList();
        assertEquals(Set.of("reload", "content reload"),
                handlers.stream()
                        .map(ReloadCommandRoutesTest::path)
                        .collect(Collectors.toSet())
        );
        assertTrue(handlers.stream().allMatch(CommandRoute::showInHelp));
    }

    @Test
    void itemListRemainsAvailableWithoutAnNpcDirectory() {
        List<String> paths = Stream.of(DreamRPGCommand.class, ItemCommands.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(method -> method.getAnnotation(CommandRoute.class))
                .filter(Objects::nonNull)
                .map(ReloadCommandRoutesTest::path)
                .toList();
        assertTrue(paths.contains("items list"));
        assertFalse(paths.contains("npc list"));
        assertTrue(paths.contains("npc open"));
        assertFalse(paths.contains("items"));
        assertFalse(paths.contains("exchanges"));
        assertFalse(paths.contains("stages"));
        assertFalse(paths.contains("quests"));
    }

    @Test
    void tradeAliasUsesTheRootRouteWithExactlyOnePlayerArgument() {
        List<CommandRoute> roots = Stream.of(DreamRPGCommand.class, ItemCommands.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(method -> method.getAnnotation(CommandRoute.class))
                .filter(Objects::nonNull)
                .filter(route -> path(route).isEmpty())
                .toList();
        assertEquals(1, roots.size());
        CommandRoute trade = roots.getFirst();
        assertEquals(List.of("dreamrpg"), Arrays.asList(trade.path()));
        assertEquals(1, trade.minArguments());
        assertEquals(1, trade.maxArguments());
        assertEquals(List.of("/trade <在线玩家>"), Arrays.asList(trade.usages()));
        assertEquals("", trade.permission());
        assertTrue(trade.executable());
    }

    @Test
    void mobLibraryIsOnlyReachableThroughItsListSubcommand() {
        List<String> paths = Stream.of(DreamRPGCommand.class, ItemCommands.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(method -> method.getAnnotation(CommandRoute.class))
                .filter(Objects::nonNull)
                .map(ReloadCommandRoutesTest::path)
                .toList();
        assertTrue(paths.contains("mobs list"));
        assertFalse(paths.contains("mobs"));
    }

    @Test
    void obsoleteCommandsAreNotRegistered() {
        for (Class<?> type : List.of(DreamRPGCommand.class, ItemCommands.class)) {
            assertTrue(Arrays.stream(type.getDeclaredMethods())
                    .map(method -> method.getAnnotation(CommandRoute.class))
                    .filter(Objects::nonNull)
                    .noneMatch(handler -> List.of("npc", "check", "stats", "first-rewards")
                            .contains(path(handler))));
        }
    }

    private static String path(CommandRoute route) {
        return String.join(" ", Arrays.copyOfRange(route.path(), 1, route.path().length));
    }
}
