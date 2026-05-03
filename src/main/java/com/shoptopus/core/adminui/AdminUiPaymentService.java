package com.shoptopus.core.adminui;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.admin.AdminPaymentService;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.payment.Payment;
import com.shoptopus.core.payment.PaymentSource;
import com.shoptopus.core.payment.repository.PaymentRepository;
import com.shoptopus.core.store.Store;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminUiPaymentService {

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ORDER_PAYABLE_TYPE = "App\\Models\\Order";

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final AdminPaymentService adminPaymentService;
    private final AdminStoreContext adminStoreContext;

    public AdminUiPaymentService(PaymentRepository paymentRepository, OrderRepository orderRepository, AdminPaymentService adminPaymentService, AdminStoreContext adminStoreContext) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.adminPaymentService = adminPaymentService;
        this.adminStoreContext = adminStoreContext;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String search, String status, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 12 : Math.min(perPage, 100);

        List<Payment> filtered = scopedPayments().stream()
                .filter(payment -> matchesSearch(payment, search))
                .filter(payment -> matchesStatus(payment, status))
                .sorted(Comparator.comparing(Payment::getCreatedAt).reversed())
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", safePage);
        meta.put("per_page", safePerPage);
        meta.put("total", total);
        meta.put("total_pages", total == 0 ? 1 : (int) Math.ceil((double) total / safePerPage));

        return Map.of(
                "items", filtered.subList(fromIndex, toIndex).stream().map(this::listItem).toList(),
                "meta", meta,
                "filters", Map.of(
                        "search", search == null ? "" : search,
                        "status", status == null ? "all" : status,
                        "statuses", List.of(
                                Map.of("value", "all", "label", "All"),
                                Map.of("value", "pending", "label", "Pending"),
                                Map.of("value", "captured", "label", "Captured"),
                                Map.of("value", "failed", "label", "Failed"),
                                Map.of("value", "refunded", "label", "Refunded")
                        )
                )
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String id) {
        UUID paymentId;
        try {
            paymentId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Payment not found", "Payment not found");
        }

        Payment payment = paymentRepository.findById(paymentId)
                .filter(item -> item.getDeletedAt() == null)
                .filter(this::matchesActiveStore)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found", "Payment not found"));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", payment.getId());
        row.put("slug", payment.getSlug());
        row.put("amount", money(payment.getAmount()));
        row.put("amount_value", payment.getAmount());
        row.put("status", payment.getStatus());
        row.put("status_label", statusLabel(payment.getStatus()));
        row.put("type", payment.getType());
        row.put("type_label", typeLabel(payment.getType()));
        row.put("description", payment.getDescription());
        row.put("payment_ref", payment.getPaymentRef());
        row.put("method_ref", payment.getMethodRef());
        row.put("created_at", format(payment.getCreatedAt()));
        row.put("updated_at", format(payment.getUpdatedAt()));
        row.put("payable_type", payment.getPayableType());
        row.put("payable_id", payment.getPayableId());
        row.put("proof", payment.getProof());
        row.put("provider", payment.getProof() == null ? null : payment.getProof().get("provider"));
        row.put("refunded_amount", money(decimal(payment.getProof() == null ? null : payment.getProof().get("refunded_amount"))));
        row.put("refundable_amount", money(
                payment.getAmount().subtract(decimal(payment.getProof() == null ? null : payment.getProof().get("refunded_amount"))).max(BigDecimal.ZERO)
        ));
        row.put("user", payment.getUser() == null ? null : Map.of(
                "id", payment.getUser().getId(),
                "name", payment.getUser().getName(),
                "email", payment.getUser().getEmail()
        ));
        row.put("payment_source", sourceRow(payment.getPaymentSource()));
        row.put("order", orderRow(payment));
        row.put("status_options", statusOptions());
        return row;
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> updateStatus(String id, Map<String, Object> body) {
        adminPaymentService.update(id, body);
        return detail(id);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> refund(String id, Map<String, Object> body) {
        adminPaymentService.refund(id, decimal(body == null ? null : body.get("amount")));
        return detail(id);
    }

    private boolean matchesSearch(Payment payment, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase();
        return contains(payment.getSlug(), term)
                || contains(payment.getPaymentRef(), term)
                || contains(payment.getMethodRef(), term)
                || contains(payment.getDescription(), term)
                || (payment.getUser() != null && contains(payment.getUser().getEmail(), term))
                || (payment.getUser() != null && contains(payment.getUser().getName(), term));
    }

    private boolean matchesStatus(Payment payment, String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        return switch (status.toLowerCase()) {
            case "pending" -> payment.getStatus() == 0;
            case "captured" -> payment.getStatus() == 1;
            case "failed" -> payment.getStatus() == 2;
            case "refunded" -> payment.getStatus() == 3;
            default -> true;
        };
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase().contains(term);
    }

    private Map<String, Object> listItem(Payment payment) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", payment.getId());
        row.put("slug", payment.getSlug());
        row.put("amount", money(payment.getAmount()));
        row.put("status", payment.getStatus());
        row.put("status_label", statusLabel(payment.getStatus()));
        row.put("type", payment.getType());
        row.put("type_label", typeLabel(payment.getType()));
        row.put("payable_type", payment.getPayableType());
        row.put("payment_ref", payment.getPaymentRef());
        row.put("method_ref", payment.getMethodRef());
        row.put("created_at", format(payment.getCreatedAt()));
        row.put("user_name", payment.getUser() == null ? null : payment.getUser().getName());
        row.put("user_email", payment.getUser() == null ? null : payment.getUser().getEmail());
        row.put("order_slug", orderSlug(payment));
        return row;
    }

    private Map<String, Object> sourceRow(PaymentSource source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", source.getId());
        row.put("name", source.getName());
        row.put("brand", source.getBrand());
        row.put("last_four", source.getLastFour());
        row.put("exp_month", source.getExpMonth());
        row.put("exp_year", source.getExpYear());
        return row;
    }

    private Map<String, Object> orderRow(Payment payment) {
        if (!ORDER_PAYABLE_TYPE.equals(payment.getPayableType())) {
            return null;
        }
        try {
            UUID orderId = UUID.fromString(payment.getPayableId());
            Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId).orElse(null);
            if (order == null) {
                return null;
            }
            return Map.of(
                    "id", order.getId(),
                    "slug", order.getSlug(),
                    "status", order.getStatus(),
                    "total_price", order.getTotalPrice()
            );
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private List<Payment> scopedPayments() {
        if (!adminStoreContext.multiStoreEnabled()) {
            return paymentRepository.findByDeletedAtIsNull();
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        if (activeStore == null) {
            return List.of();
        }
        return paymentRepository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(activeStore.getId());
    }

    private boolean matchesActiveStore(Payment payment) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        return activeStore != null
                && payment.getStore() != null
                && activeStore.getId().equals(payment.getStore().getId());
    }

    private String orderSlug(Payment payment) {
        Map<String, Object> order = orderRow(payment);
        return order == null ? null : String.valueOf(order.get("slug"));
    }

    private String statusLabel(Integer status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (status) {
            case 0 -> "Pending";
            case 1 -> "Captured";
            case 2 -> "Failed";
            case 3 -> "Refunded";
            default -> "Status " + status;
        };
    }

    private String typeLabel(Integer type) {
        if (type == null) {
            return "Unknown";
        }
        return switch (type) {
            case 0 -> "Charge";
            case 1 -> "Authorization";
            case 2 -> "Manual";
            default -> "Type " + type;
        };
    }

    private List<Map<String, Object>> statusOptions() {
        return List.of(
                Map.of("value", 0, "label", "Pending"),
                Map.of("value", 1, "label", "Captured"),
                Map.of("value", 2, "label", "Failed"),
                Map.of("value", 3, "label", "Refunded")
        );
    }

    private String money(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return "GBP " + safe.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(Object raw) {
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private String format(java.time.OffsetDateTime value) {
        return value == null ? null : DATETIME_FORMAT.format(value);
    }
}
