package com.shoptopus.core.messaging;

import com.shoptopus.core.adminui.AdminUiMediaService;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.files.ManagedFileService;
import com.shoptopus.core.engagement.EngagementService;
import com.shoptopus.core.mail.ShopMailService;
import com.shoptopus.core.messaging.repository.OrderMessageRepository;
import com.shoptopus.core.messaging.repository.OrderMessageThreadRepository;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderMessagingService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final OrderMessageThreadRepository threadRepository;
    private final OrderMessageRepository messageRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ManagedFileService managedFileService;
    private final AdminUiMediaService mediaService;
    private final ShopMailService shopMailService;
    private final EngagementService engagementService;

    public OrderMessagingService(
            OrderMessageThreadRepository threadRepository,
            OrderMessageRepository messageRepository,
            OrderRepository orderRepository,
            UserRepository userRepository,
            ManagedFileService managedFileService,
            AdminUiMediaService mediaService,
            ShopMailService shopMailService,
            EngagementService engagementService
    ) {
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.managedFileService = managedFileService;
        this.mediaService = mediaService;
        this.shopMailService = shopMailService;
        this.engagementService = engagementService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminThreads(Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? DEFAULT_PAGE_SIZE : Math.min(perPage, 100);
        var result = threadRepository.findAllByOrderByLastMessageAtDescCreatedAtDesc(PageRequest.of(safePage - 1, safePerPage));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", safePage);
        meta.put("per_page", safePerPage);
        meta.put("total", (int) result.getTotalElements());
        meta.put("total_pages", Math.max(1, result.getTotalPages()));

        return Map.of(
                "items", result.getContent().stream().map(this::threadSummaryRow).toList(),
                "meta", meta
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> customerThreads(String email, Integer page, Integer perPage) {
        User user = requireUser(email);
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? DEFAULT_PAGE_SIZE : Math.min(perPage, 100);
        var result = threadRepository.findByCustomerUserIdOrderByLastMessageAtDescCreatedAtDesc(user.getId(), PageRequest.of(safePage - 1, safePerPage));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", safePage);
        meta.put("per_page", safePerPage);
        meta.put("total", (int) result.getTotalElements());
        meta.put("total_pages", Math.max(1, result.getTotalPages()));

        return Map.of(
                "items", result.getContent().stream().map(this::customerThreadSummaryRow).toList(),
                "meta", meta
        );
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> ensureAdminThread(UUID orderId) {
        OrderMessageThread thread = ensureThread(requireOrder(orderId));
        return threadDetailRow(thread);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> ensureCustomerThread(String email, UUID orderId) {
        User user = requireUser(email);
        Order order = requireOrder(orderId);
        if (order.getUser() == null || !order.getUser().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden", "Order does not belong to authenticated customer.");
        }
        OrderMessageThread thread = ensureThread(order);
        if (thread.getCustomerUser() == null) {
            thread.setCustomerUser(user);
            threadRepository.save(thread);
        }
        return threadDetailRow(thread);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> adminThreadDetail(UUID threadId) {
        OrderMessageThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Message thread not found", "Message thread not found."));
        return threadDetailRow(thread);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> customerThreadDetail(String email, UUID threadId) {
        User user = requireUser(email);
        OrderMessageThread thread = threadRepository.findByIdAndCustomerUserId(threadId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Message thread not found", "Message thread not found."));
        return threadDetailRow(thread);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> postAdminMessage(String senderEmail, UUID threadId, Map<String, Object> body) {
        User sender = requireUser(senderEmail);
        OrderMessageThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Message thread not found", "Message thread not found."));
        OrderMessage message = createMessage(thread, sender, "admin", body);
        String senderName = sender.getName() == null || sender.getName().isBlank() ? sender.getEmail() : sender.getName();
        if (thread.getCustomerUser() != null) {
            shopMailService.sendNewMessageToCustomer(thread.getCustomerUser().getEmail(), thread.getOrder().getSlug(), thread.getId(), senderName);
            engagementService.notifyUser(
                    thread.getCustomerUser(),
                    "new_message",
                    "New message about your order",
                    Map.of(
                            "message", senderName + " sent you a new message about " + thread.getOrder().getSlug() + ".",
                            "thread_id", thread.getId(),
                            "order_id", thread.getOrder().getId(),
                            "order_slug", thread.getOrder().getSlug()
                    )
            );
        }
        return messageRow(message);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> postCustomerMessage(String senderEmail, UUID threadId, Map<String, Object> body) {
        User sender = requireUser(senderEmail);
        OrderMessageThread thread = threadRepository.findByIdAndCustomerUserId(threadId, sender.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Message thread not found", "Message thread not found."));
        OrderMessage message = createMessage(thread, sender, "customer", body);
        String senderName = sender.getName() == null || sender.getName().isBlank() ? sender.getEmail() : sender.getName();
        shopMailService.sendNewMessageToAdmins(thread.getOrder(), thread.getId(), senderName);
        engagementService.notifyAdmins(
                "new_message",
                "New customer message",
                Map.of(
                        "message", senderName + " sent a new message about " + thread.getOrder().getSlug() + ".",
                        "thread_id", thread.getId(),
                        "order_id", thread.getOrder().getId(),
                        "order_slug", thread.getOrder().getSlug()
                )
        );
        return messageRow(message);
    }

    public Map<String, Object> uploadMessageImage(MultipartFile file, boolean storefront) {
        return mediaService.uploadImage(file, storefront ? "message-customer" : "message-admin");
    }

    private OrderMessage createMessage(OrderMessageThread thread, User sender, String role, Map<String, Object> body) {
        String messageBody = str(body == null ? null : body.get("body"));
        if (messageBody == null || messageBody.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Message body is required.");
        }

        OrderMessage message = new OrderMessage();
        message.setThread(thread);
        message.setSenderUser(sender);
        message.setSenderRole(role);
        message.setBody(messageBody.trim());
        messageRepository.save(message);

        managedFileService.attachSingle("order_message", message.getId(), "message_photo", map(body == null ? null : body.get("photo")));
        thread.setLastMessagePreview(trimPreview(messageBody));
        thread.setLastMessageAt(message.getCreatedAt() == null ? OffsetDateTime.now() : message.getCreatedAt());
        thread.setLastMessageSenderRole(role);
        threadRepository.save(thread);
        return messageRepository.findById(message.getId()).orElse(message);
    }

    private OrderMessageThread ensureThread(Order order) {
        return threadRepository.findByOrderId(order.getId()).orElseGet(() -> {
            OrderMessageThread thread = new OrderMessageThread();
            thread.setOrder(order);
            thread.setCustomerUser(order.getUser());
            thread.setLastMessageAt(order.getCreatedAt());
            return threadRepository.save(thread);
        });
    }

    private Map<String, Object> threadSummaryRow(OrderMessageThread thread) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", thread.getId());
        row.put("order_id", thread.getOrder().getId());
        row.put("order_slug", thread.getOrder().getSlug());
        row.put("order_status", thread.getOrder().getStatus());
        row.put("order_status_label", orderStatusLabel(thread.getOrder().getStatus()));
        row.put("order_placed_at", format(thread.getOrder().getCreatedAt()));
        row.put("customer_name", customerName(thread.getOrder()));
        row.put("most_recent_message", thread.getLastMessagePreview());
        row.put("most_recent_message_at", format(thread.getLastMessageAt()));
        return row;
    }

    private Map<String, Object> customerThreadSummaryRow(OrderMessageThread thread) {
        Map<String, Object> row = threadSummaryRow(thread);
        row.put("last_message", row.remove("most_recent_message"));
        row.put("last_message_at", row.remove("most_recent_message_at"));
        return row;
    }

    private Map<String, Object> threadDetailRow(OrderMessageThread thread) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", thread.getId());
        row.put("order", Map.of(
                "id", thread.getOrder().getId(),
                "slug", thread.getOrder().getSlug(),
                "status", thread.getOrder().getStatus(),
                "status_label", orderStatusLabel(thread.getOrder().getStatus()),
                "placed_at", format(thread.getOrder().getCreatedAt())
        ));
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("id", thread.getCustomerUser() == null ? null : thread.getCustomerUser().getId());
        customer.put("name", customerName(thread.getOrder()));
        customer.put("email", thread.getCustomerUser() == null ? null : thread.getCustomerUser().getEmail());
        row.put("customer", customer);
        row.put("messages", messageRepository.findByThreadIdOrderByCreatedAtAsc(thread.getId()).stream().map(this::messageRow).toList());
        return row;
    }

    private Map<String, Object> messageRow(OrderMessage message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", message.getId());
        row.put("body", message.getBody());
        row.put("sender_role", message.getSenderRole());
        if (message.getSenderUser() == null) {
            row.put("sender", null);
        } else {
            Map<String, Object> sender = new LinkedHashMap<>();
            sender.put("id", message.getSenderUser().getId());
            sender.put("name", message.getSenderUser().getName());
            sender.put("email", message.getSenderUser().getEmail());
            row.put("sender", sender);
        }
        row.put("created_at", format(message.getCreatedAt()));
        row.put("photo", managedFileService.findAttachedSingle("order_message", message.getId(), "message_photo"));
        return row;
    }

    private Order requireOrder(UUID orderId) {
        return orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found", "Order not found."));
    }

    private User requireUser(String email) {
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication required.");
        }
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthenticated", "User not found."));
    }

    private String customerName(Order order) {
        if (order.getUser() != null && order.getUser().getName() != null && !order.getUser().getName().isBlank()) {
            return order.getUser().getName();
        }
        if (order.getAddress() != null && order.getAddress().getName() != null && !order.getAddress().getName().isBlank()) {
            return order.getAddress().getName();
        }
        return "Customer";
    }

    private String orderStatusLabel(Integer status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (status) {
            case 0 -> "Pending";
            case 1 -> "Processing";
            case 2 -> "Completed";
            case 3 -> "Cancelled";
            default -> "Unknown";
        };
    }

    private String format(OffsetDateTime value) {
        return value == null ? null : value.format(ISO);
    }

    private String trimPreview(String value) {
        String stripped = value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (stripped.length() <= 120) {
            return stripped;
        }
        return stripped.substring(0, 117) + "...";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
