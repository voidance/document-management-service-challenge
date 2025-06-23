package com.clara.ops.challenge.document_management_service_challenge.service;

import com.clara.ops.challenge.document_management_service_challenge.dto.*;
import com.clara.ops.challenge.document_management_service_challenge.exception.DocumentAlreadyExistsException;
import com.clara.ops.challenge.document_management_service_challenge.model.Document;
import com.clara.ops.challenge.document_management_service_challenge.model.Tag;
import com.clara.ops.challenge.document_management_service_challenge.repository.DocumentRepository;
import com.clara.ops.challenge.document_management_service_challenge.repository.DocumentSpecifications;
import com.clara.ops.challenge.document_management_service_challenge.repository.TagRepository;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

  private final MinioService minioService;
  private final TagRepository tagRepository;
  private final DocumentRepository documentRepository;

  @Override
  @Transactional
  public void uploadDocument(MultipartFile file, UploadDocumentRequest request) {

    documentRepository
        .findOne(
            DocumentSpecifications.documentNameEquals(request.name())
                .and(DocumentSpecifications.userEquals(request.user())))
        .ifPresent(
            doc -> {
              throw new DocumentAlreadyExistsException(
                  "A document with the same name already exists for this user.");
            });

    String minioPath = generateMinioPath(request.user(), request.name());

    minioService.uploadPdf(minioPath, file);

    try {
      saveDocumentMetadataInDB(minioPath, request, file.getSize(), file.getContentType());
    } catch (OptimisticLockingFailureException | IllegalArgumentException dbEx) {
      log.error(
          "Failed to save document metadata for path {}: {}", minioPath, dbEx.getMessage(), dbEx);
      minioService.deleteObject(minioPath);
    }
  }

  private String generateMinioPath(String user, String fileName) {
    return String.format("%s/%s", user, fileName);
  }

  private void saveDocumentMetadataInDB(
      String minioPath, UploadDocumentRequest request, long fileSize, String fileType) {

    Set<Tag> resolvedTags = resolveOrCreateTags(request.tags());

    Document doc =
        new Document(request.user(), request.name(), minioPath, fileSize, fileType, resolvedTags);

    documentRepository.save(doc);
  }

  private Set<Tag> resolveOrCreateTags(List<String> tagNames) {
    if (tagNames == null) return Collections.emptySet();

    Set<String> normalizedTagNames =
        tagNames.stream()
            .filter(s -> s != null && !s.trim().isEmpty())
            .map(String::trim)
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    if (normalizedTagNames.isEmpty()) return Collections.emptySet();

    List<Tag> existingTags = tagRepository.findAllByNameIn(normalizedTagNames);

    Set<String> existingTagNames =
        existingTags.stream().map(tag -> tag.getName().toLowerCase()).collect(Collectors.toSet());

    Set<String> missingTagNames = new java.util.HashSet<>(normalizedTagNames);
    missingTagNames.removeAll(existingTagNames);

    List<Tag> createdTags = new java.util.ArrayList<>();
    for (String name : missingTagNames) {

      createdTags.add(tagRepository.save(new Tag(name)));
    }

    Set<Tag> resultTags = new java.util.HashSet<>(existingTags);
    resultTags.addAll(createdTags);
    return resultTags;
  }

  @Override
  public PaginatedDocumentSearchResponse searchDocuments(
      DocumentSearchFilters filters, Pageable pageable) {
    Specification<Document> spec =
        Specification.where(DocumentSpecifications.userEquals(filters.user()))
            .and(DocumentSpecifications.nameLike(filters.name()))
            .and(DocumentSpecifications.hasAnyTag(filters.tags()));
    Page<Document> result = documentRepository.findAll(spec, pageable);

    List<DocumentDto> docs = result.getContent().stream().map(this::mapToDto).toList();
    Metadata metadata =
        new Metadata(
            result.getNumber(),
            result.getSize(),
            result.getNumberOfElements(),
            result.getTotalPages(),
            (int) result.getTotalElements());

    return new PaginatedDocumentSearchResponse(metadata, docs);
  }

  @Override
  public DocumentDownloadUrl generateDownloadUrl(Integer documentId) {
    Document entity =
        documentRepository
            .findById(documentId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

    String url = minioService.generatePresignedUrl(entity.getMinioPath());
    return new DocumentDownloadUrl(url);
  }

  private DocumentDto mapToDto(Document e) {
    Set<String> tagNames = e.getTags().stream().map(Tag::getName).collect(Collectors.toSet());

    return new DocumentDto(
        e.getId() != null ? e.getId().toString() : null,
        e.getUser(),
        e.getDocumentName(),
        tagNames,
        e.getFileSize(),
        e.getFileType(),
        e.getCreatedAt());
  }
}
