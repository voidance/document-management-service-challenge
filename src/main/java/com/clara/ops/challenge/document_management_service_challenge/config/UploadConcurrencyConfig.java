package com.clara.ops.challenge.document_management_service_challenge.config;

import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UploadConcurrencyConfig {
  @Bean
  public Semaphore uploadSemaphore(@Value("${upload.max-concurrent:3}") int maxConcurrent) {
    return new Semaphore(maxConcurrent);
  }
}
