package com.clara.ops.challenge.document_management_service_challenge.controller;

import com.clara.ops.challenge.document_management_service_challenge.dto.*;
import com.clara.ops.challenge.document_management_service_challenge.service.DocumentService;
import com.clara.ops.challenge.document_management_service_challenge.validator.UploadDocumentValidator;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/document-management")
@RequiredArgsConstructor
public class DocumentManagementController {
  private final DocumentService documentService;

  private final UploadDocumentValidator uploadDocumentValidator;

  @PostMapping("/upload")
  @Bulkhead(name = "uploadBulkhead")
  public ResponseEntity<Void> uploadDocument(
      @RequestPart("file") MultipartFile file,
      @RequestPart("request") UploadDocumentRequest request) {
    uploadDocumentValidator.validate(file, request);

    documentService.uploadDocument(file, request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PostMapping("/search")
  public ResponseEntity<PaginatedDocumentSearchResponse> searchDocuments(
      @Valid @RequestBody DocumentSearchFilters filters,
      @PageableDefault(page = 0, size = 20)
          @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(documentService.searchDocuments(filters, pageable));
  }

  @GetMapping("/download/{documentId}")
  public ResponseEntity<DocumentDownloadUrl> downloadDocument(@PathVariable Integer documentId) {

    return ResponseEntity.ok(documentService.generateDownloadUrl(documentId));
  }
}
