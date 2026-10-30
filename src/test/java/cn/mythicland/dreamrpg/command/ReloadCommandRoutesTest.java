package cn.mythicland.dreamrpg.command;

import cn.mythicland.dreamlib.bootstrap.annotation.CommandHandler;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadCommandRoutesTest {
    @Test
    void dreamRpgExposesOnlyTheRootReloadCommand() {
        List<CommandHandler> handlers = java.util.stream.Stream.of(DreamRPGCommand.class, ItemCommands.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(method -> method.getAnnotation(CommandHandler.class))
                .filter(java.util.Objects::nonNull)
                .filter(handler -> handler.value().equals("reload") || handler.value().endsWith(" reload"))
                .toList();
        assertEquals(1, handlers.size());
        assertEquals("reload", handlers.getFirst().value());
        assertTrue(handlers.getFirst().showInHelp());
    }

    @Test
    void itemListRemainsAvailableWithoutAnNpcDirectory() {
        List<String> paths = java.util.stream.Stream.of(DreamRPGCommand.class, ItemCommands.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(method -> method.getAnnotation(CommandHandler.class))
                .filter(java.util.Objects::nonNull)
                .map(CommandHandler::value)
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
                    .map(method -> method.getAnnotation(CommandHandler.class))
                    .filter(java.util.Objects::nonNull)
                    .noneMatch(handler -> List.of("npc", "check", "stats", "first-rewards")
                            .contains(handler.value())));
        }
    }
}
