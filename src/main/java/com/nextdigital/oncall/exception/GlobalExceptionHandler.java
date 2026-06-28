package com.nextdigital.oncall.exception;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.nextdigital.oncall.dto.ApiResponse;
import com.nextdigital.oncall.dto.ValidationError;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ValidationError.of("NOT_FOUND", ex.getMessage())));
    }

    @ExceptionHandler(ShiftOverlapException.class)
    public ResponseEntity<ApiResponse<Void>> handleShiftOverlap(ShiftOverlapException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ValidationError.of("SHIFT_OVERLAP", ex.getMessage())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ValidationError.of("INVALID_ARGUMENT", ex.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ValidationError("VALIDATION_ERROR", fe.getDefaultMessage(), fe.getField()))
                .collect(java.util.stream.Collectors.toList());

        // Include class-level (cross-field) validation errors
        ex.getBindingResult().getGlobalErrors().stream()
                .map(oe -> new ValidationError("VALIDATION_ERROR", oe.getDefaultMessage(), null))
                .forEach(errors::add);

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ValidationError.of("INTERNAL_ERROR", "An unexpected error occurred")));
    }
}
