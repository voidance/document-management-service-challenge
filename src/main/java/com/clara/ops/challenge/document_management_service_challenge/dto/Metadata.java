package com.clara.ops.challenge.document_management_service_challenge.dto;

public record Metadata(
    int currentPage, int itemsPerPage, int currentItems, int totalPages, int totalItems) {}
