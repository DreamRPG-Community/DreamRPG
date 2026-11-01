package cn.mythicland.dreamrpgitems.api;

import cn.mythicland.framework.validation.TextValidation;

/**
 * Hidden identity attached to an item materialized by DreamRPG item service.
 *
 * @param internalName MM item internal name
 * @param revision     revision of the source configuration
 */
public record MythicItemIdentity(String internalName, String revision) {

    public MythicItemIdentity {
        internalName = TextValidation.requireText(internalName, "internalName");
        revision = TextValidation.requireText(revision, "revision");
    }

}
