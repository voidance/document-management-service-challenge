package com.clara.ops.challenge.document_management_service_challenge.service;

import com.clara.ops.challenge.document_management_service_challenge.exception.MinioStorageException;
import com.clara.ops.challenge.document_management_service_challenge.exception.TooManyUploadsException;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioServiceImpl implements MinioService {

  private final MinioClient minioClient;

  private final Semaphore uploadSemaphore;

  @Value("${minio.bucket}")
  private String bucketName;

  @Override
  public void uploadPdf(String objectPath, MultipartFile file) {
    boolean acquired = false;

    try (InputStream in = file.getInputStream()) {
      long fileSize = file.getSize();

      // Min bufferSize for MinIO
      final int bufferSize = 5 * 1024 * 1024;

      acquired = uploadSemaphore.tryAcquire(5, TimeUnit.MINUTES);
      if (!acquired) {
        throw new TooManyUploadsException("Too many concurrent uploads. Please try again later.");
      }

      PutObjectArgs args =
          PutObjectArgs.builder().bucket(bucketName).object(objectPath).stream(in, fileSize, -1)
              .contentType("application/pdf")
              .build();

      minioClient.putObject(args);

    } catch (NoSuchAlgorithmException
        | InvalidKeyException
        | IllegalArgumentException
        | IOException
        | MinioException e) {

      throw new MinioStorageException("Error uploading PDF to MinIO", e);
    } catch (InterruptedException e) {

      Thread.currentThread().interrupt();
      throw new MinioStorageException("Upload interrupted while waiting for semaphore", e);

    } finally {
      if (acquired) {

        uploadSemaphore.release();
      }
    }
  }

  @Override
  public void deleteObject(String objectPath) {
    try {
      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(bucketName).object(objectPath).build());
    } catch (Exception e) {
      throw new MinioStorageException("Failed to delete object from MinIO", e);
    }
  }

  @Override
  public String generatePresignedUrl(String objectPath) {
    try {
      return minioClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(Method.GET)
              .bucket(bucketName)
              .object(objectPath)
              .expiry(15, TimeUnit.MINUTES)
              .build());
    } catch (Exception e) {
      throw new MinioStorageException("Error generating presigned URL", e);
    }
  }

  @Override
  public long getFileSize(String user, String fileName) {
    try {
      StatObjectResponse stat =
          minioClient.statObject(
              StatObjectArgs.builder().bucket(bucketName).object(user + "/" + fileName).build());
      return stat.size();
    } catch (Exception e) {
      throw new MinioStorageException("Failed to get file size", e);
    }
  }
}
