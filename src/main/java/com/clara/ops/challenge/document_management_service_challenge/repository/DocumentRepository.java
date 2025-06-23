package com.clara.ops.challenge.document_management_service_challenge.repository;

import com.clara.ops.challenge.document_management_service_challenge.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentRepository
    extends JpaRepository<Document, Integer>, JpaSpecificationExecutor<Document> {}
