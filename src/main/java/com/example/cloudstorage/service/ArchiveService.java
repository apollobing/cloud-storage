package com.example.cloudstorage.service;

import com.example.cloudstorage.config.MinioProperties;
import com.example.cloudstorage.exception.ResourceNotFoundException;
import com.example.cloudstorage.security.CustomUserDetails;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for creating ZIP archives from directories.
 * Streams ZIP content directly to the client without buffering in memory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final PathService pathService;

    /**
     * Creates a streaming ZIP archive of a directory.
     * The ZIP is streamed directly to the client without buffering in memory.
     *
     * @param user User requesting the archive
     * @param path Directory path (must end with '/')
     * @return StreamingResponseBody for HTTP response
     * @throws ResourceNotFoundException if directory doesn't exist or is empty
     */
    public StreamingResponseBody zipDirectoryStream(CustomUserDetails user, String path) {
        pathService.validatePath(path);
        pathService.validateDirectoryPath(path);
        
        if (!hasDirectoryContent(user, path)) {
            throw new ResourceNotFoundException("Directory not found or empty: " + path);
        }

        return outputStream -> {
            try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
                Iterable<Result<Item>> results = minioClient.listObjects(
                        ListObjectsArgs.builder()
                                .bucket(minioProperties.getBucketName())
                                .prefix(pathService.buildUserPath(user.getId(), path))
                                .recursive(true)
                                .build()
                );

                String rootFolderName = extractFolderName(path);

                for (Result<Item> res : results) {
                    Item item = res.get();

                    String relative = pathService.stripUserPath(item.objectName(), user.getId());

                    String entryName = rootFolderName + "/" + relative.substring(path.length());
                    if (entryName.startsWith("/")) {
                        entryName = entryName.substring(1);
                    }

                    if (item.isDir()) {
                        if (!entryName.endsWith("/")) {
                            entryName += "/";
                        }
                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.closeEntry();
                    } else {
                        zos.putNextEntry(new ZipEntry(entryName));
                        try (InputStream is = getObjectStream(user, relative)) {
                            is.transferTo(zos);
                        }
                        zos.closeEntry();
                    }
                }
            } catch (Exception e) {
                log.error("ZIP stream error during object reading or writing to client", e);
                throw new java.io.IOException("Error during ZIP streaming from MinIO.", e);
            }
        };
    }


    /**
     * Checks if directory has any content.
     */
    private boolean hasDirectoryContent(CustomUserDetails user, String path) {
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
            log.error("Error checking directory content for path: {}", path, e);
            return false;
        }
    }

    /**
     * Extracts folder name from path safely.
     * Handles edge cases like "/", empty paths, and trailing slashes.
     */
    private String extractFolderName(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return "archive";
        }
        
        String cleanPath = path.replaceAll("/$", "");
        
        if (cleanPath.isEmpty()) {
            return "archive";
        }
        
        try {
            Path pathObj = Path.of(cleanPath);
            Path fileName = pathObj.getFileName();
            return fileName != null ? fileName.toString() : "archive";
        } catch (Exception e) {
            log.warn("Could not extract folder name from path: {}, using default", path);
            return "archive";
        }
    }

    /**
     * Gets InputStream for a MinIO object.
     */
    private InputStream getObjectStream(CustomUserDetails user, String path) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioProperties.getBucketName())
                .object(pathService.buildUserPath(user.getId(), path))
                .build());
    }
}
