package com.clara.ops.challenge.document_management_service_challenge.dto;

import java.util.List;

public record PaginatedDocumentSearchResponse(Metadata metadata, List<DocumentDto> documents) {}
