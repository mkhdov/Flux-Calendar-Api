package com.flux.calendar_service.attachment;

import com.flux.calendar_service.attachment.dto.AttachmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/attachments")
@RequiredArgsConstructor
@Tag(name = "Attachments", description = "Endpoints for managing event attachments")
public class AttachmentController {
    private final AttachmentService attachmentService;

    @Operation(summary = "Upload an attachment", description = "Uploads a file and associates it as an attachment to a specific event")
    @ApiResponse(responseCode = "201", description = "Attachment uploaded successfully")
    @PostMapping(value = "/event/{eventId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> uploadAttachment(
            @Parameter(description = "ID of the event") @PathVariable String eventId,
            @Parameter(description = "The file to upload") @RequestParam("file") MultipartFile file) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(attachmentService.uploadAttachment(eventId, file));
    }

    @Operation(summary = "Download an attachment", description = "Retrieves the file content of a specific attachment")
    @ApiResponse(responseCode = "200", description = "File retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Attachment not found")
    @GetMapping("/{attachmentId}/file")
    public ResponseEntity<Resource> getFile(
            @Parameter(description = "ID of the attachment") @PathVariable String attachmentId) {
        Attachment attachment = attachmentService.getAttachment(attachmentId);
        java.io.InputStream inputStream = attachmentService.downloadAttachment(attachmentId);
        Resource resource = new InputStreamResource(inputStream);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getTitle() + "\"")
                .body(resource);
    }

    @Operation(summary = "Delete an attachment", description = "Deletes a specific attachment and its associated file")
    @ApiResponse(responseCode = "204", description = "Attachment deleted successfully")
    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> deleteAttachment(
            @Parameter(description = "ID of the attachment") @PathVariable String attachmentId) {
        attachmentService.deleteAttachment(attachmentId);
        return ResponseEntity.noContent().build();
    }
}
