package com.vendorfraud.notification.dto;

import com.vendorfraud.notification.Notification;
import com.vendorfraud.notification.NotificationType;
import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        String referenceType,
        Long referenceId,
        boolean read,
        Instant readAt,
        Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getReferenceType(), n.getReferenceId(), n.isRead(), n.getReadAt(), n.getCreatedAt());
    }
}
