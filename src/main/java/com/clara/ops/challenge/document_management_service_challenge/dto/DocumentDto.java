package com.clara.ops.challenge.document_management_service_challenge.dto;

import java.time.Instant;
import java.util.Set;

public record DocumentDto(
    String id,
    String user,
    String name,
    Set<String> tags,
    Long size,
    String type,
    Instant createdAt) {}
