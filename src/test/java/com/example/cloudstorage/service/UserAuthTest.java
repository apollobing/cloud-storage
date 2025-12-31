package com.example.cloudstorage.service;

import com.example.cloudstorage.dto.AuthRequest;
import com.example.cloudstorage.entity.User;
import com.example.cloudstorage.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import tools.jackson.databind.json.JsonMapper;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class UserAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private MinioClient minioClient;

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.5-alpine");

    @DynamicPropertySource
    private static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.clean-disabled", () -> "false");
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void registerUserSuccessfully() throws Exception {
        String username = "testuser";
        String password = "password123";
        AuthRequest authRequest = new AuthRequest(username, password);

        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(authRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.username").value(username));

        Optional<User> foundUser = userRepository.findByUsername(username);
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getUsername()).isEqualTo(username);
    }

    @Test
    void registerExistingUserFails() throws Exception {
        String existingUsername = "existingUser";
        AuthRequest firstUser = new AuthRequest(existingUsername, "password123");
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(firstUser)))
                .andExpect(status().isCreated());

        AuthRequest secondUser = new AuthRequest(existingUsername, "anotherPass");
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(secondUser)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message").value("Username " + existingUsername + " is already taken"));

    }

    @Test
    void signInUserSuccessfully() throws Exception {
        String username = "signInUser";
        String password = "signInPassword";
        AuthRequest authRequest = new AuthRequest(username, password);

        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(authRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void signInWithWrongPassword() throws Exception {
        String username = "validUser";
        String correctPassword = "correctPass";
        String wrongPassword = "wrongPass";

        AuthRequest registerRequest = new AuthRequest(username, correctPassword);
        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        AuthRequest signInRequest = new AuthRequest(username, wrongPassword);
        mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(signInRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signInNonExistentUser() throws Exception {
        AuthRequest authRequest = new AuthRequest("nonExistentUser", "somePassword");

        mockMvc.perform(post("/api/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(authRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerWithEmptyUsername() throws Exception {
        AuthRequest authRequest = new AuthRequest("", "password123");

        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(authRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerWithEmptyPassword() throws Exception {
        AuthRequest authRequest = new AuthRequest("validUsername", "");

        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(authRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerWithNullCredentials() throws Exception {
        AuthRequest authRequest = new AuthRequest(null, null);

        mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(authRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signOutSuccessfully() throws Exception {
        String username = "userToLogout";
        String password = "password123";

        AuthRequest authRequest = new AuthRequest(username, password);
        var signUpResult = mockMvc.perform(post("/api/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(authRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        var sessionCookie = signUpResult.getResponse().getCookie("SESSION");

        Assertions.assertNotNull(sessionCookie);
        mockMvc.perform(post("/api/auth/sign-out")
                        .cookie(sessionCookie))
                .andExpect(status().isNoContent())
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void signOutWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/auth/sign-out"))
                .andExpect(status().isUnauthorized());
    }
}
