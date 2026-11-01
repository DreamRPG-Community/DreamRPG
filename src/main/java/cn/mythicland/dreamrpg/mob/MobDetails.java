package cn.mythicland.dreamrpg.mob;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Configured attributes and declared equipment; inspection never rolls drops or executes skills. */
public record MobDetails(MobSummary summary, List<Attribute> attributes, List<Equipment> equipment) {
    public MobDetails {
        attributes = List.copyOf(attributes);
        equipment = List.copyOf(equipment);
    }

    public record Attribute(String name, String value) { }

    public record Equipment(String slot, String declaration, ItemStack preview) {
        public Equipment { preview = preview.clone(); }
        @Override public ItemStack preview() { return preview.clone(); }
    }
}
