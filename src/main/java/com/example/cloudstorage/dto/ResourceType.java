package com.example.cloudstorage.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Type of resource", enumAsRef = true)
public enum ResourceType {
    @Schema(description = "Regular file")
    FILE,
    
    @Schema(description = "Directory (folder)")
    DIRECTORY
}
