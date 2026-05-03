package com.shoptopus.core.admin;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.adminui.AdminStoreContext;
import com.shoptopus.core.delivery.DeliveryType;
import com.shoptopus.core.engagement.EngagementService;
import com.shoptopus.core.invoice.Invoice;
import com.shoptopus.core.invoice.repository.InvoiceRepository;
import com.shoptopus.core.mail.ShopMailService;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.payment.Payment;
import com.shoptopus.core.payment.repository.PaymentRepository;
import com.shoptopus.core.store.Store;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminOrderService {

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ORDER_PAYABLE_TYPE = "App\\Models\\Order";

    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ShopMailService shopMailService;
    private final EngagementService engagementService;
    private final AdminStoreContext adminStoreContext;

    public AdminOrderService(
            OrderRepository orderRepository,
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            ShopMailService shopMailService,
            EngagementService engagementService,
            AdminStoreContext adminStoreContext
    ) {
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.shopMailService = shopMailService;
        this.engagementService = engagementService;
        this.adminStoreContext = adminStoreContext;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list() {
        List<Map<String, Object>> data = scopedOrders().stream()
                .map(this::toListRow)
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "orders");
        response.put("data", data);
        response.put("records", data.size());
        response.put("total_records", data.size());
        response.put("next", null);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> show(String id) {
        return toDetailRow(findOrder(id));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> updateStatus(String id, Map<String, Object> body) {
        Order order = findOrder(id);
        Map<String, Object> payload = body == null ? Map.of() : body;
        if (!payload.containsKey("status")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The status field is required.");
        }
        order.setStatus(readStatus(payload.get("status")));
        orderRepository.save(order);
        String recipientEmail = order.getUser() == null ? null : order.getUser().getEmail();
        shopMailService.sendOrderStatusUpdated(recipientEmail, order.getId(), order.getSlug(), statusLabel(order.getStatus()));
        engagementService.notifyUser(
                order.getUser(),
                "order_status_updated",
                "Order status updated",
                Map.of(
                        "message", "Your order " + order.getSlug() + " is now " + statusLabel(order.getStatus()) + ".",
                        "order_id", order.getId().toString(),
                        "order_slug", order.getSlug(),
                        "status_label", statusLabel(order.getStatus())
                )
        );
        return toListRow(order);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkStatus(Map<String, Object> body) {
        List<Order> orders = requireBulkOrders(body);
        if (body == null || !body.containsKey("status")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The status field is required.");
        }
        int status = readStatus(body.get("status"));
        orders.forEach(order -> order.setStatus(status));
        orderRepository.saveAll(orders);
        orders.forEach(order -> {
            String recipientEmail = order.getUser() == null ? null : order.getUser().getEmail();
            shopMailService.sendOrderStatusUpdated(recipientEmail, order.getId(), order.getSlug(), statusLabel(order.getStatus()));
            engagementService.notifyUser(
                    order.getUser(),
                    "order_status_updated",
                    "Order status updated",
                    Map.of(
                            "message", "Your order " + order.getSlug() + " is now " + statusLabel(order.getStatus()) + ".",
                            "order_id", order.getId().toString(),
                            "order_slug", order.getSlug(),
                            "status_label", statusLabel(order.getStatus())
                    )
            );
        });
        return Map.of("ids", orders.stream().map(Order::getId).toList(), "count", orders.size());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String id) {
        Order order = findOrder(id);
        order.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        orderRepository.save(order);
        return Map.of("id", order.getId());
    }

    private Order findOrder(String id) {
        UUID orderId = parseUuid(id, "order");
        return orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .filter(this::matchesActiveStore)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not found", "Order not found."));
    }

    private List<Order> requireBulkOrders(Map<String, Object> body) {
        if (body == null || !(body.get("ids") instanceof List<?> rawIds) || rawIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The ids field is required.");
        }
        List<Order> orders = new ArrayList<>();
        int index = 0;
        for (Object rawId : rawIds) {
            final int currentIndex = index;
            UUID orderId;
            try {
                orderId = UUID.fromString(String.valueOf(rawId));
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ids." + currentIndex + " is invalid.");
            }
            orders.add(orderRepository.findByIdAndDeletedAtIsNull(orderId)
                    .filter(this::matchesActiveStore)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ids." + currentIndex + " is invalid.")));
            index++;
        }
        return orders;
    }

    private int readStatus(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The status field is invalid.");
        }
    }

    private UUID parseUuid(String rawId, String label) {
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid " + label + " id.");
        }
    }

    private Map<String, Object> toListRow(Order order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("slug", order.getSlug());
        row.put("created_at", format(order.getCreatedAt()));
        row.put("updated_at", format(order.getUpdatedAt()));
        row.put("total_price", order.getTotalPrice());
        row.put("original_price", order.getOriginalPrice());
        row.put("total_discount", order.getTotalDiscount());
        row.put("status", order.getStatus());
        row.put("user", order.getUser() == null ? "N/A" : order.getUser().getName());
        row.put("delivery_type", deliveryTypeName(order).orElse("N/A"));
        row.put("delivery_cost", order.getDeliveryCost());
        row.put("town", order.getAddress() == null ? "N/A" : order.getAddress().getTown());
        return row;
    }

    private Map<String, Object> toDetailRow(Order order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("slug", order.getSlug());
        row.put("created_at", format(order.getCreatedAt()));
        row.put("updated_at", format(order.getCreatedAt()));
        row.put("original_price", order.getOriginalPrice());
        row.put("total_price", order.getTotalPrice());
        row.put("total_discount", order.getTotalDiscount());
        row.put("subtotal", order.getSubtotal());
        row.put("delivery_cost", order.getDeliveryCost());
        row.put("address", addressRow(order));
        row.put("user", userRow(order));
        row.put("payments", paymentRows(order));
        row.put("status", order.getStatus());
        row.put("voucher_code", null);
        row.put("delivery_type", deliveryTypeDetail(order));
        row.put("products", List.of());
        row.put("event_logs", List.of());
        row.put("notes", List.of());
        row.put("invoice_access_token", null);
        return row;
    }

    private String statusLabel(Integer status) {
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

    private List<Order> scopedOrders() {
        Store activeStore = activeStore();
        if (activeStore == null) {
            return orderRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        }
        return orderRepository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(activeStore.getId());
    }

    private boolean matchesActiveStore(Order order) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = activeStore();
        return activeStore != null && order.getStore() != null && activeStore.getId().equals(order.getStore().getId());
    }

    private Store activeStore() {
        return adminStoreContext.activeStoreForCurrentRequest().orElse(null);
    }

    private Map<String, Object> addressRow(Order order) {
        if (order.getAddress() == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getAddress().getId());
        row.put("address_line_1", order.getAddress().getAddressLine1());
        row.put("town", order.getAddress().getTown());
        row.put("post_code", order.getAddress().getPostCode());
        row.put("country", order.getAddress().getCountry());
        return row;
    }

    private Map<String, Object> userRow(Order order) {
        if (order.getUser() == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getUser().getId());
        row.put("email", order.getUser().getEmail());
        row.put("name", order.getUser().getName());
        row.put("first_name", order.getUser().getFirstName());
        row.put("last_name", order.getUser().getLastName());
        return row;
    }

    private List<Map<String, Object>> paymentRows(Order order) {
        return paymentRepository.findByPayableTypeAndPayableIdAndDeletedAtIsNull(ORDER_PAYABLE_TYPE, order.getId().toString()).stream()
                .map(this::toPaymentRow)
                .toList();
    }

    private Map<String, Object> toPaymentRow(Payment payment) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", payment.getId());
        row.put("amount", payment.getAmount());
        row.put("status", payment.getStatus());
        row.put("type", payment.getType());
        row.put("description", payment.getDescription());
        row.put("payment_ref", payment.getPaymentRef());
        row.put("method_ref", payment.getMethodRef());
        return row;
    }

    private Optional<Object> deliveryTypeName(Order order) {
        DeliveryType live = order.getDeliveryType();
        if (live != null && live.getName() != null && !live.getName().isEmpty()) {
            return Optional.of(live.getName());
        }
        return invoiceRepository.findFirstByOrderIdAndDeletedAtIsNull(order.getId())
                .map(Invoice::getDeliveryType)
                .filter(snapshot -> snapshot.get("name") != null)
                .map(snapshot -> snapshot.get("name"));
    }

    private Map<String, Object> deliveryTypeDetail(Order order) {
        DeliveryType live = order.getDeliveryType();
        if (live != null) {
            return Map.of(
                    "name", live.getName() == null ? Map.of() : live.getName(),
                    "description", live.getDescription() == null ? Map.of() : live.getDescription()
            );
        }
        Optional<Invoice> invoice = invoiceRepository.findFirstByOrderIdAndDeletedAtIsNull(order.getId());
        if (invoice.isPresent()) {
            Object snapshot = invoice.get().getDeliveryType();
            if (snapshot instanceof Map<?, ?> data) {
                Object name = data.containsKey("name") ? data.get("name") : Map.of();
                Object description = data.containsKey("description") ? data.get("description") : Map.of();
                return Map.of(
                        "name", name,
                        "description", description
                );
            }
        }
        return Map.of("name", Map.of(), "description", Map.of());
    }

    private String format(OffsetDateTime value) {
        return value == null ? null : DATETIME_FORMAT.format(value);
    }
}
