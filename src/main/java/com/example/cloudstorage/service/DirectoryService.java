package com.example.cloudstorage.service;

import com.example.cloudstorage.config.MinioProperties;
import com.example.cloudstorage.dto.ResourceInfo;
import com.example.cloudstorage.exception.ResourceAlreadyExistsException;
import com.example.cloudstorage.exception.ResourceNotFoundException;
import com.example.cloudstorage.exception.StorageException;
import com.example.cloudstorage.security.CustomUserDetails;
import io.minio.*;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Service for directory operations (create, list, delete).
 * Provides efficient batch operations and proper error handling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectoryService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final PathService pathService;
    private final ResourceInfoBuilder resourceInfoBuilder;

    private static final String SLASH = "/";

    /**
     * Creates a new directory and returns its info.
     *
     * @param user User creating the directory
     * @param path Directory path (must end with '/')
     * @return ResourceInfo for the created directory
     * @throws ResourceAlreadyExistsException if directory already exists
     * @throws StorageException if MinIO operation fails
     */
    public ResourceInfo createDirectory(CustomUserDetails user, String path) {
        pathService.validatePath(path);
        pathService.validateDirectoryPath(path);

        if (directoryExists(user, path)) {
            throw new ResourceAlreadyExistsException("Directory already exists: " + path);
        }

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(pathService.buildUserPath(user.getId(), path))
                    .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                    .build());

            return resourceInfoBuilder.build(path, 0, true);
        } catch (Exception e) {
            log.error("Failed to create directory: {}", path, e);
            throw new StorageException("Failed to create directory: " + path, e);
        }
    }

    /**
     * Lists contents of a directory (direct children only, not recursive).
     *
     * @param user User requesting the listing
     * @param path Directory path (must end with '/')
     * @return List of ResourceInfo for direct children
     * @throws ResourceNotFoundException if directory doesn't exist
     * @throws StorageException if MinIO operation fails
     */
    public List<ResourceInfo> listDirectory(CustomUserDetails user, String path) {
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        
        pathService.validatePath(path);
        pathService.validateDirectoryPath(path);

        if (!directoryExists(user, path)) {
            throw new ResourceNotFoundException("Directory not found: " + path);
        }

        try {
            String prefix = pathService.buildUserPath(user.getId(), path);

            Iterable<io.minio.Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .prefix(prefix)
                            .delimiter(SLASH)
                            .build()
            );

            List<ResourceInfo> resources = new ArrayList<>();
            for (io.minio.Result<Item> result : results) {
                Item item = result.get();
                String fullObjectName = item.objectName();

                if (fullObjectName.equals(prefix)) {
                    continue;
                }

                String relativePath = pathService.stripUserPath(fullObjectName, user.getId());
                if (relativePath.isEmpty()) {
                    continue;
                }

                boolean isDir = item.isDir() || fullObjectName.endsWith(SLASH);
                ResourceInfo info = resourceInfoBuilder.build(relativePath, item.size(), isDir);
                resources.add(info);
            }

            return resources;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to list directory: {}", path, e);
            throw new StorageException("Failed to list directory: " + path, e);
        }
    }

    /**
     * Deletes a directory and all its contents recursively.
     * Uses batch API for efficient deletion of multiple objects.
     *
     * @param user User deleting the directory
     * @param path Directory path (must end with '/')
     * @throws ResourceNotFoundException if directory doesn't exist
     * @throws StorageException if MinIO operation fails
     */
    public void deleteDirectory(CustomUserDetails user, String path) {
        pathService.validatePath(path);
        pathService.validateDirectoryPath(path);

        if ("/".equals(path)) {
            throw new IllegalArgumentException("Cannot delete root directory");
        }

        if (!directoryExists(user, path)) {
            throw new ResourceNotFoundException("Directory not found: " + path);
        }

        try {
            String prefix = pathService.buildUserPath(user.getId(), path);

            Iterable<io.minio.Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .prefix(prefix)
                            .recursive(true)
                            .build()
            );

            List<DeleteObject> objectsToDelete = StreamSupport.stream(objects.spliterator(), false)
                    .map(result -> {
                        try {
                            return new DeleteObject(result.get().objectName());
                        } catch (Exception e) {
                            log.error("Failed to get object name from MinIO result", e);
                            throw new StorageException("Failed to list directory contents for deletion", e);
                        }
                    })
                    .collect(Collectors.toList());

            if (objectsToDelete.isEmpty()) {
                log.warn("No objects found to delete for directory: {}", path);
                return;
            }

            Iterable<io.minio.Result<io.minio.messages.DeleteError>> results =
                    minioClient.removeObjects(RemoveObjectsArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .objects(objectsToDelete)
                            .build());

            for (io.minio.Result<io.minio.messages.DeleteError> result : results) {
                io.minio.messages.DeleteError error = result.get();
                log.error("Failed to delete object: {} - {}", error.objectName(), error.message());
            }

            log.info("Successfully deleted directory: {} ({} objects)", path, objectsToDelete.size());
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete directory: {}", path, e);
            throw new StorageException("Failed to delete directory: " + path, e);
        }
    }

    /**
     * Checks if directory exists.
     *
     * @param user User to check directory for
     * @param path Directory path
     * @return true if directory exists, false otherwise
     */
    public boolean directoryExists(CustomUserDetails user, String path) {
        if ("/".equals(path)) {
            return true;
        }
        
        try {
            String prefix = pathService.buildUserPath(user.getId(), path);
            return minioClient.listObjects(ListObjectsArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .prefix(prefix)
                            .maxKeys(1)
                            .build())
                    .iterator().hasNext();
        } catch (Exception e) {
            log.error("Error checking directory existence for path: {}", path, e);
            return false;
        }
    }
}
