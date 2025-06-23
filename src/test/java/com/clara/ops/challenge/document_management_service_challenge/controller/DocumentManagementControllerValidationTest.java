package com.clara.ops.challenge.document_management_service_challenge.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.clara.ops.challenge.document_management_service_challenge.dto.UploadDocumentRequest;
import com.clara.ops.challenge.document_management_service_challenge.exception.DocumentAlreadyExistsException;
import com.clara.ops.challenge.document_management_service_challenge.exception.MinioStorageException;
import com.clara.ops.challenge.document_management_service_challenge.service.DocumentService;
import com.clara.ops.challenge.document_management_service_challenge.validator.UploadDocumentValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocumentManagementController.class)
@Import(UploadDocumentValidator.class)
class DocumentManagementControllerValidationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DocumentService documentService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void uploadDocument_happyPath() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[] {1, 2, 3});
    UploadDocumentRequest request = new UploadDocumentRequest("user1", "doc.pdf", List.of("tag1"));

    String requestJson = objectMapper.writeValueAsString(request);
    MockMultipartFile requestPart =
        new MockMultipartFile("request", "request", "application/json", requestJson.getBytes());

    doNothing().when(documentService).uploadDocument(any(), any());

    mockMvc
        .perform(
            multipart("/document-management/upload")
                .file(file)
                .file(requestPart)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isCreated())
        .andExpect(content().string(""));
  }

  @Test
  void uploadDocument_invalidFile_uploadDocumentValidator() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "not_pdf.txt", "text/plain", new byte[] {5, 5, 5});
    UploadDocumentRequest request =
        new UploadDocumentRequest("user1", "not_pdf.txt", List.of("tag1"));
    String requestJson = objectMapper.writeValueAsString(request);
    MockMultipartFile requestPart =
        new MockMultipartFile("request", "request", "application/json", requestJson.getBytes());

    mockMvc
        .perform(
            multipart("/document-management/upload")
                .file(file)
                .file(requestPart)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "File must be a PDF (application/pdf). | File name must end with .pdf. |"
                        + " Document name must end with .pdf."));
  }

  @Test
  void uploadDocument_invalidInput_validationFramework() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[] {1, 2, 3});

    UploadDocumentRequest invalidRequest =
        new UploadDocumentRequest("user1", "   ", List.of("tag1"));
    String requestJson = objectMapper.writeValueAsString(invalidRequest);
    MockMultipartFile requestPart =
        new MockMultipartFile("request", "request", "application/json", requestJson.getBytes());

    mockMvc
        .perform(
            multipart("/document-management/upload")
                .file(file)
                .file(requestPart)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void uploadDocument_missingFile() throws Exception {
    // No file part
    UploadDocumentRequest request = new UploadDocumentRequest("user1", "doc.pdf", List.of("tag1"));
    String requestJson = objectMapper.writeValueAsString(request);
    MockMultipartFile requestPart =
        new MockMultipartFile("request", "request", "application/json", requestJson.getBytes());

    mockMvc
        .perform(
            multipart("/document-management/upload")
                .file(requestPart)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isBadRequest());
  }

  @Test
  void whenDocumentAlreadyExistsThrows_thenReturnsErrorViaGlobalExceptionHandler()
      throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[] {1, 2, 3});
    UploadDocumentRequest request = new UploadDocumentRequest("user1", "doc.pdf", List.of("tag1"));

    String requestJson = objectMapper.writeValueAsString(request);
    MockMultipartFile requestPart =
        new MockMultipartFile("request", "request", "application/json", requestJson.getBytes());

    doThrow(DocumentAlreadyExistsException.class)
        .when(documentService)
        .uploadDocument(any(), any());

    mockMvc
        .perform(
            multipart("/document-management/upload")
                .file(file)
                .file(requestPart)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isConflict());
  }

  @Test
  void whenMinioStorageThrows_thenReturnsErrorViaGlobalExceptionHandler() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[] {1, 2, 3});
    UploadDocumentRequest request = new UploadDocumentRequest("user1", "doc.pdf", List.of("tag1"));

    String requestJson = objectMapper.writeValueAsString(request);
    MockMultipartFile requestPart =
        new MockMultipartFile("request", "request", "application/json", requestJson.getBytes());

    doThrow(MinioStorageException.class).when(documentService).uploadDocument(any(), any());

    mockMvc
        .perform(
            multipart("/document-management/upload")
                .file(file)
                .file(requestPart)
                .contentType(MediaType.MULTIPART_FORM_DATA))
        .andExpect(status().isInternalServerError());
  }
}
