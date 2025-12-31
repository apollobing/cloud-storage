package com.example.cloudstorage.service;

import com.example.cloudstorage.dto.ResourceInfo;
import com.example.cloudstorage.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Facade service for cloud storage operations.
 * Delegates all operations to specialized services.
 * This keeps the API stable while allowing internal refactoring.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final FileOperationsService fileOperationsService;
    private final DirectoryService directoryService;
    private final ResourceMoveService resourceMoveService;
    private final SearchService searchService;
    private final ArchiveService archiveService;

    /**
     * Uploads multiple files to the specified directory path.
     */
    public List<ResourceInfo> upload(CustomUserDetails user, String path, List<MultipartFile> files) {
        return fileOperationsService.uploadFiles(user, path, files);
    }

    /**
     * Downloads a resource: file as InputStream or directory as ZIP StreamingResponseBody.
     */
    public Object downloadResource(CustomUserDetails user, String path) {
        if (path.endsWith("/")) {
            return archiveService.zipDirectoryStream(user, path);
        } else {
            return fileOperationsService.downloadFile(user, path);
        }
    }

    /**
     * Gets information about a resource (file or directory).
     */
    public ResourceInfo getResourceInfo(CustomUserDetails user, String path) {
        return fileOperationsService.getResourceInfo(user, path);
    }

    /**
     * Creates a new directory.
     */
    public ResourceInfo createDirectory(CustomUserDetails user, String path) {
        return directoryService.createDirectory(user, path);
    }

    /**
     * Lists contents of a directory (direct children only).
     */
    public List<ResourceInfo> listDirectory(CustomUserDetails user, String path) {
        return directoryService.listDirectory(user, path);
    }

    /**
     * Deletes a resource (file or directory).
     */
    public void deleteResource(CustomUserDetails user, String path) {
        if (path.endsWith("/")) {
            directoryService.deleteDirectory(user, path);
        } else {
            fileOperationsService.deleteFile(user, path);
        }
    }

    /**
     * Moves or renames a resource.
     */
    public ResourceInfo moveOrRenameResource(CustomUserDetails user, String fromPath, String toPath) {
        return resourceMoveService.moveOrRenameResource(user, fromPath, toPath);
    }

    /**
     * Searches for files matching the query.
     */
    public List<ResourceInfo> searchUserFiles(CustomUserDetails user, String query) {
        return searchService.searchUserFiles(user, query);
    }
}
