package com.example.cloudstorage.controller;

import com.example.cloudstorage.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "User Management",
        description = "Operations related to the currently authenticated user"
)
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Operation(
            summary = "Get current authenticated user",
            description = "Returns information about the currently authenticated user.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successfully retrieved current user",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = UserResponse.class),
                                    examples = @ExampleObject(
                                            name = "Successful Response Example",
                                            value = "{\"username\": \"user_1\"}"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Unauthorized: User is not authenticated",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = UserResponse.class),
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
                                    schema = @Schema(implementation = UserResponse.class),
                                    examples = @ExampleObject(
                                            name = "Internal Server Error Example",
                                            value = "{\"message\": \"An internal server error occurred.\"}"
                                    )
                            )
                    )
            }
    )

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(new UserResponse(userDetails.getUsername()));
    }
}
