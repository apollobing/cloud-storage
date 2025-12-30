package com.example.cloudstorage.service;

import com.example.cloudstorage.dto.ResourceInfo;
import com.example.cloudstorage.dto.ResourceType;
import org.springframework.stereotype.Component;

/**
 * Utility component for building ResourceInfo objects from paths.
 * Centralizes the logic for parsing paths and handling directory naming conventions.
 */
@Component
public class ResourceInfoBuilder {

    private static final String SLASH = "/";

    /**
     * Builds ResourceInfo from a relative path.
     * Handles directory naming (adds trailing slash) and path parsing.
     *
     * @param relativePath Path relative to user's root (e.g., "folder/file.txt")
     * @param size Size in bytes (ignored for directories)
     * @param isDir Whether the resource is a directory
     * @return ResourceInfo object with parsed name and path
     */
    public ResourceInfo build(String relativePath, long size, boolean isDir) {
        if (relativePath.startsWith(SLASH)) {
            relativePath = relativePath.substring(1);
        }

        String name;
        String parentPath;

        if (relativePath.isEmpty()) {
            name = "";
            parentPath = "";
        } else {
            String pathForParsing = relativePath;
            if (isDir && pathForParsing.endsWith(SLASH)) {
                pathForParsing = pathForParsing.substring(0, pathForParsing.length() - 1);
            }

            int lastSlashIndex = pathForParsing.lastIndexOf(SLASH);
            if (lastSlashIndex == -1) {
                name = pathForParsing;
                parentPath = "";
            } else {
                name = pathForParsing.substring(lastSlashIndex + 1);
                parentPath = pathForParsing.substring(0, lastSlashIndex + 1);
            }

            if (isDir && !name.isEmpty() && !name.endsWith(SLASH)) {
                name = name + SLASH;
            }
        }

        return ResourceInfo.builder()
                .name(name)
                .path(parentPath)
                .size(isDir ? null : size)
                .type(isDir ? ResourceType.DIRECTORY : ResourceType.FILE)
                .build();
    }
}
