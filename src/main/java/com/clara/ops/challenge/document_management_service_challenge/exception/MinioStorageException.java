package com.clara.ops.challenge.document_management_service_challenge.exception;

public class MinioStorageException extends RuntimeException {
  public MinioStorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
