package com.example.cloudstorage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "User authentication credentials")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {

    @Schema(description = "Username for authentication", example = "user_1")
    @NotBlank(message = "Username required")
    @Size(min = 5, max = 20, message = "Username must be 5–20 characters long")
    private String username;

    @Schema(description = "Password for authentication", example = "password123")
    @NotBlank(message = "Password required")
    @Size(min = 5, max = 60, message = "Password must contain at least 5 characters")
    private String password;
}
