package com.example.cloudstorage.service;

import com.example.cloudstorage.config.MinioProperties;
import com.example.cloudstorage.dto.ResourceInfo;
import com.example.cloudstorage.exception.StorageException;
import com.example.cloudstorage.security.CustomUserDetails;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Service for searching user files.
 * Provides recursive search across all user files with case-insensitive matching.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final PathService pathService;
    private final ResourceInfoBuilder resourceInfoBuilder;

    private static final String SLASH = "/";
    private static final int MAX_SEARCH_RESULTS = 100;

    /**
     * Searches for files matching the query string in file names.
     * Search is case-insensitive and recursive across all user files.
     * Note: This operation has O(n) complexity where n is the total number of user files.
     * For users with many files, consider implementing indexed search (e.g., Elasticsearch).
     * Results are limited to {@value MAX_SEARCH_RESULTS} items.
     *
     * @param user User performing the search
     * @param query Search query (matched against file names, case-insensitive)
     * @return List of matching ResourceInfo objects (max {@value MAX_SEARCH_RESULTS} results)
     * @throws com.example.cloudstorage.exception.InvalidPathException if query contains invalid characters
     * @throws StorageException if MinIO operation fails
     */
    public List<ResourceInfo> searchUserFiles(CustomUserDetails user, String query) {
        pathService.validatePath(query);

        try {
            String userPrefix = pathService.buildUserPath(user.getId(), "");

            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .prefix(userPrefix)
                            .recursive(true)
                            .build()
            );

            String lowerQuery = query.toLowerCase();
            
            List<ResourceInfo> searchResults = StreamSupport.stream(results.spliterator(), false)
                    .map(result -> extractItem(result, user.getId()))
                    .filter(info -> info != null && 
                            info.getName().toLowerCase().contains(lowerQuery))
                    .limit(MAX_SEARCH_RESULTS)
                    .toList();

            log.info("Search completed for user {}: query='{}', found {} results", 
                    user.getId(), query, searchResults.size());

            return searchResults;
        } catch (Exception e) {
            log.error("Failed to search files for user {}: query='{}'", user.getId(), query, e);
            throw new StorageException("Failed to search files: " + query, e);
        }
    }

    /**
     * Extracts Item from Result and maps it to ResourceInfo.
     * Returns null for invalid or empty paths.
     *
     * @param result MinIO result containing Item
     * @param userId User ID for path stripping
     * @return ResourceInfo or null if path is empty
     */
    private ResourceInfo extractItem(Result<Item> result, Long userId) {
        try {
            Item item = result.get();
            String relative = pathService.stripUserPath(item.objectName(), userId);
            
            if (relative.isEmpty()) {
                return null;
            }

            boolean isDir = item.isDir() || item.objectName().endsWith(SLASH);
            return resourceInfoBuilder.build(relative, item.size(), isDir);
        } catch (Exception e) {
            log.error("Failed to extract item from search result", e);
            return null;
        }
    }
}
