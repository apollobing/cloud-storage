package com.example.cloudstorage.controller;

import com.example.cloudstorage.dto.ResourceInfo;
import com.example.cloudstorage.exception.InvalidPathException;
import com.example.cloudstorage.security.CustomUserDetails;
import com.example.cloudstorage.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

@Tag(name = "Resource Management", description = "Operations related to files and folders")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class ResourceController {

    private final StorageService storageService;

    @Operation(
            summary = "Upload files",
            description = "Uploads one or more files to the specified path. " +
                    "To test 'Malformed multipart request' error, use Postman (raw mode) or curl " +
                    "with invalid Content-Type/body - Swagger UI cannot send malformed requests.",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Files successfully uploaded",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "Single File Upload",
                                                    value = "[{\"path\": \"folder1/\", \"name\": \"file.txt\", " +
                                                            "\"size\": 1024, \"type\": \"FILE\"}]"
                                            ),
                                            @ExampleObject(
                                                    name = "Multiple Files Upload",
                                                    value = "[{\"path\": \"folder1/\", \"name\": \"file1.txt\", " +
                                                            "\"size\": 1024, \"type\": \"FILE\"}, " +
                                                            "{\"path\": \"folder1/\", \"name\": \"file2.pdf\", " +
                                                            "\"size\": 2048, \"type\": \"FILE\"}]"
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid request body or validation error",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "No Files Selected",
                                                    value = "{\"message\": " +
                                                            "\"At least one file must be selected for upload\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Empty Files",
                                                    value = "{\"message\": \"Empty files cannot be uploaded\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Invalid Path",
                                                    value = "{\"message\": " +
                                                            "\"Invalid characters in path: folder<name>\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Malformed Multipart Request",
                                                    value = "{\"message\": \"Malformed multipart request. " +
                                                            "Please ensure Content-Type is set to multipart/form-data.\"}"
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Unauthorized: User is not authenticated",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Unauthorized Response Example",
                                            value = "{\"message\": \"Unauthorized\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Conflict: File already exists",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Conflict Response Example",
                                            value = "{\"message\": \"File already exists\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Unknown server error",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Internal Server Error Example",
                                            value = "{\"message\": \"An internal server error occurred.\"}"
                                    )
                            )
                    )
            }
    )
    @PostMapping(value = "/resource", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ResourceInfo>> uploadResource(
            @RequestParam("path") String path,
            @RequestPart("object") 
            @NotEmpty(message = "At least one file must be selected for upload") 
            List<MultipartFile> files,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (files.stream().allMatch(MultipartFile::isEmpty)) {
            boolean noFilesSelected = files.stream()
                    .allMatch(f -> f.getOriginalFilename() == null || f.getOriginalFilename().isEmpty());
            
            String message = noFilesSelected 
                    ? "At least one file must be selected for upload"
                    : "Empty files cannot be uploaded";
            
            throw new InvalidPathException(message);
        }

        List<ResourceInfo> uploaded = storageService.upload(userDetails, path, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
    }

    @Operation(
            summary = "Get resource information",
            description = "Returns information about a file or folder at the specified path. " +
                    "Note: To test the 'empty path' error in Swagger UI, use a space character " +
                    "or test via external client (curl/Postman).",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successfully retrieved resource information",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "File Example",
                                                    value = "{\"path\": \"folder1/folder2/\", \"name\": \"file.txt\", " +
                                                            "\"size\": 1024, \"type\": \"FILE\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Directory Example",
                                                    value = "{\"path\": \"folder1/\", \"name\": \"folder2\", " +
                                                            "\"type\": \"DIRECTORY\"}"
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid or missing path",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "Empty Path Parameter",
                                                    value = "{\"message\": \"The 'path' parameter cannot be empty\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Invalid Characters",
                                                    value = "{\"message\": \"Invalid characters in path: folder<name>\"}"
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Unauthorized: User is not authenticated",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Unauthorized Response Example",
                                            value = "{\"message\": \"Unauthorized\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Resource not found",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Not Found Response Example",
                                            value = "{\"message\": \"Resource not found: folder1/file.txt\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Unknown server error",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Internal Server Error Example",
                                            value = "{\"message\": \"An internal server error occurred.\"}"
                                    )
                            )
                    )
            }
    )
    @GetMapping("/resource")
    public ResponseEntity<ResourceInfo> getResourceInfo(
            @RequestParam @NotBlank(message = "The 'path' parameter cannot be empty") String path,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ResourceInfo info = storageService.getResourceInfo(userDetails, path);
        return ResponseEntity.ok(info);
    }

    @Operation(
            summary = "Delete resource",
            description = "Deletes a file or folder at the specified path. " +
                    "Note: To test the 'empty path' error in Swagger UI, use a space character " +
                    "or test via external client (curl/Postman).",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "Resource successfully deleted"
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid or missing path",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "Empty Path Parameter",
                                                    value = "{\"message\": \"The 'path' parameter cannot be empty\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Invalid Characters",
                                                    value = "{\"message\": \"Invalid characters in path: folder<name>\"}"
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Unauthorized: User is not authenticated",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Unauthorized Response Example",
                                            value = "{\"message\": \"Unauthorized\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Resource not found",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Not Found Response Example",
                                            value = "{\"message\": \"Resource not found: folder1/file.txt\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Unknown server error",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Internal Server Error Example",
                                            value = "{\"message\": \"An internal server error occurred.\"}"
                                    )
                            )
                    )
            }
    )
    @DeleteMapping("/resource")
    public ResponseEntity<Void> deleteResource(
            @RequestParam @NotBlank(message = "The 'path' parameter cannot be empty") String path,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        storageService.deleteResource(userDetails, path);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Download resource",
            description = "Downloads a file or folder (as ZIP archive) from the specified path. " +
                    "Note: To test the 'empty path' error in Swagger UI, use a space character " +
                    "or test via external client (curl/Postman).",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successfully downloaded resource",
                            content = @Content(
                                    mediaType = "application/octet-stream",
                                    schema = @Schema(implementation = StreamingResponseBody.class),
                                    examples = @ExampleObject(
                                            name = "Successful Response Example",
                                            value = "Binary file content"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid or missing path",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "Empty Path Parameter",
                                                    value = "{\"message\": \"The 'path' parameter cannot be empty\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Invalid Characters",
                                                    value = "{\"message\": \"Invalid characters in path: folder<name>\"}"
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Unauthorized: User is not authenticated",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Unauthorized Response Example",
                                            value = "{\"message\": \"Unauthorized\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Resource not found",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Not Found Response Example",
                                            value = "{\"message\": \"Resource not found: folder1/file.txt\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Unknown server error",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Internal Server Error Example",
                                            value = "{\"message\": \"An internal server error occurred.\"}"
                                    )
                            )
                    )
            }
    )
    @GetMapping("/resource/download")
    public ResponseEntity<StreamingResponseBody> downloadResource(
            @RequestParam @NotBlank(message = "The 'path' parameter cannot be empty") String path,
            @AuthenticationPrincipal CustomUserDetails user
    ) {

        Object body = storageService.downloadResource(user, path);

        String name = Path.of(path).getFileName().toString();
        if (body instanceof StreamingResponseBody stream) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".zip" + "\"")
                    .body(stream);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + name + "\"")
                .body(out -> {
                    try (InputStream is = (InputStream) body) {
                        is.transferTo(out);
                    }
                });
    }


    @Operation(
            summary = "Move or rename resource",
            description = "Moves or renames a file or folder from one path to another. " +
                    "Note: To test 'empty path' errors in Swagger UI, use a space character " +
                    "or test via external client (curl/Postman).",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Resource successfully moved or renamed",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "File Moved Example",
                                                    value = "{\"path\": \"folder2/\", \"name\": \"file.txt\", " +
                                                            "\"size\": 1024, \"type\": \"FILE\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Directory Renamed Example",
                                                    value = "{\"path\": \"folder1/\", \"name\": \"newFolderName\", " +
                                                            "\"type\": \"DIRECTORY\"}"
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid or missing path",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "Empty From Parameter",
                                                    value = "{\"message\": \"The 'from' parameter cannot be empty\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Empty To Parameter",
                                                    value = "{\"message\": \"The 'to' parameter cannot be empty\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Type Mismatch",
                                                    value = "{\"message\": \"Resource type must match " +
                                                            "(file -> file, folder/ -> folder/).\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Simultaneous Path and Name Change",
                                                    value = "{\"message\": \"Operation cannot change both path " +
                                                            "and file name at the same time.\"}"
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Unauthorized: User is not authenticated",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Unauthorized Response Example",
                                            value = "{\"message\": \"Unauthorized\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Resource not found",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Not Found Response Example",
                                            value = "{\"message\": \"Source not found: folder1/file.txt\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Conflict: Resource at destination already exists",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Conflict Response Example",
                                            value = "{\"message\": " +
                                                    "\"Destination resource already exists: folder2/file.txt\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Unknown server error",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Internal Server Error Example",
                                            value = "{\"message\": \"An internal server error occurred.\"}"
                                    )
                            )
                    )
            }
    )
    @GetMapping("/resource/move")
    public ResponseEntity<ResourceInfo> moveOrRenameResource(
            @RequestParam 
            @NotBlank(message = "Source path cannot be empty") 
            String from,
            @RequestParam 
            @NotBlank(message = "Destination path cannot be empty") 
            String to,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ResourceInfo result = storageService.moveOrRenameResource(userDetails, from, to);
        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Search resources",
            description = "Searches for files and folders matching the query string. " +
                    "Note: To test the 'empty query' error in Swagger UI, use a space character " +
                    "or test via external client (curl/Postman).",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successfully retrieved search results",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "Mixed Results Example",
                                                    value = "[{\"path\": \"folder1/\", \"name\": \"document.txt\", " +
                                                            "\"size\": 1024, \"type\": \"FILE\"}, " +
                                                            "{\"path\": \"\", \"name\": \"documents\", " +
                                                            "\"type\": \"DIRECTORY\"}]"
                                            ),
                                            @ExampleObject(
                                                    name = "Empty Results Example",
                                                    value = "[]"
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid or missing search query",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Empty Query Parameter",
                                            value = "{\"message\": \"Search query cannot be empty\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Unauthorized: User is not authenticated",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Unauthorized Response Example",
                                            value = "{\"message\": \"Unauthorized\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Unknown server error",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Internal Server Error Example",
                                            value = "{\"message\": \"An internal server error occurred.\"}"
                                    )
                            )
                    )
            }
    )
    @GetMapping("/resource/search")
    public ResponseEntity<List<ResourceInfo>> searchResources(
            @RequestParam 
            @NotBlank(message = "Search query cannot be empty") 
            String query,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        List<ResourceInfo> results = storageService.searchUserFiles(userDetails, query);
        return ResponseEntity.ok(results);
    }

    @Operation(
            summary = "Create directory",
            description = "Creates a new empty directory at the specified path. " +
                    "Note: To test the 'empty path' error in Swagger UI, use a space character " +
                    "or test via external client (curl/Postman).",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Directory successfully created",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Successful Response Example",
                                            value = "{\"path\": \"folder1/folder2/\", \"name\": \"folder3\", " +
                                                    "\"type\": \"DIRECTORY\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid or missing path to new directory",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "Empty Path Parameter",
                                                    value = "{\"message\": \"The 'path' parameter cannot be empty\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Missing Trailing Slash",
                                                    value = "{\"message\": \"Folder path must end with '/'\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Invalid Characters",
                                                    value = "{\"message\": \"Invalid characters in path: folder<name>/\"}"
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Unauthorized: User is not authenticated",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Unauthorized Response Example",
                                            value = "{\"message\": \"Unauthorized\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Parent directory does not exist",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Not Found Response Example",
                                            value = "{\"message\": \"Parent folder does not exist: folder1/\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Conflict: Directory already exists",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Conflict Response Example",
                                            value = "{\"message\": \"Resource already exists: folder1/folder2/\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Unknown server error",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Internal Server Error Example",
                                            value = "{\"message\": \"An internal server error occurred.\"}"
                                    )
                            )
                    )
            }
    )
    @PostMapping("/directory")
    public ResponseEntity<ResourceInfo> createDirectory(
            @RequestParam 
            @NotBlank(message = "The 'path' parameter cannot be empty") 
            String path,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ResourceInfo created = storageService.createDirectory(userDetails, path);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(
            summary = "List directory contents",
            description = "Returns a list of files and folders in the specified directory. " +
                    "Note: Empty path (?path=) returns root directory (200 OK). " +
                    "To test 'Missing Path Parameter' error (400), use external client (curl/Postman) " +
                    "because Swagger UI requires path parameter.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successfully retrieved directory contents",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "Directory with Files and Folders",
                                                    value = "[{\"path\": \"folder1/\", \"name\": \"subfolder\", " +
                                                            "\"type\": \"DIRECTORY\"}, " +
                                                            "{\"path\": \"folder1/\", \"name\": \"document.txt\", " +
                                                            "\"size\": 1024, \"type\": \"FILE\"}, " +
                                                            "{\"path\": \"folder1/\", \"name\": \"image.png\", " +
                                                            "\"size\": 2048, \"type\": \"FILE\"}]"
                                            ),
                                            @ExampleObject(
                                                    name = "Empty Directory",
                                                    value = "[]"
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid or missing path",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "Missing Path Parameter",
                                                    value = "{\"message\": " +
                                                            "\"Required request parameter is missing: path\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Invalid Characters",
                                                    value = "{\"message\": \"Invalid characters in path: folder<name>/\"}"
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Unauthorized: User is not authenticated",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Unauthorized Response Example",
                                            value = "{\"message\": \"Unauthorized\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Directory does not exist",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Not Found Response Example",
                                            value = "{\"message\": \"Resource not found: folder1/\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Unknown server error",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResourceInfo.class),
                                    examples = @ExampleObject(
                                            name = "Internal Server Error Example",
                                            value = "{\"message\": \"An internal server error occurred.\"}"
                                    )
                            )
                    )
            }
    )
    @GetMapping("/directory")
    public ResponseEntity<List<ResourceInfo>> listDirectory(
            @RequestParam String path,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (path == null || path.isEmpty()) {
            path = "/";
        }

        List<ResourceInfo> content = storageService.listDirectory(userDetails, path);
        return ResponseEntity.ok(content);
    }
}
