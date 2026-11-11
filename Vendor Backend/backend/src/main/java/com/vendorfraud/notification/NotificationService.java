package com.vendorfraud.notification;

import com.vendorfraud.common.exception.ResourceNotFoundException;
import com.vendorfraud.notification.dto.NotificationResponse;
import com.vendorfraud.notification.dto.NotificationSummary;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database-backed notifications.
 *
 * <p>Delivery joins the business transaction that caused it
 * ({@code Propagation.MANDATORY}): if the workflow step rolls back, the
 * notification it would have produced rolls back with it. That is what
 * makes the event handling transactionally reliable - there is no
 * window in which a user is told about a state the database never
 * reached.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyUser(Long recipientId, NotificationType type, String title, String message,
                           String referenceType, Long referenceId) {
        if (recipientId == null) {
            return;
        }
        repository.save(new Notification(recipientId, type, title, message, referenceType, referenceId));
        log.debug("Notification queued type={} recipient={} reference={}:{}",
                type, recipientId, referenceType, referenceId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyUsers(List<Long> recipientIds, NotificationType type, String title,
                            String message, String referenceType, Long referenceId) {
        recipientIds.stream().distinct().filter(id -> id != null)
                .forEach(id -> notifyUser(id, type, title, message, referenceType, referenceId));
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForRecipient(Long recipientId, int page, int size) {
        return repository.findByRecipientIdOrderByIdDesc(recipientId, PageRequest.of(page, size))
                .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public NotificationSummary summaryForRecipient(Long recipientId) {
        return new NotificationSummary(repository.countByRecipientIdAndReadFalse(recipientId));
    }

    /**
     * Marks one notification read. The lookup is scoped by recipient, so
     * a request for someone else's notification is indistinguishable
     * from a request for one that does not exist.
     */
    @Transactional
    public NotificationResponse markRead(Long notificationId, Long recipientId) {
        Notification notification = repository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        notification.markRead();
        return NotificationResponse.from(notification);
    }
}
