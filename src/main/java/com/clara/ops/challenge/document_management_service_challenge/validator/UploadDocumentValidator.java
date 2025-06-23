package com.clara.ops.challenge.document_management_service_challenge.validator;

import com.clara.ops.challenge.document_management_service_challenge.dto.UploadDocumentRequest;
import com.clara.ops.challenge.document_management_service_challenge.exception.InvalidFileUploadException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class UploadDocumentValidator {

  private static final long MAX_FILE_SIZE_BYTES = 500L * 1024 * 1024;

  public void validate(MultipartFile file, UploadDocumentRequest request) {
    List<String> errors = new ArrayList<>();

    if (file == null || file.isEmpty()) {
      errors.add("File is required and cannot be empty.");
    } else {
      if (!"application/pdf".equalsIgnoreCase(file.getContentType())) {
        errors.add("File must be a PDF (application/pdf).");
      }

      if (file.getOriginalFilename() == null
          || !file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
        errors.add("File name must end with .pdf.");
      }

      if (file.getSize() > MAX_FILE_SIZE_BYTES) {
        errors.add("File size exceeds 500MB limit.");
      }
    }

    if (request.name() == null
        || request.name().isBlank()
        || !request.name().toLowerCase().endsWith(".pdf")) {
      errors.add("Document name must end with .pdf.");
    }

    if (!errors.isEmpty()) {
      throw new InvalidFileUploadException(String.join(" | ", errors));
    }
  }
}
