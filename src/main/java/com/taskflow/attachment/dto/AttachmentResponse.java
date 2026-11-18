package com.taskflow.attachment.dto;

import com.taskflow.attachment.Attachment;
import com.taskflow.user.dto.UserSummary;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(
        UUID id,
        UUID taskId,
        String fileName,
        String contentType,
        long sizeBytes,
        UserSummary uploadedBy,
        String downloadUrl,
        Instant createdAt
) {
    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getTask().getId(),
                attachment.getOriginalName(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                UserSummary.from(attachment.getUploadedBy()),
                "/api/v1/attachments/" + attachment.getId() + "/download",
                attachment.getCreatedAt()
        );
    }
}
