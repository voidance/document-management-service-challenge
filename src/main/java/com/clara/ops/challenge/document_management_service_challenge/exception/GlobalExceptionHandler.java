package com.clara.ops.challenge.document_management_service_challenge.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(DocumentAlreadyExistsException.class)
  public ResponseEntity<Map<String, Object>> handleDocumentAlreadyExistsException(
      DocumentAlreadyExistsException ex) {
    log.error("Attempt to create a document that already exists: {}", ex.getMessage(), ex);
    return errorResponse(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(InvalidFileUploadException.class)
  public ResponseEntity<Map<String, Object>> handleInvalidFileUploadException(
      InvalidFileUploadException ex) {
    log.error("Invalid file upload: {}", ex.getMessage(), ex);
    return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidationException(
      MethodArgumentNotValidException ex) {
    var errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .toList();
    log.error("Validation errors: {}", errors, ex);
    return errorResponse(HttpStatus.BAD_REQUEST, "Validation failed", errors);
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<Map<String, Object>> handleMissingServletRequestPartException(
      MissingServletRequestPartException ex) {
    log.error("Missing part: {}", ex.getMessage(), ex);
    return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(BindException.class)
  public ResponseEntity<Map<String, Object>> handleBindException(BindException ex) {
    var errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .toList();
    log.error("Bind errors: {}", errors, ex);
    return errorResponse(HttpStatus.BAD_REQUEST, "Binding failed", errors);
  }

  @ExceptionHandler(TooManyUploadsException.class)
  public ResponseEntity<Map<String, Object>> handleTooManyUploads(TooManyUploadsException ex) {
    return errorResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> handleResponseStatusException(
      ResponseStatusException ex) {
    HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }
    log.error("Problem with response status: {}", ex.getMessage(), ex);
    return errorResponse(status, ex.getReason() != null ? ex.getReason() : "Error");
  }

  @ExceptionHandler(MinioStorageException.class)
  public ResponseEntity<Map<String, Object>> handleMinioStorageException(MinioStorageException ex) {
    log.error("Problem with MinIO: {}", ex.getMessage(), ex);
    return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleOtherExceptions(Exception ex) {
    log.error("Exception: {}", ex.getMessage(), ex);
    return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
  }

  private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String message) {
    return errorResponse(status, message, null);
  }

  private ResponseEntity<Map<String, Object>> errorResponse(
      HttpStatus status, String message, List<String> errors) {
    Map<String, Object> errorBody = new HashMap<>();
    errorBody.put("timestamp", Instant.now());
    errorBody.put("status", status.value());
    errorBody.put("error", status.getReasonPhrase());
    errorBody.put("message", message);
    if (errors != null && !errors.isEmpty()) {
      errorBody.put("errors", errors);
    }

    return ResponseEntity.status(status).body(errorBody);
  }
}
