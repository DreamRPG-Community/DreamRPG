package cn.mythicland.dreamrpgitems.api;

import cn.mythicland.dreamlib.material.EnchantmentEntry;
import cn.mythicland.dreamlib.material.MaterialEntry;

import java.util.List;

/**
 * Immutable data required by the browser editor.
 */
public record MythicItemEditorCatalog(
        List<MaterialEntry> materials,
        List<EnchantmentEntry> enchantments,
        List<String> itemFlags
) {
    public MythicItemEditorCatalog {
        materials = materials == null ? List.of() : List.copyOf(materials);
        enchantments = enchantments == null ? List.of() : List.copyOf(enchantments);
        itemFlags = itemFlags == null ? List.of() : List.copyOf(itemFlags);
    }
}
