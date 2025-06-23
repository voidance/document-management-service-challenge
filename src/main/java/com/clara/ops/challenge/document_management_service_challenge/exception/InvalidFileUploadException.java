package com.clara.ops.challenge.document_management_service_challenge.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidFileUploadException extends RuntimeException {
  public InvalidFileUploadException(String message) {
    super(message);
  }
}
