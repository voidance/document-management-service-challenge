package com.clara.ops.challenge.document_management_service_challenge.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.clara.ops.challenge.document_management_service_challenge.dto.DocumentDownloadUrl;
import com.clara.ops.challenge.document_management_service_challenge.dto.DocumentSearchFilters;
import com.clara.ops.challenge.document_management_service_challenge.dto.PaginatedDocumentSearchResponse;
import com.clara.ops.challenge.document_management_service_challenge.dto.UploadDocumentRequest;
import com.clara.ops.challenge.document_management_service_challenge.model.Document;
import com.clara.ops.challenge.document_management_service_challenge.repository.DocumentRepository;
import io.minio.*;
import io.minio.messages.Item;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DocumentManagementIntegrationTest {

  private static final String SERVICE_URL = "http://localhost:";

  @LocalServerPort int port;

  @Autowired private DocumentRepository documentRepository;

  @Autowired private MinioClient minioClient;

  @Value("${MINIO_BUCKET:document-bucket}")
  private String bucketName;

  @BeforeEach
  void preTestClean() throws Exception {
    // Clean the DB
    documentRepository.deleteAll();

    // Clean MinIO bucket
    Iterable<Result<Item>> results =
        minioClient.listObjects(
            ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());

    for (Result<Item> result : results) {
      Item item = result.get();
      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(bucketName).object(item.objectName()).build());
    }
  }

  @Test
  void uploadDocument_savesToDbAndMinio() throws Exception {
    ResponseEntity<Void> response =
        sendUploadRequest(
            "fakeuser", "mydoc.pdf", new String[] {"tag1", "tag2"}, "test".getBytes());

    Assertions.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // Check metadata in DB
    List<Document> allDocs = documentRepository.findAll();
    assertThat(allDocs)
        .anySatisfy(
            doc -> {
              assertThat(doc.getUser()).isEqualTo("fakeuser");
              assertThat(doc.getDocumentName()).isEqualTo("mydoc.pdf");
              assertThat(doc.getMinioPath()).isEqualTo("fakeuser/mydoc.pdf");
            });

    // Check file in MinIO
    var minioPath = "fakeuser/mydoc.pdf";
    var stat =
        minioClient.statObject(
            StatObjectArgs.builder().bucket("document-bucket").object(minioPath).build());
    Assertions.assertThat(stat.size()).isGreaterThan(0);
    Assertions.assertThat(stat.contentType()).isEqualTo("application/pdf");
    Assertions.assertThat(stat.bucket()).isEqualTo("document-bucket");
    Assertions.assertThat(stat.object()).isEqualTo("fakeuser/mydoc.pdf");
  }

  @Test
  void upload_invalidInput_shouldFail() throws Exception {
    // No file, no user, missing fields
    RestTemplate restTemplate = new RestTemplate();
    String url = SERVICE_URL + port + "/document-management/upload";
    LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add(
        "file",
        new ByteArrayResource("abc".getBytes()) {
          @Override
          public String getFilename() {
            return "nofile.pdf";
          }
        });
    // Malformed request
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    HttpEntity<?> request = new HttpEntity<>(body, headers);
    try {
      restTemplate.exchange(url, HttpMethod.POST, request, String.class);
      fail("Expected BadRequest did not happen");
    } catch (HttpClientErrorException.BadRequest ex) {
      assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    assertThat(documentRepository.count()).isZero();
  }

  @Test
  void upload_duplicateDocument_shouldNotDuplicate() throws Exception {
    byte[] content = "duptest".getBytes();
    sendUploadRequest("dupuser", "dupe.pdf", new String[] {"t"}, content);
    // Repeat upload
    try {
      ResponseEntity<Void> duplicate =
          sendUploadRequest("dupuser", "dupe.pdf", new String[] {"t"}, content);
      fail("Expected Conflict did not happen");
    } catch (HttpClientErrorException.Conflict ex) {
      assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    List<Document> docs = documentRepository.findAll();
    assertThat(
            docs.stream()
                .filter(
                    d -> d.getUser().equals("dupuser") && d.getDocumentName().equals("dupe.pdf"))
                .count())
        .isEqualTo(1);
  }

  @Test
  void searchDocument_findsTheDocument() throws Exception {

    sendUploadRequest("fakeuser", "mydoc.pdf", new String[] {"tag1", "tag2"}, "test".getBytes());

    String baseUrl = SERVICE_URL + port + "/document-management/search?page=0&size=50";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<String> entity = new HttpEntity<>("{}", headers);

    ResponseEntity<String> response =
        new RestTemplate().postForEntity(baseUrl, entity, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("\"totalItems\":1");
    assertThat(body).contains("\"user\":\"fakeuser\"");
    assertThat(body).contains(",\"name\":\"mydoc.pdf\"");
    assertThat(body).contains("\"tags\":[\"tag1\",\"tag2\"]");
  }

  @Test
  void searchDocuments_filters_and_pagination() throws Exception {
    // Upload multiple docs
    sendUploadRequest("Xuser", "Aname.pdf", new String[] {"tag1", "tag2"}, "1".getBytes());
    sendUploadRequest("Yuser", "Bname.pdf", new String[] {"tag2", "tag3"}, "2".getBytes());
    sendUploadRequest("Xuser", "Cname.pdf", new String[] {"tag3", "tag4"}, "3".getBytes());

    RestTemplate restTemplate = new RestTemplate();
    String url = SERVICE_URL + port + "/document-management/search";
    // Filter by user
    DocumentSearchFilters filters = new DocumentSearchFilters("Xuser", null, null);

    ResponseEntity<PaginatedDocumentSearchResponse> responseUser =
        restTemplate.postForEntity(url, filters, PaginatedDocumentSearchResponse.class);
    assertThat(responseUser.getBody().documents().size()).isEqualTo(2);

    // Filter by tag
    filters = new DocumentSearchFilters(null, null, Set.of("tag3"));

    ResponseEntity<PaginatedDocumentSearchResponse> respTag =
        restTemplate.postForEntity(url, filters, PaginatedDocumentSearchResponse.class);
    assertThat(respTag.getBody().documents().size()).isEqualTo(2);

    // Pagination: page size 1
    filters = new DocumentSearchFilters(null, null, Set.of());
    Pageable pageable = PageRequest.of(0, 1);
    // Custom method or adjust controller to handle pageable param (depends on your implementation)
    ResponseEntity<PaginatedDocumentSearchResponse> respPage =
        restTemplate.postForEntity(url + "?size=1", filters, PaginatedDocumentSearchResponse.class);
    assertThat(respPage.getBody().documents().size()).isEqualTo(1);

    // No match
    filters = new DocumentSearchFilters("NoExistUser", null, null);

    ResponseEntity<PaginatedDocumentSearchResponse> noMatch =
        restTemplate.postForEntity(url, filters, PaginatedDocumentSearchResponse.class);
    assertThat(noMatch.getBody().documents()).isEmpty();
  }

  @Test
  void downloadDocument_existing_and_notFound() throws Exception {
    // Upload a document and get its id
    byte[] fileContent = "Hello World!".getBytes(StandardCharsets.UTF_8);
    Integer docId = uploadAndGetDocumentId("user1", "sample.pdf", new String[] {"a"}, fileContent);

    // Download should return a presigned url
    RestTemplate restTemplate = new RestTemplate();
    String url = SERVICE_URL + port + "/document-management/download/" + docId;
    ResponseEntity<DocumentDownloadUrl> result =
        restTemplate.exchange(url, HttpMethod.GET, null, DocumentDownloadUrl.class);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody().url()).contains("http"); // Presigned

    // Download nonexisting document
    String notFoundUrl = SERVICE_URL + port + "/document-management/download/987654";
    try {
      restTemplate.exchange(notFoundUrl, HttpMethod.GET, null, DocumentDownloadUrl.class);
      fail("Expected Notfound request did not happen");
    } catch (HttpClientErrorException.NotFound ex) {
      assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @Test
  void tags_caseInsensitivity_andNewTagHandling() throws Exception {
    // Upload documents with various tag cases and verify search
    sendUploadRequest("taguser", "T1.pdf", new String[] {"CaseTag"}, "t1".getBytes());
    sendUploadRequest("taguser", "T2.pdf", new String[] {"casetag"}, "t2".getBytes());
    // Now search for lower, upper
    RestTemplate restTemplate = new RestTemplate();
    String url = SERVICE_URL + port + "/document-management/search";
    DocumentSearchFilters filters = new DocumentSearchFilters(null, null, Set.of("casetag"));

    ResponseEntity<PaginatedDocumentSearchResponse> response =
        restTemplate.postForEntity(url, filters, PaginatedDocumentSearchResponse.class);
    assertThat(response.getBody().documents().size()).isGreaterThanOrEqualTo(2);
  }

  private ResponseEntity<Void> sendUploadRequest(
      String user, String docName, String[] tags, byte[] fileContent) throws Exception {
    RestTemplate restTemplate = new RestTemplate();
    String url = SERVICE_URL + port + "/document-management/upload";
    LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add(
        "file",
        new ByteArrayResource(fileContent) {
          @Override
          public String getFilename() {
            return docName;
          }
        });
    UploadDocumentRequest requestObj =
        new UploadDocumentRequest(user, docName, Arrays.asList(tags));

    body.add(
        "request",
        new HttpEntity<>(
            requestObj,
            new HttpHeaders() {
              {
                setContentType(MediaType.APPLICATION_JSON);
              }
            }));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    HttpEntity<?> request = new HttpEntity<>(body, headers);
    return restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
  }

  private Integer uploadAndGetDocumentId(
      String user, String docName, String[] tags, byte[] fileContent) throws Exception {
    sendUploadRequest(user, docName, tags, fileContent);
    return documentRepository.findAll().stream()
        .filter(d -> d.getUser().equals(user) && d.getDocumentName().equals(docName))
        .map(Document::getId)
        .findFirst()
        .orElseThrow();
  }
}
