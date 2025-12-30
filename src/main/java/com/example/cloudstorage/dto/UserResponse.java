package com.example.cloudstorage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Schema(description = "Username of the authenticated user", example = "user_1")
@Data
@AllArgsConstructor
public class UserResponse {
    private String username;
}
