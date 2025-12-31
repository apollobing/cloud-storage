package com.example.cloudstorage.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class GlobalExceptionHandler {

    /**
     * Handles 401 - Invalid credentials error during login.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentialsException(BadCredentialsException ex) {
        log.debug("Bad credentials exception: {}", ex.getMessage());
        return createErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    /**
     * Handles 409 - User already exists error during registration.
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleUserAlreadyExistsException(UserAlreadyExistsException ex) {
        return createErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Handles 404 - Resource (file or folder) not found error.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFound(ResourceNotFoundException ex) {
        return createErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles 409 - Resource already exists error (during creation/moving).
     */
    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleResourceAlreadyExists(ResourceAlreadyExistsException ex) {
        return createErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Handles DTO validation errors (e.g., short password).
     * Combines multiple validation errors into a single message.
     * Sorting ensures consistent error message order across requests.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String errorDetails = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getDefaultMessage() != null 
                        ? fieldError.getDefaultMessage() 
                        : "Invalid value")
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.joining(", "));

        return new ResponseEntity<>(Map.of("message", errorDetails), HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles custom invalid path errors (e.g., folder path without a trailing slash).
     */
    @ExceptionHandler(InvalidPathException.class)
    public ResponseEntity<Map<String, String>> handleInvalidPath(InvalidPathException ex) {
        return createErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Handles missing required request parameters (e.g., ?path= or ?query=).
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingRequestParameter(
            MissingServletRequestParameterException ex
    ) {
        return createErrorResponse(
                HttpStatus.BAD_REQUEST, 
                "Required request parameter is missing: " + ex.getParameterName()
        );
    }

    /**
     * Handles errors related to multipart requests.
     * Catches: "Current request is not a multipart request" 
     * if the client forgets `Content-Type: multipart/form-data`.
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipartException(MultipartException ex) {
        log.debug("Multipart exception: {}", ex.getMessage());
        return createErrorResponse(
                HttpStatus.BAD_REQUEST, 
                "Malformed multipart request. Please ensure Content-Type is set to multipart/form-data."
        );
    }

    /**
     * Handles missing required parts of a multipart request (e.g., the file itself).
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, String>> handleMissingRequestPart(
            MissingServletRequestPartException ex
    ) {
        if ("object".equals(ex.getRequestPartName())) {
            return createErrorResponse(
                    HttpStatus.BAD_REQUEST, 
                    "At least one file must be selected for upload"
            );
        }
        return createErrorResponse(
                HttpStatus.BAD_REQUEST, 
                "Required request part (file) is missing: " + ex.getRequestPartName()
        );
    }

    /**
     * Handles all unforeseen errors (500 - Internal Server Error).
     * Includes logging for further analysis.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAllUncaughtExceptions(Exception ex) {
        log.error("An unexpected error occurred:", ex);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An internal server error occurred.");
    }

    /**
     * Helper method to create a standardized error response.
     * Forces Content-Type to application/json regardless of Accept header.
     */
    private ResponseEntity<Map<String, String>> createErrorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .header("Content-Type", "application/json")
                .body(Map.of("message", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolationException(
            ConstraintViolationException ex
    ) {
        String errorMessage = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("Validation failed");

        return createErrorResponse(HttpStatus.BAD_REQUEST, errorMessage);
    }
}
