package com.example.cloudstorage.service;

import com.example.cloudstorage.config.MinioProperties;
import com.example.cloudstorage.dto.ResourceInfo;
import com.example.cloudstorage.exception.InvalidPathException;
import com.example.cloudstorage.exception.ResourceAlreadyExistsException;
import com.example.cloudstorage.exception.ResourceNotFoundException;
import com.example.cloudstorage.exception.StorageException;
import com.example.cloudstorage.security.CustomUserDetails;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for file operations (upload, download, info, delete).
 * Provides validation, error handling, and integration with MinIO storage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileOperationsService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final PathService pathService;
    private final ResourceInfoBuilder resourceInfoBuilder;

    private static final String SLASH = "/";

    /**
     * Uploads multiple files to the specified directory path.
     * Note: If upload fails for any file, previously uploaded files will remain.
     *
     * @param user User uploading files
     * @param path Directory path (must end with '/')
     * @param files List of files to upload
     * @return List of ResourceInfo for successfully uploaded files
     * @throws InvalidPathException if path is invalid
     * @throws ResourceAlreadyExistsException if any file already exists
     * @throws StorageException if MinIO operation fails
     */
    public List<ResourceInfo> uploadFiles(CustomUserDetails user, String path, List<MultipartFile> files) {
        pathService.validatePath(path);
        pathService.validateDirectoryPath(path);

        List<ResourceInfo> uploaded = new ArrayList<>();
        for (MultipartFile file : files) {
            uploaded.add(uploadSingleFile(user, path, file));
        }
        return uploaded;
    }

    /**
     * Downloads a file as InputStream.
     * The caller is responsible for closing the returned InputStream.
     *
     * @param user User downloading the file
     * @param path File path (must not end with '/')
     * @return InputStream of file contents (must be closed by caller)
     * @throws InvalidPathException if path is a directory
     * @throws ResourceNotFoundException if file doesn't exist
     * @throws StorageException if MinIO operation fails
     */
    public InputStream downloadFile(CustomUserDetails user, String path) {
        pathService.validatePath(path);
        
        if (path.endsWith(SLASH)) {
            throw new InvalidPathException("Cannot download directory as file. Use directory download endpoint.");
        }

        if (!resourceExists(user, path)) {
            throw new ResourceNotFoundException("File not found: " + path);
        }

        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(pathService.buildUserPath(user.getId(), path))
                    .build());
        } catch (Exception e) {
            log.error("Failed to download file: {}", path, e);
            throw new StorageException("Failed to download file: " + path, e);
        }
    }

    /**
     * Gets information about a file or directory.
     *
     * @param user User requesting info
     * @param path Resource path (file or directory)
     * @return ResourceInfo with name, path, size, type
     * @throws ResourceNotFoundException if resource doesn't exist
     * @throws StorageException if MinIO operation fails
     */
    public ResourceInfo getResourceInfo(CustomUserDetails user, String path) {
        pathService.validatePath(path);

        try {
            String fullPath = pathService.buildUserPath(user.getId(), path);

            if (path.endsWith(SLASH)) {
                if (!"/".equals(path)) {
                    boolean exists = minioClient.listObjects(ListObjectsArgs.builder()
                                    .bucket(minioProperties.getBucketName())
                                    .prefix(fullPath)
                                    .maxKeys(1)
                                    .build())
                            .iterator().hasNext();

                    if (!exists) {
                        throw new ResourceNotFoundException("Directory not found: " + path);
                    }
                }

                return resourceInfoBuilder.build(path, 0, true);
            } else {
                StatObjectResponse stat = getStatObject(user, path);
                return resourceInfoBuilder.build(path, stat.size(), false);
            }
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get resource info: {}", path, e);
            throw new StorageException("Failed to get resource info: " + path, e);
        }
    }

    /**
     * Deletes a file (not directory).
     *
     * @param user User deleting the file
     * @param path File path (must not end with '/')
     * @throws InvalidPathException if path is a directory
     * @throws ResourceNotFoundException if file doesn't exist
     * @throws StorageException if MinIO operation fails
     */
    public void deleteFile(CustomUserDetails user, String path) {
        pathService.validatePath(path);

        if (path.endsWith(SLASH)) {
            throw new InvalidPathException("Use directory service to delete directories");
        }

        if (!resourceExists(user, path)) {
            throw new ResourceNotFoundException("File not found: " + path);
        }

        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(pathService.buildUserPath(user.getId(), path))
                    .build());
        } catch (Exception e) {
            log.error("Failed to delete file: {}", path, e);
            throw new StorageException("Failed to delete file: " + path, e);
        }
    }

    /**
     * Checks if a resource (file or directory) exists.
     *
     * @param user User to check resource for
     * @param path Resource path
     * @return true if resource exists, false otherwise
     */
    public boolean resourceExists(CustomUserDetails user, String path) {
        try {
            if (path.endsWith(SLASH)) {
                return minioClient.listObjects(ListObjectsArgs.builder()
                                .bucket(minioProperties.getBucketName())
                                .prefix(pathService.buildUserPath(user.getId(), path))
                                .maxKeys(1)
                                .build())
                        .iterator().hasNext();
            }
            getStatObject(user, path);
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking resource existence for path: {}", path, e);
            return false;
        }
    }

    /**
     * Uploads a single file to MinIO.
     *
     * @param user User uploading the file
     * @param path Directory path where file will be uploaded
     * @param file MultipartFile to upload
     * @return ResourceInfo for uploaded file
     * @throws InvalidPathException if filename is empty or invalid
     * @throws ResourceAlreadyExistsException if file already exists
     * @throws StorageException if upload fails
     */
    private ResourceInfo uploadSingleFile(CustomUserDetails user, String path, MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            throw new InvalidPathException("File name is empty");
        }

        String fullPath = path + filename;
        pathService.validatePath(fullPath);

        if (resourceExists(user, fullPath)) {
            throw new ResourceAlreadyExistsException("File already exists: " + fullPath);
        }

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(pathService.buildUserPath(user.getId(), fullPath))
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            return getResourceInfo(user, fullPath);
        } catch (ResourceAlreadyExistsException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload file: {}", fullPath, e);
            throw new StorageException("Failed to upload file: " + fullPath, e);
        }
    }

    /**
     * Gets MinIO object metadata.
     *
     * @param user User requesting metadata
     * @param path File path
     * @return StatObjectResponse with metadata
     * @throws ResourceNotFoundException if file doesn't exist
     * @throws StorageException if operation fails
     */
    private StatObjectResponse getStatObject(CustomUserDetails user, String path) {
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(pathService.buildUserPath(user.getId(), path))
                    .build());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new ResourceNotFoundException("Resource not found: " + path);
            }
            log.error("MinIO error for path: {}", path, e);
            throw new StorageException("Failed to get object metadata: " + path, e);
        } catch (Exception e) {
            log.error("Failed to get object metadata: {}", path, e);
            throw new StorageException("Failed to get object metadata: " + path, e);
        }
    }
}
