package com.flux.calendar_service.attachment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Attachment metadata (used when creating event with attachments)")
public record AttachmentRequest(
        @Schema(description = "URL of the uploaded file", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String fileUrl,
        @Schema(description = "Display title", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String title,
        @Schema(description = "MIME type", example = "application/pdf", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String mimeType,
        @Schema(description = "File size in bytes", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long fileSize) {
}
