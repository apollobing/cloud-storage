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
     * Searches for files and folders matching the query string.
     * Search is case-insensitive and recursive across all user resources.

     * Implementation uses a hybrid approach:
     * 1. Search files using recursive listing
     * 2. Extract folder paths from file paths and filter by query
     * 3. Search directory markers (objects ending with '/')
     * 4. Combine and deduplicate results

     * Note: This operation has O(n) complexity where n is the total number of user files.
     * For users with many files, consider implementing indexed search (e.g., Elasticsearch).
     * Results are limited to {@value MAX_SEARCH_RESULTS} items.
     *
     * @param user User performing the search
     * @param query Search query (matched against file and folder names, case-insensitive)
     * @return List of matching ResourceInfo objects (max {@value MAX_SEARCH_RESULTS} results)
     * @throws com.example.cloudstorage.exception.InvalidPathException if query contains invalid characters
     * @throws StorageException if MinIO operation fails
     */
    public List<ResourceInfo> searchUserFiles(CustomUserDetails user, String query) {
        pathService.validatePath(query);

        try {
            String userPrefix = pathService.buildUserPath(user.getId(), "");
            String lowerQuery = query.toLowerCase();

            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .prefix(userPrefix)
                            .recursive(true)
                            .build()
            );

            List<ResourceInfo> searchResults = new java.util.ArrayList<>();
            java.util.Set<String> foundPaths = new java.util.HashSet<>();

            for (Result<Item> result : results) {
                ResourceInfo info = extractItem(result, user.getId());

                if (info != null && info.getName().toLowerCase().contains(lowerQuery)) {
                    String fullPath = info.getPath() + info.getName();
                    if (!foundPaths.contains(fullPath)) {
                        searchResults.add(info);
                        foundPaths.add(fullPath);
                    }
                }

                if (info != null && !info.getPath().isEmpty()) {
                    extractMatchingFolders(info.getPath(), lowerQuery, foundPaths, searchResults);
                }

                if (searchResults.size() >= MAX_SEARCH_RESULTS) {
                    break;
                }
            }

            log.info("Search completed for user {}: query='{}', found {} results ({} files, {} folders)",
                    user.getId(), query, searchResults.size(),
                    searchResults.stream().filter(r -> r.getType().toString().equals("FILE")).count(),
                    searchResults.stream().filter(r -> r.getType().toString().equals("DIRECTORY")).count());

            return searchResults.stream().limit(MAX_SEARCH_RESULTS).toList();
        } catch (Exception e) {
            log.error("Failed to search files for user {}: query='{}'", user.getId(), query, e);
            throw new StorageException("Failed to search files: " + query, e);
        }
    }

    /**
     * Extracts matching folder names from a path and adds them to results.
     * For example, path "Documents/Reports/" will check "Documents" and "Reports".
     *
     * @param path File or folder path
     * @param lowerQuery Lowercase search query
     * @param foundPaths Set of already found paths (for deduplication)
     * @param searchResults List to add matching folders to
     */
    private void extractMatchingFolders(String path, String lowerQuery,
                                        java.util.Set<String> foundPaths,
                                        List<ResourceInfo> searchResults) {
        String[] parts = path.split(SLASH);
        StringBuilder currentPath = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            if (part.toLowerCase().contains(lowerQuery)) {
                String folderPath = currentPath.toString();
                String fullPath = folderPath + part + SLASH;

                if (!foundPaths.contains(fullPath)) {
                    ResourceInfo folderInfo = resourceInfoBuilder.build(
                            folderPath + part + SLASH, 0, true);
                    searchResults.add(folderInfo);
                    foundPaths.add(fullPath);
                }
            }

            currentPath.append(part).append(SLASH);
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
