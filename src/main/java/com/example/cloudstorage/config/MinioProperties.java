package com.example.cloudstorage.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@ToString(exclude = {"accessKey", "secretKey"})
@Validated
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {
    
    @NotBlank(message = "MinIO URL is required (minio.url)")
    @Pattern(
        regexp = "^https?://.*",
        message = "MinIO URL must start with http:// or https://"
    )
    private String url;
    
    @NotBlank(message = "MinIO access key is required (minio.accessKey)")
    private String accessKey;
    
    @NotBlank(message = "MinIO secret key is required (minio.secretKey)")
    private String secretKey;
    
    @NotBlank(message = "MinIO bucket name is required (minio.bucketName)")
    @Pattern(
        regexp = "^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$",
        message = "Bucket name must be 3-63 characters, lowercase letters, numbers, and hyphens only"
    )
    private String bucketName;
}
