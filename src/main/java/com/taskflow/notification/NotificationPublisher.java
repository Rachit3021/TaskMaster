package com.taskflow.notification;

import java.util.UUID;

/** Small facade used by other modules to raise notifications. */
public interface NotificationPublisher {

    void publish(UUID recipientId, NotificationType type, String title, String message,
                 UUID teamId, UUID projectId, UUID taskId);

    default void publish(UUID recipientId, NotificationType type, String title, String message) {
        publish(recipientId, type, title, message, null, null, null);
    }
}
