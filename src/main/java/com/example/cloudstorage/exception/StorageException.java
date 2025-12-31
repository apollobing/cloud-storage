package com.example.cloudstorage.exception;

/**
 * Exception thrown when storage operations fail.
 * Wraps underlying MinIO or I/O exceptions with context.
 */
public class StorageException extends RuntimeException {
    
    public StorageException(String message) {
        super(message);
    }
    
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
