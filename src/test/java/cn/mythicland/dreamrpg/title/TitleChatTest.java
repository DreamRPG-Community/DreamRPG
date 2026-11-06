package cn.mythicland.dreamrpg.title;

import cn.mythicland.dreamrpg.api.CareerDefinition;
import cn.mythicland.dreamrpg.api.PlayerProfile;
import cn.mythicland.dreamrpg.api.TitleApi;
import cn.mythicland.dreamrpg.api.TitleDefinition;
import cn.mythicland.dreamrpg.bootstrap.DreamRPGContext;
import cn.mythicland.dreamrpg.chat.DreamRPGChatListener;
import cn.mythicland.dreamrpg.config.DreamRPGSettings;
import cn.mythicland.dreamrpg.member.MemberAccessService;
import cn.mythicland.dreamrpg.profile.PlayerProfileService;
import cn.mythicland.framework.bukkit.integration.PlaceholderService;
import cn.mythicland.framework.bukkit.text.TemplateRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TitleChatTest {
    @Test
    void preservesLuckPermsPrefixAlongsideTitleAndEscapesPercentNames() {
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Tester");
        var placeholders = mock(PlaceholderService.class);
        when(placeholders.render(any(), anyString())).thenAnswer(call -> call.getArgument(1));
        var context = mock(DreamRPGContext.class);
        when(context.templates()).thenReturn(new TemplateRenderer(mock(JavaPlugin.class), placeholders));
        var settings = mock(DreamRPGSettings.class);
        when(context.settings()).thenReturn(settings);
        when(settings.chat()).thenReturn(new DreamRPGSettings.ChatSettings(
                "%luckperms_prefix% {prefix} {name}: {message}", "dreamrpg.chat.color"));
        var profiles = mock(PlayerProfileService.class);
        when(profiles.getProfile(id)).thenReturn(new PlayerProfile(id, "mage", new CareerDefinition("mage", "法师", "&b[法师]")));
        var members = mock(MemberAccessService.class);
        when(members.permissionPrefix(player)).thenReturn("&c[管理]");
        when(members.memberSuffix(player)).thenReturn("&a[VIP]");
        var titles = mock(TitleApi.class);
        when(titles.equipped(id)).thenReturn(Optional.of(new TitleDefinition("hero", "&6[100%勇者]", Map.of())));
        var listener = new DreamRPGChatListener(context, profiles, members, titles);
        var event = new AsyncPlayerChatEvent(true, player, "Hello", new HashSet<>());
        listener.onAsyncChat(event);
        String rendered = String.format(event.getFormat(), "Tester", "Hello");
        assertTrue(rendered.contains("§c[管理]"), "LP staff prefix must remain visible");
        assertTrue(rendered.contains("§6[100%勇者]"));
        assertTrue(rendered.indexOf("管理") < rendered.indexOf("Tester"));
        assertTrue(rendered.indexOf("勇者") < rendered.indexOf("法师"));
        assertTrue(rendered.indexOf("法师") < rendered.indexOf("Tester"));
        assertTrue(rendered.indexOf("VIP") > rendered.indexOf("Tester"));
        assertEquals(1, rendered.split("勇者", -1).length - 1);
        when(titles.equipped(id)).thenReturn(Optional.empty());
        var cleared = new AsyncPlayerChatEvent(true, player, "Hello", new HashSet<>());
        listener.onAsyncChat(cleared);
        assertTrue(cleared.getFormat().contains("§c[管理]"));
        assertFalse(cleared.getFormat().contains("勇者"));
    }
}
