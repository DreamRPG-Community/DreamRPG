package cn.mythicland.dreamrpg.title;

import cn.mythicland.dreamrpg.display.PlayerNameFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerNameFormatTest {
    @Test
    void ordersLegacyAndExplicitTemplatesAndKeepsTabPadding() {
        String ordered = "{lp_prefix} {title} {prefix} {name} {member_prefix}";
        assertEquals(ordered, PlayerNameFormat.ordered("{member_prefix} {prefix} {name}", true, true));
        assertEquals(ordered, PlayerNameFormat.ordered(ordered, true, true));
        assertEquals("{title} {prefix} {name} {member_prefix}        ",
                PlayerNameFormat.ordered("{member_prefix} {prefix} {name}        ", true, false));
        assertEquals("{prefix} {name} {member_prefix}&7: &f{message}",
                PlayerNameFormat.ordered("%luckperms_prefix% {prefix} {name}&7: &f{message}", false, false));
    }
}
