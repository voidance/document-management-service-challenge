package com.clara.ops.challenge.document_management_service_challenge.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.clara.ops.challenge.document_management_service_challenge.dto.DocumentDownloadUrl;
import com.clara.ops.challenge.document_management_service_challenge.dto.DocumentDto;
import com.clara.ops.challenge.document_management_service_challenge.dto.DocumentSearchFilters;
import com.clara.ops.challenge.document_management_service_challenge.dto.PaginatedDocumentSearchResponse;
import com.clara.ops.challenge.document_management_service_challenge.dto.UploadDocumentRequest;
import com.clara.ops.challenge.document_management_service_challenge.exception.DocumentAlreadyExistsException;
import com.clara.ops.challenge.document_management_service_challenge.model.Document;
import com.clara.ops.challenge.document_management_service_challenge.model.Tag;
import com.clara.ops.challenge.document_management_service_challenge.repository.DocumentRepository;
import com.clara.ops.challenge.document_management_service_challenge.repository.TagRepository;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class DocumentServiceImplTest {

  @Mock MinioService minioService;
  @Mock TagRepository tagRepository;
  @Mock DocumentRepository documentRepository;

  @InjectMocks DocumentServiceImpl service;

  @Captor ArgumentCaptor<Document> documentCaptor;

  @BeforeEach
  void init() {
    MockitoAnnotations.openMocks(this);
    documentCaptor = ArgumentCaptor.forClass(Document.class);
  }

  @Test
  void uploadDocument_uploadsFile_and_saves_metadata() {
    MockMultipartFile file =
        new MockMultipartFile("file", "test.pdf", "application/pdf", "test content".getBytes());
    List<String> tags = Arrays.asList("Tag1", "Tag2");
    UploadDocumentRequest request = new UploadDocumentRequest("user1", "test.pdf", tags);

    Tag tag1 = new Tag("tag1");
    Tag tag2 = new Tag("tag2");

    when(documentRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());
    when(tagRepository.findAllByNameIn(anySet())).thenReturn(Collections.emptyList());
    when(tagRepository.save(any(Tag.class))).thenReturn(tag1, tag2);

    service.uploadDocument(file, request);

    verify(minioService).uploadPdf(eq("user1/test.pdf"), eq(file));
    verify(documentRepository).save(documentCaptor.capture());

    Document saved = documentCaptor.getValue();
    assertThat(saved.getUser()).isEqualTo("user1");
    assertThat(saved.getDocumentName()).isEqualTo("test.pdf");
    assertThat(saved.getTags())
        .extracting("name", String.class)
        .containsExactlyInAnyOrder("tag1", "tag2");
    assertThat(saved.getFileSize()).isEqualTo(file.getSize());
    assertThat(saved.getFileType()).isEqualTo(file.getContentType());
  }

  @Test
  void uploadDocument_throws_if_document_already_exists() {
    MockMultipartFile file =
        new MockMultipartFile("file", "test2.pdf", "application/pdf", "abc".getBytes());
    UploadDocumentRequest request = new UploadDocumentRequest("user2", "test2.pdf", List.of());

    when(documentRepository.findOne(any(Specification.class)))
        .thenReturn(Optional.of(new Document()));

    assertThatThrownBy(() -> service.uploadDocument(file, request))
        .isInstanceOf(DocumentAlreadyExistsException.class)
        .hasMessageContaining("already exists");

    verify(minioService, never()).uploadPdf(anyString(), any());
    verify(documentRepository, never()).save(any());
  }

  @Test
  void uploadDocument_handles_tag_normalization_and_duplicates() {
    MockMultipartFile file =
        new MockMultipartFile("file", "d.pdf", "application/pdf", "abc".getBytes());
    List<String> tags = Arrays.asList("Tag", "tag", "  tag  ", "newTag");
    UploadDocumentRequest request = new UploadDocumentRequest("usr", "d.pdf", tags);

    Tag tag1 = new Tag("tag");

    when(documentRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());
    when(tagRepository.findAllByNameIn(anySet())).thenReturn(List.of(tag1));
    when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

    service.uploadDocument(file, request);

    verify(documentRepository).save(documentCaptor.capture());
    Document saved = documentCaptor.getValue();
    assertThat(saved.getTags()).extracting(Tag::getName).contains("tag", "newtag");
    assertThat(saved.getTags()).hasSize(2);
  }

  @Test
  void uploadDocument_deletes_file_if_metadata_save_fails() {
    MockMultipartFile file =
        new MockMultipartFile("file", "ex.pdf", "application/pdf", "test".getBytes());
    UploadDocumentRequest request = new UploadDocumentRequest("x", "ex.pdf", List.of());

    when(documentRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());
    doNothing().when(minioService).uploadPdf(anyString(), any());
    doThrow(new OptimisticLockingFailureException("fail db"))
        .when(documentRepository)
        .save(any(Document.class));

    service.uploadDocument(file, request);

    verify(minioService).deleteObject("x/ex.pdf");
  }

  @Test
  void uploadDocument_handles_null_and_empty_tags() {
    MockMultipartFile file =
        new MockMultipartFile("file", "a.pdf", "application/pdf", "data".getBytes());
    UploadDocumentRequest noTagReq = new UploadDocumentRequest("u", "a.pdf", null);

    when(documentRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

    assertThatCode(() -> service.uploadDocument(file, noTagReq)).doesNotThrowAnyException();

    UploadDocumentRequest emptyTagReq =
        new UploadDocumentRequest("u", "a.pdf", Collections.emptyList());
    assertThatCode(() -> service.uploadDocument(file, emptyTagReq)).doesNotThrowAnyException();
  }

  @Test
  void searchDocuments_returns_expected_page_response()
      throws NoSuchFieldException, IllegalAccessException {
    Document doc1 =
        new Document("user", "doc1", "path1", 123L, "application/pdf", Set.of(new Tag("t1")));
    Document doc2 =
        new Document("user", "doc2", "path2", 456L, "application/pdf", Set.of(new Tag("t2")));
    Field idField = Document.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(doc1, 1);
    idField.set(doc2, 1);

    List<Document> docs = Arrays.asList(doc1, doc2);

    Page<Document> page = new PageImpl<>(docs, PageRequest.of(0, 2), 2);
    when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);

    DocumentSearchFilters filters = new DocumentSearchFilters("user", null, null);
    Pageable pageable = PageRequest.of(0, 2);

    PaginatedDocumentSearchResponse resp = service.searchDocuments(filters, pageable);

    assertThat(resp).isNotNull();
    assertThat(resp.documents()).hasSize(2);
    assertThat(resp.metadata().totalItems()).isEqualTo(2);
    assertThat(resp.documents()).extracting(DocumentDto::name).contains("doc1", "doc2");
  }

  @Test
  void generateDownloadUrl_returns_url_when_found()
      throws NoSuchFieldException, IllegalAccessException {
    Document doc =
        new Document(
            "user", "filename.pdf", "user/filename.pdf", 100L, "application/pdf", Set.of());

    Field idField = Document.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(doc, 1);

    when(documentRepository.findById(123)).thenReturn(Optional.of(doc));
    when(minioService.generatePresignedUrl("user/filename.pdf")).thenReturn("http://url");

    DocumentDownloadUrl url = service.generateDownloadUrl(123);

    assertThat(url.url()).isEqualTo("http://url");
  }

  @Test
  void generateDownloadUrl_throws_when_not_found() {
    when(documentRepository.findById(987)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.generateDownloadUrl(987))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Document not found");
  }
}
