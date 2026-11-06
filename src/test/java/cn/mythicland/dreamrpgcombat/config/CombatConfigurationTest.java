package cn.mythicland.dreamrpgcombat.config;

import cn.mythicland.dreamrpgcombat.lore.CombatStat;
import cn.mythicland.framework.bukkit.config.ConfigSupport;
import cn.mythicland.framework.bukkit.config.ConfigView;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CombatConfigurationTest {

    @Test
    void bindsSharedPenetrationLabelsAndIgnoresRemovedKeys() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("labels.physical-penetration", "旧物理穿透");
        yaml.set("labels.magic-penetration", "旧法术穿透");
        yaml.set("labels.penetration", "忽略防御值");
        yaml.set("labels.penetration-percent", "忽略防御比例");
        ConfigView view = mock(ConfigView.class);
        when(view.bind(any())).thenAnswer(invocation -> ConfigSupport.bind(yaml, invocation.getArgument(0)));
        CombatConfiguration configuration = new CombatConfiguration();
        configuration.reload(view);
        assertEquals("忽略防御值", configuration.snapshot().label(CombatStat.PENETRATION));
        assertEquals("忽略防御比例", configuration.snapshot().label(CombatStat.PENETRATION_PERCENT));

        yaml.set("labels.penetration", null);
        yaml.set("labels.penetration-percent", null);
        configuration.reload(view);
        assertEquals("穿透值", configuration.snapshot().label(CombatStat.PENETRATION));
        assertEquals("穿透率", configuration.snapshot().label(CombatStat.PENETRATION_PERCENT));
    }
}
