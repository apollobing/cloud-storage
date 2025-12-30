package com.example.cloudstorage.service;

import com.example.cloudstorage.exception.InvalidPathException;
import org.springframework.stereotype.Service;

/**
 * Service responsible for path validation and manipulation.
 * Handles user path isolation and security validation.
 */
@Service
public class PathService {

    private static final String USER_FILES_PREFIX_TEMPLATE = "user-%d-files/";
    private static final String SLASH = "/";

    /**
     * Builds full MinIO path with user isolation prefix.
     * @param userId User ID for isolation
     * @param relativePath Relative path from user perspective (e.g., "folder/file.txt")
     * @return Full path in MinIO (e.g., "user-123-files/folder/file.txt")
     */
    public String buildUserPath(Long userId, String relativePath) {
        String prefix = String.format(USER_FILES_PREFIX_TEMPLATE, userId);
        
        if (relativePath == null || relativePath.isEmpty()) {
            return prefix;
        }
        
        String normalized = relativePath.startsWith(SLASH) 
                ? relativePath.substring(1) 
                : relativePath;
        
        return prefix + normalized;
    }

    /**
     * Validates path for security and format.
     * Checks for path traversal attacks and invalid characters.
     * @param path Path to validate
     * @throws InvalidPathException if path is invalid
     */
    public void validatePath(String path) {
        if (path == null) {
            return;
        }
        
        if (path.contains("../") || path.contains("..\\")) {
            throw new InvalidPathException("Path contains dangerous sequence '..'.");
        }
        
        if (!path.matches("^[a-zA-Z0-9._/\\-()\\[\\] ]*$")) {
            throw new InvalidPathException("Invalid characters in path: " + path);
        }
    }

    /**
     * Strips user prefix from full MinIO path to get relative path.
     * Inverse operation of buildUserPath.
     * @param fullPath Full path from MinIO (e.g., "user-123-files/folder/file.txt")
     * @param userId User ID
     * @return Relative path (e.g., "folder/file.txt")
     */
    public String stripUserPath(String fullPath, Long userId) {
        String prefix = String.format(USER_FILES_PREFIX_TEMPLATE, userId);
        return fullPath.startsWith(prefix)
                ? fullPath.substring(prefix.length())
                : fullPath;
    }

    /**
     * Validates that directory path ends with '/'.
     * @param path Path to validate
     * @throws InvalidPathException if path doesn't end with '/'
     */
    public void validateDirectoryPath(String path) {
        if (path.isEmpty()) {
            return;
        }
        if (!path.endsWith(SLASH)) {
            throw new InvalidPathException("Folder path must end with '/'");
        }
    }

}
