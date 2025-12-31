package com.example.cloudstorage.service;

import com.example.cloudstorage.config.MinioProperties;
import com.example.cloudstorage.dto.ResourceInfo;
import com.example.cloudstorage.exception.InvalidPathException;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for moving and renaming resources.
 * Provides copy-then-delete operations for files and directories.
 * Note: Operations are not atomic - partial moves may occur on failure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceMoveService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final PathService pathService;
    private final FileOperationsService fileOperationsService;

    private static final String SLASH = "/";

    /**
     * Moves or renames a resource (file or directory).
     * Warning: This operation is NOT atomic. If the operation fails after copying
     * but before deletion, the resource will be duplicated. Consider this when
     * moving large directories or in unstable network conditions.
     *
     * @param user User performing the operation
     * @param fromPath Source path (file or directory)
     * @param toPath Destination path (file or directory)
     * @return ResourceInfo for the moved/renamed resource
     * @throws InvalidPathException if paths are invalid or types don't match
     * @throws ResourceNotFoundException if source doesn't exist
     * @throws ResourceAlreadyExistsException if destination already exists
     * @throws StorageException if MinIO operation fails
     */
    public ResourceInfo moveOrRenameResource(CustomUserDetails user, String fromPath, String toPath) {
        pathService.validatePath(fromPath);
        pathService.validatePath(toPath);

        boolean isSourceDir = fromPath.endsWith(SLASH);
        boolean isTargetDir = toPath.endsWith(SLASH);

        if (isSourceDir != isTargetDir) {
            throw new InvalidPathException(
                    "Resource type must match (file -> file, folder/ -> folder/)."
            );
        }

        if (!fileOperationsService.resourceExists(user, fromPath)) {
            throw new ResourceNotFoundException("Source resource not found: " + fromPath);
        }

        if (fileOperationsService.resourceExists(user, toPath)) {
            throw new ResourceAlreadyExistsException("Target resource already exists: " + toPath);
        }

        log.info("Moving resource: {} -> {} for user {}", fromPath, toPath, user.getId());

        try {
            if (isSourceDir) {
                moveDirectory(user, fromPath, toPath);
            } else {
                moveFile(user, fromPath, toPath);
            }

            log.info("Successfully moved resource: {} -> {}", fromPath, toPath);
            return fileOperationsService.getResourceInfo(user, toPath);
        } catch (ResourceNotFoundException | ResourceAlreadyExistsException | InvalidPathException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to move resource: {} -> {}", fromPath, toPath, e);
            throw new StorageException("Failed to move resource from " + fromPath + " to " + toPath, e);
        }
    }

    /**
     * Moves a single file (copy then delete).
     * Not atomic - if deletion fails, file will be duplicated.
     */
    private void moveFile(CustomUserDetails user, String fromPath, String toPath) {
        try {
            String srcFullPath = pathService.buildUserPath(user.getId(), fromPath);
            String destFullPath = pathService.buildUserPath(user.getId(), toPath);

            copyObject(srcFullPath, destFullPath);

            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(srcFullPath)
                    .build());
        } catch (Exception e) {
            log.error("Failed to move file: {} -> {}", fromPath, toPath, e);
            throw new StorageException("Failed to move file", e);
        }
    }

    /**
     * Moves a directory recursively (copy all, then delete all).
     * Not atomic - if any step fails, partial move will occur.
     * Uses batch API for efficient deletion.
     */
    private void moveDirectory(CustomUserDetails user, String fromPath, String toPath) {
        try {
            String srcPrefix = pathService.buildUserPath(user.getId(), fromPath);
            String destPrefix = pathService.buildUserPath(user.getId(), toPath);

            Iterable<io.minio.Result<Item>> objectsIterable = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .prefix(srcPrefix)
                            .recursive(true)
                            .build()
            );

            List<Item> objects = new ArrayList<>();
            for (io.minio.Result<Item> result : objectsIterable) {
                objects.add(result.get());
            }

            if (objects.isEmpty()) {
                log.warn("No objects found in source directory: {}", fromPath);
                return;
            }

            log.info("Moving directory with {} objects", objects.size());

            for (Item item : objects) {
                String srcObjectName = item.objectName();
                String destObjectName = srcObjectName.replace(srcPrefix, destPrefix);
                copyObject(srcObjectName, destObjectName);
            }

            List<DeleteObject> objectsToDelete = objects.stream()
                    .map(item -> new DeleteObject(item.objectName()))
                    .collect(Collectors.toList());

            Iterable<io.minio.Result<io.minio.messages.DeleteError>> results = 
                    minioClient.removeObjects(RemoveObjectsArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .objects(objectsToDelete)
                            .build());

            for (io.minio.Result<io.minio.messages.DeleteError> result : results) {
                io.minio.messages.DeleteError error = result.get();
                log.error("Failed to delete object during move: {} - {}", error.objectName(), error.message());
            }
        } catch (Exception e) {
            log.error("Failed to move directory: {} -> {}", fromPath, toPath, e);
            throw new StorageException("Failed to move directory", e);
        }
    }

    /**
     * Copies an object in MinIO from source to destination.
     */
    private void copyObject(String source, String dest) {
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(dest)
                    .source(CopySource.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(source)
                            .build())
                    .build());
        } catch (Exception e) {
            log.error("Failed to copy object: {} -> {}", source, dest, e);
            throw new StorageException("Failed to copy object in MinIO", e);
        }
    }
}
