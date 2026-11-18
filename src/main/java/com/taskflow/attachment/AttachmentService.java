package com.taskflow.attachment;

import com.taskflow.attachment.dto.AttachmentResponse;
import com.taskflow.common.error.BadRequestException;
import com.taskflow.common.error.ForbiddenException;
import com.taskflow.common.error.NotFoundException;
import com.taskflow.task.Task;
import com.taskflow.task.TaskService;
import com.taskflow.team.TeamMember;
import com.taskflow.team.TeamMembershipGuard;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "scr", "msi", "ps1", "sh", "jar", "js", "vbs", "dll", "cpl");

    private final AttachmentRepository attachmentRepository;
    private final AttachmentStorage storage;
    private final TaskService taskService;
    private final TeamMembershipGuard guard;
    private final UserRepository userRepository;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             AttachmentStorage storage,
                             TaskService taskService,
                             TeamMembershipGuard guard,
                             UserRepository userRepository) {
        this.attachmentRepository = attachmentRepository;
        this.storage = storage;
        this.taskService = taskService;
        this.guard = guard;
        this.userRepository = userRepository;
    }

    @Transactional
    public AttachmentResponse upload(UUID actorId, UUID taskId, MultipartFile file) {
        Task task = taskService.requireAccess(actorId, taskId);
        User uploader = userRepository.findById(actorId)
                .orElseThrow(() -> NotFoundException.of("User", actorId));

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("The uploaded file is empty");
        }
        if (file.getSize() > storage.maxFileSizeBytes()) {
            throw new BadRequestException("File exceeds the maximum allowed size of "
                    + storage.maxFileSizeBytes() + " bytes");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (storage.allowedContentTypes().stream().noneMatch(allowed -> allowed.equalsIgnoreCase(contentType))) {
            throw new BadRequestException("Unsupported file type: " + contentType);
        }
        String originalName = sanitizeFileName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (BLOCKED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Files with '." + extension + "' extension are not allowed");
        }
        String storedName = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

        storage.store(file, storedName);
        try {
            Attachment attachment = attachmentRepository.save(new Attachment(
                    task, uploader, originalName, storedName, contentType, file.getSize()));
            return AttachmentResponse.from(attachment);
        } catch (RuntimeException ex) {
            storage.deleteQuietly(storedName);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(UUID actorId, UUID taskId) {
        taskService.requireAccess(actorId, taskId);
        return attachmentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Download download(UUID actorId, UUID attachmentId) {
        Attachment attachment = requireAttachment(attachmentId);
        taskService.requireAccess(actorId, attachment.getTask().getId());
        Resource resource = storage.load(attachment.getStoredName());
        return new Download(resource, attachment.getOriginalName(),
                attachment.getContentType(), attachment.getSizeBytes());
    }

    @Transactional
    public void delete(UUID actorId, UUID attachmentId) {
        Attachment attachment = requireAttachment(attachmentId);
        Task task = taskService.requireAccess(actorId, attachment.getTask().getId());
        if (!attachment.getUploadedBy().getId().equals(actorId)) {
            TeamMember member = guard.requireMember(task.getProject().getTeam().getId(), actorId);
            if (!member.getRole().canManage()) {
                throw new ForbiddenException(
                        "Only the uploader or a team owner/admin can delete this attachment");
            }
        }
        attachmentRepository.delete(attachment);
        storage.deleteQuietly(attachment.getStoredName());
    }

    private Attachment requireAttachment(UUID attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> NotFoundException.of("Attachment", attachmentId));
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }

    private static String sanitizeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "attachment";
        }
        String name = originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "attachment";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,10}") ? extension : "";
    }

    /** Resource plus metadata needed to stream the file back to the client. */
    public record Download(Resource resource, String fileName, String contentType, long sizeBytes) {
    }
}
