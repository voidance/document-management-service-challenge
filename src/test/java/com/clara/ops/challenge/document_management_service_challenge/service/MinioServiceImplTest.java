package com.clara.ops.challenge.document_management_service_challenge.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.clara.ops.challenge.document_management_service_challenge.exception.MinioStorageException;
import com.clara.ops.challenge.document_management_service_challenge.exception.TooManyUploadsException;
import io.minio.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.web.multipart.MultipartFile;

class MinioServiceImplTest {

  @Mock private MinioClient minioClient;
  @Mock private Semaphore uploadSemaphore;
  @Mock private MultipartFile multipartFile;
  @Mock private StatObjectResponse statObjectResponse;

  @InjectMocks
  private MinioServiceImpl minioService; // The bucketName will need to be injected manually

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    minioService = new MinioServiceImpl(minioClient, uploadSemaphore);

    var bucketField = MinioServiceImpl.class.getDeclaredField("bucketName");
    bucketField.setAccessible(true);
    bucketField.set(minioService, "test-bucket");
  }

  @Test
  void uploadPdf_successful() throws Exception {
    String objectPath = "folder/file.pdf";
    byte[] data = {1, 2, 3};
    InputStream stream = new ByteArrayInputStream(data);

    when(uploadSemaphore.tryAcquire(anyLong(), any())).thenReturn(true);
    when(multipartFile.getInputStream()).thenReturn(stream);
    when(multipartFile.getSize()).thenReturn((long) data.length);

    minioService.uploadPdf(objectPath, multipartFile);

    verify(uploadSemaphore).tryAcquire(5, TimeUnit.MINUTES);
    verify(minioClient).putObject(any(PutObjectArgs.class));
    verify(uploadSemaphore).release();
  }

  @Test
  void uploadPdf_semaphoreNotAcquired_throwsException() throws Exception {
    when(uploadSemaphore.tryAcquire(anyLong(), any())).thenReturn(false);

    assertThatThrownBy(() -> minioService.uploadPdf("x", multipartFile))
        .isInstanceOf(TooManyUploadsException.class)
        .hasMessageContaining("Too many concurrent uploads");
    verify(uploadSemaphore, never()).release();
  }

  @Test
  void uploadPdf_putObjectThrowsException_wrapsInMinioStorageException() throws Exception {
    String objectPath = "folder/file.pdf";
    InputStream stream = new ByteArrayInputStream(new byte[] {1});
    when(uploadSemaphore.tryAcquire(anyLong(), any())).thenReturn(true);
    when(multipartFile.getInputStream()).thenReturn(stream);
    when(multipartFile.getSize()).thenReturn(1L);
    doThrow(new IOException()).when(minioClient).putObject(any());

    assertThatThrownBy(() -> minioService.uploadPdf(objectPath, multipartFile))
        .isInstanceOf(MinioStorageException.class)
        .hasMessageContaining("Error uploading PDF to MinIO");

    verify(uploadSemaphore).release();
  }

  @Test
  void deleteObject_callsRemoveObject() throws Exception {
    String objectPath = "delete.pdf";

    minioService.deleteObject(objectPath);

    verify(minioClient).removeObject(any(RemoveObjectArgs.class));
  }

  @Test
  void deleteObject_minioThrows_wrapsInMinioStorageException() throws Exception {
    doThrow(new RuntimeException("fail")).when(minioClient).removeObject(any());

    assertThatThrownBy(() -> minioService.deleteObject("file"))
        .isInstanceOf(MinioStorageException.class)
        .hasMessageContaining("Failed to delete object");
  }

  @Test
  void generatePresignedUrl_success() throws Exception {
    String url = "http://presigned";
    when(minioClient.getPresignedObjectUrl(any())).thenReturn(url);

    String result = minioService.generatePresignedUrl("object");

    assertThat(result).isEqualTo(url);
  }

  @Test
  void generatePresignedUrl_minioThrows_wrapsInMinioStorageException() throws Exception {
    when(minioClient.getPresignedObjectUrl(any())).thenThrow(new RuntimeException("fail"));

    assertThatThrownBy(() -> minioService.generatePresignedUrl("object.pdf"))
        .isInstanceOf(MinioStorageException.class)
        .hasMessageContaining("Error generating presigned URL");
  }

  @Test
  void getFileSize_success() throws Exception {
    String user = "user";
    String fileName = "file.pdf";
    when(minioClient.statObject(any())).thenReturn(statObjectResponse);
    when(statObjectResponse.size()).thenReturn(42L);

    long size = minioService.getFileSize(user, fileName);

    assertThat(size).isEqualTo(42L);
  }

  @Test
  void getFileSize_minioThrows_wrapsInMinioStorageException() throws Exception {
    when(minioClient.statObject(any())).thenThrow(new RuntimeException("fail"));

    assertThatThrownBy(() -> minioService.getFileSize("u", "f"))
        .isInstanceOf(MinioStorageException.class)
        .hasMessageContaining("Failed to get file size");
  }
}
