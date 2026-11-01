package cn.mythicland.dreamrpg.command;

import cn.mythicland.framework.bukkit.command.CommandRoute;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadCommandRoutesTest {
    @Test
    void dreamRpgExposesSystemAndIndependentContentReloadCommands() {
        List<CommandRoute> handlers = java.util.stream.Stream.of(DreamRPGCommand.class, ItemCommands.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(method -> method.getAnnotation(CommandRoute.class))
                .filter(java.util.Objects::nonNull)
                .filter(handler -> path(handler).equals("reload") || path(handler).endsWith(" reload"))
                .toList();
        assertEquals(java.util.Set.of("reload", "content reload"), handlers.stream().map(ReloadCommandRoutesTest::path).collect(java.util.stream.Collectors.toSet()));
        assertTrue(handlers.stream().allMatch(CommandRoute::showInHelp));
    }

    @Test
    void itemListRemainsAvailableWithoutAnNpcDirectory() {
        List<String> paths = java.util.stream.Stream.of(DreamRPGCommand.class, ItemCommands.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(method -> method.getAnnotation(CommandRoute.class))
                .filter(java.util.Objects::nonNull)
                .map(ReloadCommandRoutesTest::path)
                .toList();
        assertTrue(paths.contains("items list"));
        assertFalse(paths.contains("npc list"));
        assertTrue(paths.contains("npc open"));
        assertFalse(paths.contains("items"));
        assertFalse(paths.contains("exchanges"));
        assertFalse(paths.contains("stages"));
        assertFalse(paths.contains("quests"));
        assertFalse(paths.contains(""));
    }

    @Test
    void obsoleteCommandsAreNotRegistered() {
        for (Class<?> type : List.of(DreamRPGCommand.class, ItemCommands.class)) {
            assertTrue(Arrays.stream(type.getDeclaredMethods())
                    .map(method -> method.getAnnotation(CommandRoute.class))
                    .filter(java.util.Objects::nonNull)
                    .noneMatch(handler -> List.of("npc", "check", "stats", "first-rewards")
                            .contains(path(handler))));
        }
    }

    private static String path(CommandRoute route) {
        return String.join(" ", Arrays.copyOfRange(route.path(), 1, route.path().length));
    }
}
