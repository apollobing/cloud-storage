package com.example.cloudstorage.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    private final MinioProperties minioProperties;

    @Bean
    public MinioClient minioClient() {
        try {
            log.info("Initializing MinIO client: url={}, bucket={}", 
                     minioProperties.getUrl(), 
                     minioProperties.getBucketName());
            
            MinioClient client = MinioClient.builder()
                    .endpoint(minioProperties.getUrl())
                    .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                    .build();

            log.info("Testing MinIO connection...");
            client.listBuckets();
            log.info("MinIO connection successful");

            ensureBucketExists(client);

            log.info("MinIO client initialized successfully");
            return client;
            
        } catch (Exception e) {
            log.error("Failed to initialize MinIO client. " +
                      "Please check MinIO connection settings: " +
                      "url={}, bucket={}", 
                      minioProperties.getUrl(), 
                      minioProperties.getBucketName(), e);
            throw new IllegalStateException("MinIO initialization failed", e);
        }
    }

    private void ensureBucketExists(MinioClient client) throws Exception {
        String bucketName = minioProperties.getBucketName();
        
        boolean bucketExists = client.bucketExists(
            BucketExistsArgs.builder()
                .bucket(bucketName)
                .build()
        );
        
        if (bucketExists) {
            log.info("Bucket '{}' already exists", bucketName);
        } else {
            log.info("Bucket '{}' does not exist, creating...", bucketName);
            client.makeBucket(
                MakeBucketArgs.builder()
                    .bucket(bucketName)
                    .build()
            );
            log.info("Bucket '{}' created successfully", bucketName);
        }
    }
}
