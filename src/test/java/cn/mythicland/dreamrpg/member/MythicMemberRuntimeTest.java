package cn.mythicland.dreamrpg.member;

import cn.mythicland.dreamlib.database.SharedDatabase;
import cn.mythicland.memberlib.DatabaseMode;
import cn.mythicland.memberlib.MemberLib;
import cn.mythicland.memberlib.MemberService;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MythicMemberRuntimeTest {
    @Test
    void usesGameDatabaseWithoutClosingBorrowedResources() throws Exception {
        var plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var database = mock(SharedDatabase.class);
        var source = mock(DataSource.class);
        var service = mock(MemberService.class);
        when(database.mode()).thenReturn("postgres");
        when(database.dataSource()).thenReturn(source);
        try (var libs = mockConstruction(MemberLib.class, (lib, context) -> when(lib.service()).thenReturn(service))) {
            var runtime = new MythicMemberRuntime(plugin, database);
            var lib = libs.constructed().getFirst();
            verify(lib).enable(source, DatabaseMode.POSTGRESQL);
            assertSame(service, runtime.service());
            runtime.close();
            verify(lib).close();
            verify(database, never()).close();
            verifyNoInteractions(source);
        }
    }

    @Test
    void rejectsUnsupportedGameDatabaseBeforeRegisteringMemberships() {
        var plugin = mock(JavaPlugin.class);
        var database = mock(SharedDatabase.class);
        var service = mock(MemberService.class);
        when(database.mode()).thenReturn("sqlite");
        try (var libs = mockConstruction(MemberLib.class, (lib, context) -> when(lib.service()).thenReturn(service))) {
            assertThrows(IllegalStateException.class, () -> new MythicMemberRuntime(plugin, database));
            verify(libs.constructed().getFirst()).close();
            verifyNoInteractions(service);
            verify(database, never()).dataSource();
        }
    }

    @Test
    void initializationFailureClosesOnlyMembershipRuntimeAndPropagatesFailure() {
        var plugin = mock(JavaPlugin.class);
        var database = mock(SharedDatabase.class);
        var source = mock(DataSource.class);
        var service = mock(MemberService.class);
        var failure = new IllegalStateException("Membership initialization failed");
        when(database.mode()).thenReturn("postgres");
        when(database.dataSource()).thenReturn(source);
        try (var libs = mockConstruction(MemberLib.class, (lib, context) -> {
            when(lib.service()).thenReturn(service);
            doThrow(failure).when(lib).enable(source, DatabaseMode.POSTGRESQL);
        })) {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> new MythicMemberRuntime(plugin, database)));
            verify(libs.constructed().getFirst()).close();
            verifyNoInteractions(source);
        }
    }
}
