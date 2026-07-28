package com.jetmenu.notification;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {

    private UUID id;
    private NotificationType type;
    private String title;
    private String message;
    private String referenceData;
    private String referenceDisplay;
    private NotificationStatus status;
    private Instant createdAt;
    private Instant resolvedAt;
}
