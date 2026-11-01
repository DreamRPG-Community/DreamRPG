package cn.mythicland.dreamrpgitems.api;

import java.util.List;

/**
 * Immutable batch of local YAML files submitted for MM item import.
 */
public record MythicItemImportRequest(List<MythicItemImportFile> files, String contentPackage) {
    public MythicItemImportRequest(List<MythicItemImportFile> files) { this(files, "common"); }

    public MythicItemImportRequest {
        contentPackage = contentPackage == null || contentPackage.isBlank() ? "common" : contentPackage;
        files = files == null ? List.of() : List.copyOf(files);
    }
}
