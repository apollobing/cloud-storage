package com.example.cloudstorage.controller;

import com.example.cloudstorage.dto.AuthRequest;
import com.example.cloudstorage.dto.UserResponse;
import com.example.cloudstorage.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Operations related to user authentication")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    @Operation(
            summary = "Register a new user",
            description = "Creates a new user account and automatically logs the user in by creating a session.",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "User successfully registered and logged in",
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
                            responseCode = "400",
                            description = "Validation error: Invalid username or password format",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = UserResponse.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "Invalid Username Length",
                                                    value = "{\"message\": \"Username must be 5–20 characters long\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Invalid Password Length",
                                                    value = "{\"message\": " +
                                                            "\"Password must contain at least 5 characters\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Multiple Validation Errors",
                                                    value = "{\"message\": " +
                                                            "\"Username must be 5–20 characters long, " +
                                                            "Password must contain at least 5 characters\"}"
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Conflict: Username is already taken",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = UserResponse.class),
                                    examples = @ExampleObject(
                                            name = "Conflict Response Example",
                                            value = "{\"message\": \"User already exists\"}"
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
    @PostMapping("/sign-up")
    public ResponseEntity<UserResponse> signUp(
            @Valid @RequestBody AuthRequest authRequest,
            HttpServletRequest request
    ) {
        userService.register(authRequest);

        Authentication authentication = authenticateUser(authRequest.getUsername(), authRequest.getPassword());
        setupSession(request, authentication);

        return ResponseEntity.status(HttpStatus.CREATED).body(new UserResponse(authRequest.getUsername()));
    }

    @Operation(
            summary = "Authenticate user",
            description = "Logs in an existing user and creates a session.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "User successfully authenticated",
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
                            responseCode = "400",
                            description = "Validation error: Invalid username or password format",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = UserResponse.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "Invalid Username Length",
                                                    value = "{\"message\": \"Username must be 5–20 characters long\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Invalid Password Length",
                                                    value = "{\"message\": " +
                                                            "\"Password must contain at least 5 characters\"}"
                                            ),
                                            @ExampleObject(
                                                    name = "Multiple Validation Errors",
                                                    value = "{\"message\": " +
                                                            "\"Username must be 5–20 characters long, " +
                                                            "Password must contain at least 5 characters\"}"
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "401",
                            description = "Unauthorized: Invalid credentials",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = UserResponse.class),
                                    examples = @ExampleObject(
                                            name = "Unauthorized Response Example",
                                            value = "{\"message\": \"Invalid username or password\"}"
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
    @PostMapping("/sign-in")
    public ResponseEntity<UserResponse> signIn(
            @Valid @RequestBody AuthRequest authRequest,
            HttpServletRequest request
    ) {
        Authentication authentication = authenticateUser(authRequest.getUsername(), authRequest.getPassword());
        setupSession(request, authentication);

        return ResponseEntity.ok(new UserResponse(authRequest.getUsername()));
    }

    private Authentication authenticateUser(String username, String password) {
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(username, password);
        return authenticationManager.authenticate(authToken);
    }

    @Operation(
            summary = "Log out user",
            description = "Logs out the currently authenticated user " +
                    "by invalidating the session and clearing cookies.",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "User successfully logged out"
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
    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();

        ResponseCookie deleteCookie = ResponseCookie.from("JSESSIONID", "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .sameSite("Lax")
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .build();
    }

    private void setupSession(HttpServletRequest request, Authentication authentication) {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        HttpSession session = request.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
    }
}
