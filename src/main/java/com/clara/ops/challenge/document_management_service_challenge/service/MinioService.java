package com.clara.ops.challenge.document_management_service_challenge.service;

import org.springframework.web.multipart.MultipartFile;

public interface MinioService {
  void uploadPdf(String objectPath, MultipartFile file);

  void deleteObject(String objectPath);

  String generatePresignedUrl(String objectPath);

  long getFileSize(String user, String fileName);
}
