package cn.mythicland.dreamrpg.member;

import cn.mythicland.dreamlib.database.SharedDatabase;
import cn.mythicland.memberlib.DatabaseMode;
import cn.mythicland.memberlib.MemberDefinition;
import cn.mythicland.memberlib.MemberKey;
import cn.mythicland.memberlib.MemberLib;
import cn.mythicland.memberlib.MemberScope;
import cn.mythicland.memberlib.MemberService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Embeds MythicMemberLib into DreamRPG and stores membership records in the shared game database.
 */
public final class MythicMemberRuntime implements AutoCloseable {

    public static final MemberKey VIP_PLUS = MemberKey.of("mythicmembership", "vip_plus");
    public static final MemberKey MVP_PLUS = MemberKey.of("mythicmembership", "mvp_plus");
    public static final MemberKey MVP_PLUS_PLUS = MemberKey.of("mythicmembership", "mvp_plus_plus");

    private final MemberLib memberLib;
    private final MemberService service;

    public MythicMemberRuntime(
            JavaPlugin plugin,
            SharedDatabase database
    ) {
        this.memberLib = new MemberLib(Objects.requireNonNull(plugin, "plugin"));
        this.service = this.memberLib.service();
        try {
            Objects.requireNonNull(database, "database");
            if (!database.mode().equals("postgres")) {
                throw new IllegalStateException("DreamRPG membership requires DreamLib PostgreSQL mode");
            }
            this.registerDefinitions();
            this.memberLib.enable(
                    database.dataSource(),
                    DatabaseMode.POSTGRESQL
            );
        } catch (RuntimeException | Error exception) {
            try {
                this.memberLib.close();
            } catch (RuntimeException | Error cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
        plugin.getLogger().info("MythicMemberLib integration enabled.");
    }

    private void registerDefinitions() {
        this.service.register(new MemberDefinition(
                VIP_PLUS, MemberScope.NETWORK, "&a[VIP&6+&a]", "&a[VIP&6+&a]", 100
        ));
        this.service.register(new MemberDefinition(
                MVP_PLUS, MemberScope.NETWORK, "&b[MVP&c+&b]", "&b[MVP&c+&b]", 200
        ));
        this.service.register(new MemberDefinition(
                MVP_PLUS_PLUS, MemberScope.NETWORK, "&6[MVP&c++&6]", "&6[MVP&c++&6]", 300
        ));
    }

    public MemberService service() {
        return this.service;
    }

    @Override
    public void close() {
        this.memberLib.close();
    }
}
