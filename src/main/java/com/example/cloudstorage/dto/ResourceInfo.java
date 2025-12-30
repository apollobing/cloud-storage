package com.example.cloudstorage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Schema(description = "Information about a file or folder resource")
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"path", "name", "size", "type"})
public class ResourceInfo {
    
    @Schema(description = "Path to the parent folder containing this resource", example = "folder1/folder2/")
    private String path;
    
    @Schema(description = "Name of the resource (file or folder)", example = "file.txt")
    private String name;
    
    @Schema(description = "Size of the file in bytes. Null for directories.", example = "1024")
    private Long size;
    
    @Schema(description = "Type of the resource", example = "FILE")
    private ResourceType type;
}
