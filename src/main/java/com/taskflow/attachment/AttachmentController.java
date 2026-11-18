package com.taskflow.attachment;

import com.taskflow.attachment.dto.AttachmentResponse;
import com.taskflow.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Attachments", description = "File uploads attached to tasks")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping("/tasks/{taskId}/attachments")
    @Operation(summary = "List attachments of a task")
    public List<AttachmentResponse> list(@PathVariable UUID taskId) {
        return attachmentService.list(SecurityUtils.currentUserId(), taskId);
    }

    @PostMapping(value = "/tasks/{taskId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a file attachment (multipart/form-data, field name 'file')")
    public AttachmentResponse upload(@PathVariable UUID taskId,
                                     @RequestPart("file") MultipartFile file) {
        return attachmentService.upload(SecurityUtils.currentUserId(), taskId, file);
    }

    @GetMapping("/attachments/{attachmentId}/download")
    @Operation(summary = "Download an attachment")
    public ResponseEntity<Resource> download(@PathVariable UUID attachmentId) {
        AttachmentService.Download download = attachmentService.download(
                SecurityUtils.currentUserId(), attachmentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an attachment (uploader, owner or admin)")
    public ResponseEntity<Void> delete(@PathVariable UUID attachmentId) {
        attachmentService.delete(SecurityUtils.currentUserId(), attachmentId);
        return ResponseEntity.noContent().build();
    }
}
