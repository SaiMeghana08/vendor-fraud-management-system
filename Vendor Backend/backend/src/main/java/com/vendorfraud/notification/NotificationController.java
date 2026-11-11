package com.vendorfraud.notification;

import com.vendorfraud.common.ApiPage;
import com.vendorfraud.notification.dto.NotificationResponse;
import com.vendorfraud.notification.dto.NotificationSummary;
import com.vendorfraud.security.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notifications are private. Every endpoint here is scoped to the
 * authenticated recipient - there is no path or parameter by which one
 * user can name another user's notification.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    public NotificationController(NotificationService notificationService,
                                  CurrentUserService currentUserService) {
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<ApiPage<NotificationResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long me = currentUserService.currentUserId();
        return ResponseEntity.ok(ApiPage.from(
                notificationService.listForRecipient(me, page, size)));
    }

    /** Drives the unread count on the notification bell. */
    @GetMapping("/summary")
    public ResponseEntity<NotificationSummary> summary() {
        return ResponseEntity.ok(
                notificationService.summaryForRecipient(currentUserService.currentUserId()));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable Long notificationId) {
        return ResponseEntity.ok(notificationService.markRead(notificationId,
                currentUserService.currentUserId()));
    }
}
