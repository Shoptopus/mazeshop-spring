package com.shoptopus.core.admin;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.email.OutgoingEmail;
import com.shoptopus.core.email.repository.OutgoingEmailRepository;
import com.shoptopus.core.engagement.Notification;
import com.shoptopus.core.engagement.repository.NotificationRepository;
import com.shoptopus.core.mail.EmailDeliveryService;
import com.shoptopus.core.mail.EmailSettingsService;
import com.shoptopus.core.mail.OutgoingEmailQueueService;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminSupportChannelsService {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final OutgoingEmailRepository outgoingEmailRepository;
    private final OutgoingEmailQueueService outgoingEmailQueueService;
    private final EmailSettingsService emailSettingsService;
    private final EmailDeliveryService emailDeliveryService;

    public AdminSupportChannelsService(
            UserRepository userRepository,
            NotificationRepository notificationRepository,
            OutgoingEmailRepository outgoingEmailRepository,
            OutgoingEmailQueueService outgoingEmailQueueService,
            EmailSettingsService emailSettingsService,
            EmailDeliveryService emailDeliveryService
    ) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.outgoingEmailRepository = outgoingEmailRepository;
        this.outgoingEmailQueueService = outgoingEmailQueueService;
        this.emailSettingsService = emailSettingsService;
        this.emailDeliveryService = emailDeliveryService;
    }

    public Map<String, Object> emailUsers() {
        List<Map<String, Object>> data = userRepository.findAll().stream()
                .filter(user -> user.getDeletedAt() == null)
                .map(user -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", user.getId());
                    row.put("email", user.getEmail());
                    row.put("first_name", user.getFirstName());
                    row.put("last_name", user.getLastName());
                    row.put("name", user.getName());
                    return row;
                })
                .toList();
        return Map.of("data", data);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> sendEmail(Map<String, Object> body) {
        String subject = required(body, "subject");
        String message = required(body, "message");
        List<UUID> ids = ids(body, "user_ids");
        List<User> users = ids.stream()
                .map(this::requireUser)
                .toList();
        users.forEach(user -> {
            outgoingEmailQueueService.queue(user, user.getEmail(), subject, message);
            Notification notification = new Notification();
            notification.setUser(user);
            notification.setType("email_queued");
            notification.setTitle(subject);
            notification.setData(Map.of("message", message));
            notificationRepository.save(notification);
        });
        return Map.of(
                "queued", users.size(),
                "subject", subject,
                "status", "queued"
        );
    }

    public Map<String, Object> outgoingEmails() {
        List<Map<String, Object>> items = outgoingEmailRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(OutgoingEmail::getQueuedAt).reversed())
                .map(this::toOutgoingRow)
                .toList();
        return Map.of(
                "items", items,
                "meta", Map.of("total", items.size())
        );
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> processEmails() {
        return outgoingEmailQueueService.processQueued();
    }

    public Map<String, Object> testEmailSettings(Map<String, Object> body) {
        String recipient = required(body, "recipient");
        // Commit settings before delivery: Gmail refresh opens a separate transaction.
        emailSettingsService.saveAdminSettings(body == null ? null : body.get("email"));
        emailSettingsService.sendTestEmail(recipient, emailDeliveryService);
        return Map.of("status", "sent");
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> gmailAuthorization(Map<String, Object> body) {
        return Map.of(
                "target_url",
                emailSettingsService.gmailAuthorizationRedirect(
                        body == null ? null : body.get("email"),
                        body == null ? null : string(body.get("return_url"))
                ).toString()
        );
    }

    public java.net.URI gmailCallback(String code, String state, String error) {
        return emailSettingsService.gmailCallbackRedirect(code, state, error);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> disconnectGmail() {
        emailSettingsService.disconnectGmail();
        return Map.of("status", "disconnected");
    }

    public Map<String, Object> notifications(String email) {
        User user = requireUser(email);
        List<Map<String, Object>> data = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toRow)
                .toList();
        return Map.of("data", data);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> clearNotifications(String email) {
        User user = requireUser(email);
        List<Notification> rows = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        rows.forEach(notification -> notification.setReadAt(OffsetDateTime.now()));
        notificationRepository.saveAll(rows);
        return Map.of("cleared", rows.size());
    }

    private Map<String, Object> toRow(Notification notification) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", notification.getId());
        row.put("type", notification.getType());
        row.put("title", notification.getTitle());
        row.put("data", notification.getData());
        row.put("read_at", notification.getReadAt());
        row.put("created_at", notification.getCreatedAt());
        return row;
    }

    private Map<String, Object> toOutgoingRow(OutgoingEmail email) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", email.getId());
        row.put("recipient", email.getRecipient());
        row.put("subject", email.getSubject());
        row.put("status", email.getStatus());
        row.put("attempts", email.getAttempts());
        row.put("error_message", email.getErrorMessage());
        row.put("queued_at", email.getQueuedAt());
        row.put("sent_at", email.getSentAt());
        if (email.getUser() == null) {
            row.put("user", null);
        } else {
            row.put("user", Map.of(
                    "id", email.getUser().getId(),
                    "name", email.getUser().getName(),
                    "email", email.getUser().getEmail()
            ));
        }
        return row;
    }

    private User requireUser(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found", "User not found"));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected user_ids is invalid."));
    }

    private String required(Map<String, Object> body, String key) {
        if (body == null || !body.containsKey(key) || body.get(key) == null || String.valueOf(body.get(key)).isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is required.");
        }
        return String.valueOf(body.get(key));
    }

    private List<UUID> ids(Map<String, Object> body, String key) {
        Object raw = body == null ? null : body.get(key);
        if (!(raw instanceof List<?> values) || values.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is required.");
        }
        java.util.ArrayList<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            try {
                ids.add(UUID.fromString(String.valueOf(values.get(i))));
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected " + key + "." + i + " is invalid.");
            }
        }
        return ids;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
