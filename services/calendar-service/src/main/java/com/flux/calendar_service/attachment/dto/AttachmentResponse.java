package com.flux.calendar_service.attachment.dto;

import io.swagger.v3.oas.annotations.media.Schema;import java.time.Instant;
@Schema(description = "Attachment response with file metadata and timestamps")
public record AttachmentResponse(
        @Schema(description = "Unique attachment ID") String id,
        @Schema(description = "File URL") String fileUrl,
        @Schema(description = "Display title") String title,
        @Schema(description = "MIME type") String mimeType,
        @Schema(description = "File size in bytes") Long fileSize,
        @Schema(description = "Creation timestamp") Instant createdAt,
        @Schema(description = "Last update timestamp") Instant updatedAt) {
}
