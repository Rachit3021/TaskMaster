package com.taskflow.notification;

import com.taskflow.common.dto.PageResponse;
import com.taskflow.notification.dto.NotificationResponse;
import com.taskflow.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Persisted notifications and real-time SSE stream")
@Validated
public class NotificationController {

    private final NotificationService notificationService;
    private final SseService sseService;

    public NotificationController(NotificationService notificationService, SseService sseService) {
        this.notificationService = notificationService;
        this.sseService = sseService;
    }

    @GetMapping
    @Operation(summary = "List notifications for the current user")
    public PageResponse<NotificationResponse> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return notificationService.list(SecurityUtils.currentUserId(), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count unread notifications")
    public Map<String, Long> unreadCount() {
        return Map.of("unreadCount", notificationService.unreadCount(SecurityUtils.currentUserId()));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    public NotificationResponse markRead(@PathVariable UUID id) {
        return notificationService.markRead(SecurityUtils.currentUserId(), id);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark every notification as read")
    public Map<String, Integer> markAllRead() {
        return Map.of("updated", notificationService.markAllRead(SecurityUtils.currentUserId()));
    }

    /**
     * SSE stream. Browsers cannot attach an Authorization header to EventSource,
     * so this endpoint additionally accepts {@code ?access_token=<jwt>}.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to real-time notifications (Server-Sent Events)")
    public SseEmitter stream() {
        return sseService.subscribe(SecurityUtils.currentUserId());
    }
}
