package com.clara.ops.challenge.document_management_service_challenge.service;

import com.clara.ops.challenge.document_management_service_challenge.dto.DocumentDownloadUrl;
import com.clara.ops.challenge.document_management_service_challenge.dto.DocumentSearchFilters;
import com.clara.ops.challenge.document_management_service_challenge.dto.PaginatedDocumentSearchResponse;
import com.clara.ops.challenge.document_management_service_challenge.dto.UploadDocumentRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

  void uploadDocument(MultipartFile file, UploadDocumentRequest request);

  PaginatedDocumentSearchResponse searchDocuments(DocumentSearchFilters filters, Pageable pageable);

  DocumentDownloadUrl generateDownloadUrl(Integer documentId);
}
