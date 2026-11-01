package cn.mythicland.dreamrpgitems.api;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Public MythicMobs item API exposed by DreamRPG item service.
 */
public interface DreamRPGItemApi {

    MythicItemPage search(MythicItemQuery query);

    Optional<MythicItemDetails> find(String internalName);

    ItemStack getItemStack(String internalName, int amount);

    /**
     * Materializes an MM item using the amount configured by its source definition.
     *
     * @param internalName MM item internal name
     * @return a detached, identity-marked item stack
     * @throws IllegalStateException if called off the Bukkit primary thread or the identity bridge
     *                               is unavailable
     * @throws MythicItemException   if the item does not exist or cannot be materialized
     */
    ItemStack materialize(String internalName);

    /**
     * Reads the hidden DreamRPG item service identity from an item stack.
     *
     * @param item item stack
     * @return identity when the item was materialized by this addon
     * @throws IllegalStateException if called off the Bukkit primary thread or the identity bridge
     *                               is unavailable
     */
    Optional<MythicItemIdentity> identify(ItemStack item);

    /**
     * Refreshes one marked item against the current MM catalog.
     *
     * @param item item stack to inspect
     * @param mode amount policy for the replacement
     * @return detached refresh result
     * @throws NullPointerException  if mode is null
     * @throws IllegalStateException if called off the Bukkit primary thread or the identity bridge
     *                               is unavailable
     */
    MythicItemRefreshResult refresh(ItemStack item, MythicItemRefreshMode mode);

    MythicItemWriteResult create(MythicItemCreateRequest request);

    MythicItemWriteResult update(MythicItemUpdateRequest request);

    MythicItemWriteResult delete(MythicItemDeleteRequest request);

    MythicItemsReloadResult reload();

    MythicItemImportResult previewImport(MythicItemImportRequest request);

    MythicItemImportResult importItems(MythicItemImportRequest request);


}
