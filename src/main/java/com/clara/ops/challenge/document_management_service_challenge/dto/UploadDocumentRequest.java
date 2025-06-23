package com.clara.ops.challenge.document_management_service_challenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UploadDocumentRequest(
    @NotBlank String user, @NotBlank String name, @NotNull List<String> tags) {}
