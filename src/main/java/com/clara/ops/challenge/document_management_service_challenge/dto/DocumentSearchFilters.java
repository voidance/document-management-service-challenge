package com.clara.ops.challenge.document_management_service_challenge.dto;

import java.util.Set;

public record DocumentSearchFilters(String user, String name, Set<String> tags) {}
