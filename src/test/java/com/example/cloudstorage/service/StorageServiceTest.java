package com.example.cloudstorage.service;

import com.example.cloudstorage.dto.ResourceInfo;
import com.example.cloudstorage.dto.ResourceType;
import com.example.cloudstorage.entity.User;
import com.example.cloudstorage.exception.ResourceAlreadyExistsException;
import com.example.cloudstorage.exception.ResourceNotFoundException;
import com.example.cloudstorage.repository.UserRepository;
import com.example.cloudstorage.security.CustomUserDetails;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class StorageServiceTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Container
    private static final MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2025-07-18T21-56-31Z-cpuv1")
            .withUserName("minioadmin")
            .withPassword("minioadmin");

    @DynamicPropertySource
    private static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.clean-disabled", () -> "false");

        registry.add("minio.url", minio::getS3URL);
        registry.add("minio.access-key", minio::getUserName);
        registry.add("minio.secret-key", minio::getPassword);
        registry.add("minio.bucket-name", () -> "user-files");
    }

    @Autowired
    private StorageService storageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MinioClient minioClient;

    private CustomUserDetails testUser1;
    private CustomUserDetails testUser2;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        
        cleanupMinIO();
        
        User user1 = new User();
        user1.setUsername("testuser1");
        user1.setPassword(passwordEncoder.encode("password123"));
        user1 = userRepository.save(user1);
        testUser1 = new CustomUserDetails(user1.getId(), user1.getUsername(), user1.getPassword());

        User user2 = new User();
        user2.setUsername("testuser2");
        user2.setPassword(passwordEncoder.encode("password123"));
        user2 = userRepository.save(user2);
        testUser2 = new CustomUserDetails(user2.getId(), user2.getUsername(), user2.getPassword());
    }

    private void cleanupMinIO() throws Exception {
        try {
            Iterable<io.minio.Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket("user-files")
                            .recursive(true)
                            .build()
            );

            for (io.minio.Result<Item> result : objects) {
                Item item = result.get();
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket("user-files")
                                .object(item.objectName())
                                .build()
                );
            }
        } catch (Exception ignored) {
        }
    }

    @Test
    void uploadFile_shouldAppearInMinioUserFolder() throws Exception {
        String path = "";
        MockMultipartFile file = new MockMultipartFile(
                "object",
                "test.txt",
                "text/plain",
                "Test file content".getBytes()
        );
        List<MultipartFile> files = List.of(file);

        List<ResourceInfo> uploaded = storageService.upload(testUser1, path, files);

        assertThat(uploaded).hasSize(1);
        assertThat(uploaded.getFirst().getName()).isEqualTo("test.txt");
        assertThat(uploaded.getFirst().getType()).isEqualTo(ResourceType.FILE);
        assertThat(uploaded.getFirst().getSize()).isEqualTo(17L);

        String expectedPath = "user-" + testUser1.getId() + "-files/test.txt";
        InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket("user-files")
                .object(expectedPath)
                .build());
        
        assertThat(stream).isNotNull();
        String content = new String(stream.readAllBytes());
        assertThat(content).isEqualTo("Test file content");
    }

    @Test
    void uploadFileToSubfolder_shouldCreateFolderStructure() throws Exception {
        String path = "documents/";
        MockMultipartFile file = new MockMultipartFile(
                "object",
                "report.pdf",
                "application/pdf",
                "PDF content".getBytes()
        );
        List<MultipartFile> files = List.of(file);

        List<ResourceInfo> uploaded = storageService.upload(testUser1, path, files);

        assertThat(uploaded).hasSize(1);
        assertThat(uploaded.getFirst().getPath()).isEqualTo("documents/");
        assertThat(uploaded.getFirst().getName()).isEqualTo("report.pdf");

        String expectedPath = "user-" + testUser1.getId() + "-files/documents/report.pdf";
        InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket("user-files")
                .object(expectedPath)
                .build());
        
        assertThat(stream).isNotNull();
    }

    @Test
    void deleteFile_shouldRemoveFromMinio() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "object",
                "to-delete.txt",
                "text/plain",
                "Delete me".getBytes()
        );
        storageService.upload(testUser1, "", List.of(file));

        storageService.deleteResource(testUser1, "to-delete.txt");

        String expectedPath = "user-" + testUser1.getId() + "-files/to-delete.txt";
        assertThatThrownBy(() -> minioClient.getObject(GetObjectArgs.builder()
                .bucket("user-files")
                .object(expectedPath)
                .build()))
                .hasMessageContaining("does not exist");
    }

    @Test
    void deleteFolder_shouldRemoveAllContents() {
        storageService.createDirectory(testUser1, "folder-to-delete/");
        MockMultipartFile file1 = new MockMultipartFile("object", "file1.txt", "text/plain", "content1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("object", "file2.txt", "text/plain", "content2".getBytes());
        storageService.upload(testUser1, "folder-to-delete/", List.of(file1, file2));

        storageService.deleteResource(testUser1, "folder-to-delete/");

        assertThatThrownBy(() -> storageService.getResourceInfo(testUser1, "folder-to-delete/"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void renameFile_shouldUpdateNameInMinio() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "object",
                "old-name.txt",
                "text/plain",
                "Rename test".getBytes()
        );
        storageService.upload(testUser1, "", List.of(file));

        ResourceInfo renamed = storageService.moveOrRenameResource(testUser1, "old-name.txt", "new-name.txt");

        assertThat(renamed.getName()).isEqualTo("new-name.txt");

        String oldPath = "user-" + testUser1.getId() + "-files/old-name.txt";
        assertThatThrownBy(() -> minioClient.getObject(GetObjectArgs.builder()
                .bucket("user-files")
                .object(oldPath)
                .build()))
                .hasMessageContaining("does not exist");

        String newPath = "user-" + testUser1.getId() + "-files/new-name.txt";
        InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket("user-files")
                .object(newPath)
                .build());
        assertThat(stream).isNotNull();
        assertThat(new String(stream.readAllBytes())).isEqualTo("Rename test");
    }

    @Test
    void moveFile_shouldChangeLocation() throws Exception {
        storageService.createDirectory(testUser1, "source/");
        storageService.createDirectory(testUser1, "destination/");
        
        MockMultipartFile file = new MockMultipartFile(
                "object",
                "movable.txt",
                "text/plain",
                "Move me".getBytes()
        );
        storageService.upload(testUser1, "source/", List.of(file));

        ResourceInfo moved = storageService.moveOrRenameResource(
                testUser1, "source/movable.txt", "destination/movable.txt"
        );

        assertThat(moved.getPath()).isEqualTo("destination/");
        assertThat(moved.getName()).isEqualTo("movable.txt");

        String oldPath = "user-" + testUser1.getId() + "-files/source/movable.txt";
        String newPath = "user-" + testUser1.getId() + "-files/destination/movable.txt";

        assertThatThrownBy(() -> minioClient.getObject(GetObjectArgs.builder()
                .bucket("user-files")
                .object(oldPath)
                .build()))
                .hasMessageContaining("does not exist");

        InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket("user-files")
                .object(newPath)
                .build());
        assertThat(stream).isNotNull();
    }

    @Test
    void createDirectory_shouldCreateEmptyFolder() throws Exception {
        ResourceInfo created = storageService.createDirectory(testUser1, "new-folder/");

        assertThat(created.getName()).isEqualTo("new-folder/");
        assertThat(created.getType()).isEqualTo(ResourceType.DIRECTORY);
        assertThat(created.getSize()).isNull();

        String expectedPath = "user-" + testUser1.getId() + "-files/new-folder/";
        InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket("user-files")
                .object(expectedPath)
                .build());
        assertThat(stream).isNotNull();
    }

    @Test
    void userCannotAccessOtherUsersFiles() {
        MockMultipartFile file = new MockMultipartFile(
                "object",
                "private.txt",
                "text/plain",
                "Private content".getBytes()
        );
        storageService.upload(testUser1, "", List.of(file));

        assertThatThrownBy(() -> storageService.getResourceInfo(testUser2, "private.txt"))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThatThrownBy(() -> storageService.downloadResource(testUser2, "private.txt"))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThatThrownBy(() -> storageService.deleteResource(testUser2, "private.txt"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchFindsOnlyUserFiles() {
        MockMultipartFile user1File = new MockMultipartFile(
                "object",
                "annual-report.pdf",
                "application/pdf",
                "User1 report".getBytes()
        );
        storageService.upload(testUser1, "", List.of(user1File));

        MockMultipartFile user2File = new MockMultipartFile(
                "object",
                "monthly-report.pdf",
                "application/pdf",
                "User2 report".getBytes()
        );
        storageService.upload(testUser2, "", List.of(user2File));

        List<ResourceInfo> user1Results = storageService.searchUserFiles(testUser1, "report");

        assertThat(user1Results).hasSize(1);
        assertThat(user1Results.getFirst().getName()).isEqualTo("annual-report.pdf");

        List<ResourceInfo> user2Results = storageService.searchUserFiles(testUser2, "report");

        assertThat(user2Results).hasSize(1);
        assertThat(user2Results.getFirst().getName()).isEqualTo("monthly-report.pdf");
    }

    @Test
    void searchFindsFilesInSubfolders() {
        storageService.createDirectory(testUser1, "docs/");
        storageService.createDirectory(testUser1, "docs/archive/");
        
        MockMultipartFile file1 = new MockMultipartFile("object", "readme.txt", "text/plain", "content".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("object", "readme.md", "text/plain", "markdown".getBytes());
        
        storageService.upload(testUser1, "docs/", List.of(file1));
        storageService.upload(testUser1, "docs/archive/", List.of(file2));

        List<ResourceInfo> results = storageService.searchUserFiles(testUser1, "readme");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ResourceInfo::getName)
                .containsExactlyInAnyOrder("readme.txt", "readme.md");
    }

    @Test
    void listDirectory_shouldShowOnlyDirectContents() {
        storageService.createDirectory(testUser1, "parent/");
        storageService.createDirectory(testUser1, "parent/child/");
        
        MockMultipartFile file1 = new MockMultipartFile(
                "object", "file-in-parent.txt", "text/plain", "content".getBytes()
        );
        MockMultipartFile file2 = new MockMultipartFile(
                "object", "file-in-child.txt", "text/plain", "content".getBytes()
        );
        
        storageService.upload(testUser1, "parent/", List.of(file1));
        storageService.upload(testUser1, "parent/child/", List.of(file2));

        List<ResourceInfo> contents = storageService.listDirectory(testUser1, "parent/");

        assertThat(contents).hasSize(2);
        assertThat(contents).extracting(ResourceInfo::getName)
                .containsExactlyInAnyOrder("child/", "file-in-parent.txt");
        assertThat(contents).extracting(ResourceInfo::getType)
                .contains(ResourceType.DIRECTORY, ResourceType.FILE);
    }

    @Test
    void uploadDuplicateFile_shouldThrowException() {
        MockMultipartFile file = new MockMultipartFile(
                "object",
                "duplicate.txt",
                "text/plain",
                "Original".getBytes()
        );
        storageService.upload(testUser1, "", List.of(file));

        MockMultipartFile duplicate = new MockMultipartFile(
                "object",
                "duplicate.txt",
                "text/plain",
                "Duplicate".getBytes()
        );
        
        assertThatThrownBy(() -> storageService.upload(testUser1, "", List.of(duplicate)))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .hasMessageContaining("duplicate.txt");
    }

    @Test
    void deleteNonExistentResource_shouldThrowException() {
        assertThatThrownBy(() -> storageService.deleteResource(testUser1, "non-existent.txt"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void renameFolder_shouldMoveAllContents() {
        storageService.createDirectory(testUser1, "old-folder/");
        MockMultipartFile file = new MockMultipartFile("object", "file.txt", "text/plain", "content".getBytes());
        storageService.upload(testUser1, "old-folder/", List.of(file));

        ResourceInfo renamed = storageService.moveOrRenameResource(testUser1, "old-folder/", "new-folder/");

        assertThat(renamed.getName()).isEqualTo("new-folder/");
        assertThat(renamed.getType()).isEqualTo(ResourceType.DIRECTORY);

        List<ResourceInfo> contents = storageService.listDirectory(testUser1, "new-folder/");
        assertThat(contents).hasSize(1);
        assertThat(contents.getFirst().getName()).isEqualTo("file.txt");

        assertThatThrownBy(() -> storageService.getResourceInfo(testUser1, "old-folder/"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getResourceInfo_shouldReturnCorrectMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "object",
                "metadata-test.txt",
                "text/plain",
                "Test metadata".getBytes()
        );
        storageService.upload(testUser1, "", List.of(file));

        ResourceInfo info = storageService.getResourceInfo(testUser1, "metadata-test.txt");

        assertThat(info.getName()).isEqualTo("metadata-test.txt");
        assertThat(info.getPath()).isEmpty();
        assertThat(info.getType()).isEqualTo(ResourceType.FILE);
        assertThat(info.getSize()).isEqualTo(13L);
    }

    @Test
    void createDuplicateDirectory_shouldThrowException() {
        storageService.createDirectory(testUser1, "existing-folder/");

        assertThatThrownBy(() -> storageService.createDirectory(testUser1, "existing-folder/"))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .hasMessageContaining("existing-folder/");
    }

    @Test
    void listRootDirectory_shouldReturnUserFiles() {
        MockMultipartFile file = new MockMultipartFile("object", "root-file.txt", "text/plain", "content".getBytes());
        storageService.upload(testUser1, "", List.of(file));
        storageService.createDirectory(testUser1, "root-folder/");

        List<ResourceInfo> contents = storageService.listDirectory(testUser1, "");

        assertThat(contents).hasSizeGreaterThanOrEqualTo(2);
        assertThat(contents).extracting(ResourceInfo::getName)
                .contains("root-file.txt", "root-folder/");
    }

    @Test
    void downloadFile_shouldReturnFileContent() throws Exception {
        String filename = "download-test.txt";
        String content = "File content for download";
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "text/plain", content.getBytes()
        );

        storageService.upload(testUser1, "", List.of(file));

        Object result = storageService.downloadResource(testUser1, filename);

        assertThat(result).isInstanceOf(InputStream.class);
        try (InputStream inputStream = (InputStream) result) {
            String downloadedContent = new String(inputStream.readAllBytes());
            assertThat(downloadedContent).isEqualTo(content);
        }
    }

    @Test
    void downloadNonExistentFile_shouldThrowException() {
        assertThatThrownBy(() -> storageService.downloadResource(testUser1, "nonexistent.txt"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void downloadDirectory_shouldReturnZipStream() throws Exception {
        String dirPath = "download-dir/";
        storageService.createDirectory(testUser1, dirPath);

        MockMultipartFile file1 = new MockMultipartFile(
                "file", "file1.txt", "text/plain", "Content 1".getBytes()
        );
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "file2.txt", "text/plain", "Content 2".getBytes()
        );

        storageService.upload(testUser1, dirPath, List.of(file1, file2));

        Object result = storageService.downloadResource(testUser1, dirPath);

        assertThat(result).isInstanceOf(StreamingResponseBody.class);

        StreamingResponseBody streamingBody = (StreamingResponseBody) result;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        streamingBody.writeTo(outputStream);

        try (ZipInputStream zipInputStream = new ZipInputStream(
                new java.io.ByteArrayInputStream(outputStream.toByteArray()))) {
            
            boolean foundFile1 = false;
            boolean foundFile2 = false;
            
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("file1.txt") || name.endsWith("/file1.txt")) {
                    foundFile1 = true;
                }
                if (name.equals("file2.txt") || name.endsWith("/file2.txt")) {
                    foundFile2 = true;
                }
            }
            
            assertThat(foundFile1).isTrue();
            assertThat(foundFile2).isTrue();
        }
    }

    @Test
    void downloadEmptyDirectory_shouldReturnEmptyZip() throws Exception {
        String dirPath = "empty-dir/";
        storageService.createDirectory(testUser1, dirPath);

        Object result = storageService.downloadResource(testUser1, dirPath);

        assertThat(result).isInstanceOf(StreamingResponseBody.class);

        StreamingResponseBody streamingBody = (StreamingResponseBody) result;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        streamingBody.writeTo(outputStream);

        try (ZipInputStream zipInputStream = new ZipInputStream(
                new java.io.ByteArrayInputStream(outputStream.toByteArray()))) {
            
            int fileCount = 0;
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    fileCount++;
                }
            }
            
            assertThat(fileCount).isEqualTo(0);
        }
    }

    @Test
    void downloadDirectoryWithSubfolders_shouldIncludeAllFiles() throws Exception {
        String parentDir = "parent/";
        String childDir = "parent/child/";
        
        storageService.createDirectory(testUser1, parentDir);
        storageService.createDirectory(testUser1, childDir);

        MockMultipartFile parentFile = new MockMultipartFile(
                "file", "parent-file.txt", "text/plain", "Parent content".getBytes()
        );
        MockMultipartFile childFile = new MockMultipartFile(
                "file", "child-file.txt", "text/plain", "Child content".getBytes()
        );

        storageService.upload(testUser1, parentDir, List.of(parentFile));
        storageService.upload(testUser1, childDir, List.of(childFile));

        Object result = storageService.downloadResource(testUser1, parentDir);

        assertThat(result).isInstanceOf(StreamingResponseBody.class);

        StreamingResponseBody streamingBody = (StreamingResponseBody) result;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        streamingBody.writeTo(outputStream);

        try (ZipInputStream zipInputStream = new ZipInputStream(
                new java.io.ByteArrayInputStream(outputStream.toByteArray()))) {
            
            boolean foundParentFile = false;
            boolean foundChildFile = false;

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().contains("parent-file.txt")) {
                    foundParentFile = true;
                }
                if (entry.getName().contains("child-file.txt")) {
                    foundChildFile = true;
                }
            }

            assertThat(foundParentFile).isTrue();
            assertThat(foundChildFile).isTrue();
        }
    }

    @Test
    void verifyUserIsolation_inMinIO() throws Exception {
        MockMultipartFile user1File = new MockMultipartFile(
                "object", "shared-name.txt", "text/plain", "User1 content".getBytes()
        );
        MockMultipartFile user2File = new MockMultipartFile(
                "object", "shared-name.txt", "text/plain", "User2 content".getBytes()
        );
        
        storageService.upload(testUser1, "", List.of(user1File));
        storageService.upload(testUser2, "", List.of(user2File));

        InputStream user1Stream = (InputStream) storageService.downloadResource(testUser1, "shared-name.txt");
        InputStream user2Stream = (InputStream) storageService.downloadResource(testUser2, "shared-name.txt");

        assertThat(new String(user1Stream.readAllBytes())).isEqualTo("User1 content");
        assertThat(new String(user2Stream.readAllBytes())).isEqualTo("User2 content");

        String user1Path = "user-" + testUser1.getId() + "-files/shared-name.txt";
        String user2Path = "user-" + testUser2.getId() + "-files/shared-name.txt";

        InputStream user1MinioStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket("user-files")
                .object(user1Path)
                .build());
        InputStream user2MinioStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket("user-files")
                .object(user2Path)
                .build());

        assertThat(new String(user1MinioStream.readAllBytes())).isEqualTo("User1 content");
        assertThat(new String(user2MinioStream.readAllBytes())).isEqualTo("User2 content");
    }
}
