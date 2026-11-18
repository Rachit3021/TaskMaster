package com.taskflow.notification;

import com.taskflow.common.dto.PageResponse;
import com.taskflow.common.error.NotFoundException;
import com.taskflow.notification.dto.NotificationResponse;
import com.taskflow.user.User;
import com.taskflow.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationService implements NotificationPublisher {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SseService sseService;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               SseService sseService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.sseService = sseService;
    }

    @Override
    @Transactional
    public void publish(UUID recipientId, NotificationType type, String title, String message,
                        UUID teamId, UUID projectId, UUID taskId) {
        if (recipientId == null) {
            return;
        }
        User recipient = userRepository.findById(recipientId).orElse(null);
        if (recipient == null) {
            return;
        }
        Notification notification = notificationRepository.save(
                new Notification(recipient, type, truncate(title, 160), truncate(message, 500),
                        teamId, projectId, taskId));
        NotificationResponse payload = NotificationResponse.from(notification);
        // Emit only after the surrounding transaction commits, so clients never see
        // a notification that was rolled back.
        afterCommit(() -> sseService.publish(recipientId, payload));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> result = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadAtIsNull(userId, pageable)
                : notificationRepository.findByRecipientId(userId, pageable);
        return PageResponse.from(result, NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> NotFoundException.of("Notification", notificationId));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId, Instant.now());
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
