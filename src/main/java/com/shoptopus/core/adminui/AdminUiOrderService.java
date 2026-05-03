package com.shoptopus.core.adminui;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.admin.AdminOrderService;
import com.shoptopus.core.catalog.ProductAttributeOption;
import com.shoptopus.core.catalog.ProductVariant;
import com.shoptopus.core.catalog.repository.ProductAttributeOptionRepository;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.invoice.Invoice;
import com.shoptopus.core.invoice.InvoiceAccessService;
import com.shoptopus.core.invoice.repository.InvoiceRepository;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.OrderItem;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.payment.Payment;
import com.shoptopus.core.payment.repository.PaymentRepository;
import com.shoptopus.core.store.Store;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminUiOrderService {

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ORDER_PAYABLE_TYPE = "App\\Models\\Order";
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceAccessService invoiceAccessService;
    private final PaymentRepository paymentRepository;
    private final ProductAttributeOptionRepository productAttributeOptionRepository;
    private final AdminOrderService adminOrderService;
    private final AdminStoreContext adminStoreContext;
    private final String storefrontBaseUrl;

    public AdminUiOrderService(
            OrderRepository orderRepository,
            InvoiceRepository invoiceRepository,
            InvoiceAccessService invoiceAccessService,
            PaymentRepository paymentRepository,
            ProductAttributeOptionRepository productAttributeOptionRepository,
            AdminOrderService adminOrderService,
            AdminStoreContext adminStoreContext,
            @Value("${app.storefront.public-base-url:http://localhost:8080}") String storefrontBaseUrl
    ) {
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceAccessService = invoiceAccessService;
        this.paymentRepository = paymentRepository;
        this.productAttributeOptionRepository = productAttributeOptionRepository;
        this.adminOrderService = adminOrderService;
        this.adminStoreContext = adminStoreContext;
        this.storefrontBaseUrl = storefrontBaseUrl;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String search, String status, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 12 : Math.min(perPage, 100);

        List<Order> filtered = scopedOrders().stream()
                .filter(order -> matchesSearch(order, search))
                .filter(order -> matchesStatus(order, status))
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", safePage);
        meta.put("per_page", safePerPage);
        meta.put("total", total);
        meta.put("total_pages", total == 0 ? 1 : (int) Math.ceil((double) total / safePerPage));

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("search", search == null ? "" : search);
        filters.put("status", status == null ? "all" : status);
        filters.put("statuses", List.of(
                Map.of("value", "all", "label", "All"),
                Map.of("value", "pending", "label", "Pending"),
                Map.of("value", "processing", "label", "Processing"),
                Map.of("value", "completed", "label", "Completed"),
                Map.of("value", "cancelled", "label", "Cancelled")
        ));

        return Map.of(
                "items", filtered.subList(fromIndex, toIndex).stream().map(this::listItem).toList(),
                "meta", meta,
                "filters", filters
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String id) {
        UUID orderId;
        try {
            orderId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Order not found", "Order not found");
        }

        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .filter(this::matchesActiveStore)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found", "Order not found"));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("slug", order.getSlug());
        row.put("status", order.getStatus());
        row.put("status_label", statusLabel(order.getStatus()));
        row.put("created_at", format(order.getCreatedAt()));
        row.put("updated_at", format(order.getUpdatedAt()));
        row.put("currency_code", order.getCurrencyCode());
        row.put("totals", Map.of(
                "original_price", money(order.getOriginalPrice()),
                "subtotal", money(order.getSubtotal()),
                "delivery_cost", money(order.getDeliveryCost()),
                "discount", money(order.getTotalDiscount()),
                "total_price", money(order.getTotalPrice())
        ));
        row.put("customer", customer(order));
        row.put("address", address(order));
        row.put("delivery", delivery(order));
        row.put("payments", paymentRepository.findByPayableTypeAndPayableIdAndDeletedAtIsNull(ORDER_PAYABLE_TYPE, order.getId().toString()).stream()
                .map(this::paymentRow)
                .toList());
        row.put("items", order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getCreatedAt))
                .map(this::itemRow)
                .toList());
        row.put("metrics", Map.of(
                "items", order.getItems().size(),
                "payments", paymentRepository.findByPayableTypeAndPayableIdAndDeletedAtIsNull(ORDER_PAYABLE_TYPE, order.getId().toString()).size()
        ));
        row.put("invoice", invoiceDetail(order));
        row.put("status_options", statusOptions());
        return row;
    }

    private Map<String, Object> invoiceDetail(Order order) {
        return invoiceRepository.findFirstByOrderIdAndDeletedAtIsNull(order.getId())
                .map(invoice -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", invoice.getId());
                    row.put("slug", invoice.getSlug());
                    row.put("download_url", invoiceAccessService.findLatestActiveTokenForInvoice(invoice.getId())
                            .map(token -> storefrontBaseUrl.replaceAll("/+$", "") + "/api/storefront/invoice/" + token.getToken())
                            .orElse(null));
                    return row;
                })
                .orElse(null);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> updateStatus(String id, Map<String, Object> body) {
        adminOrderService.updateStatus(id, body);
        return detail(id);
    }

    private boolean matchesSearch(Order order, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase();
        return (order.getSlug() != null && order.getSlug().toLowerCase().contains(term))
                || (order.getUser() != null && order.getUser().getEmail() != null && order.getUser().getEmail().toLowerCase().contains(term))
                || (order.getUser() != null && order.getUser().getName() != null && order.getUser().getName().toLowerCase().contains(term));
    }

    private List<Order> scopedOrders() {
        if (!adminStoreContext.multiStoreEnabled()) {
            return orderRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        if (activeStore == null) {
            return List.of();
        }
        return orderRepository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(activeStore.getId());
    }

    private boolean matchesActiveStore(Order order) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        return activeStore != null
                && order.getStore() != null
                && activeStore.getId().equals(order.getStore().getId());
    }

    private boolean matchesStatus(Order order, String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        return switch (status.toLowerCase()) {
            case "pending" -> order.getStatus() == 0;
            case "processing" -> order.getStatus() == 1;
            case "completed" -> order.getStatus() == 2;
            case "cancelled" -> order.getStatus() == 3;
            default -> true;
        };
    }

    private Map<String, Object> listItem(Order order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("slug", order.getSlug());
        row.put("status", order.getStatus());
        row.put("status_label", statusLabel(order.getStatus()));
        row.put("created_at", format(order.getCreatedAt()));
        row.put("total_price", money(order.getTotalPrice()));
        row.put("delivery_type", deliveryName(order));
        row.put("customer_name", order.getUser() == null ? "Guest" : order.getUser().getName());
        row.put("customer_email", order.getUser() == null ? null : order.getUser().getEmail());
        row.put("town", order.getAddress() == null ? null : order.getAddress().getTown());
        row.put("items", order.getItems().size());
        return row;
    }

    private Map<String, Object> customer(Order order) {
        if (order.getUser() == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getUser().getId());
        row.put("name", order.getUser().getName());
        row.put("email", order.getUser().getEmail());
        row.put("first_name", order.getUser().getFirstName());
        row.put("last_name", order.getUser().getLastName());
        return row;
    }

    private Map<String, Object> address(Order order) {
        if (order.getAddress() == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("address_line_1", order.getAddress().getAddressLine1());
        row.put("address_line_2", order.getAddress().getAddressLine2());
        row.put("town", order.getAddress().getTown());
        row.put("post_code", order.getAddress().getPostCode());
        row.put("country", order.getAddress().getCountry());
        return row;
    }

    private Map<String, Object> delivery(Order order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("cost", money(order.getDeliveryCost()));
        row.put("type_name", deliveryName(order));
        row.put("type_detail", deliveryDetail(order));
        return row;
    }

    private Map<String, Object> paymentRow(Payment payment) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", payment.getId());
        row.put("amount", money(payment.getAmount()));
        row.put("status", payment.getStatus());
        row.put("status_label", paymentStatusLabel(payment.getStatus()));
        row.put("type", payment.getType());
        row.put("description", payment.getDescription());
        row.put("payment_ref", payment.getPaymentRef());
        row.put("method_ref", payment.getMethodRef());
        return row;
    }

    private Map<String, Object> itemRow(OrderItem item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("name", item.getName());
        row.put("quantity", item.getAmount());
        row.put("unit_price", money(item.getUnitPrice()));
        row.put("final_price", money(item.getFinalPrice()));
        row.put("discount", money(item.getTotalDiscount()));
        row.put("customization_value", item.getCustomizationValue());
        row.put("returned", item.isReturned());
        row.put("sku", item.getProductVariant() == null ? null : item.getProductVariant().getSku());
        row.put("variant", variantRow(item.getProductVariant()));
        return row;
    }

    private Map<String, Object> variantRow(ProductVariant variant) {
        if (variant == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", variant.getId());
        row.put("slug", variant.getSlug());
        row.put("sku", variant.getSku());
        row.put("attribute_option_labels", variant.attributeOptionsAsList().stream()
                .map(this::attributeOptionLabel)
                .filter(label -> label != null && !label.isBlank())
                .toList());
        return row;
    }

    private String attributeOptionLabel(Object rawId) {
        if (rawId == null) {
            return null;
        }
        try {
            UUID optionId = UUID.fromString(String.valueOf(rawId));
            ProductAttributeOption option = productAttributeOptionRepository.findById(optionId).orElse(null);
            if (option == null) {
                return String.valueOf(rawId);
            }
            String attributeName = option.getProductAttribute() == null ? "Attribute" : translated(option.getProductAttribute().getName());
            return attributeName + ": " + translated(option.getName());
        } catch (IllegalArgumentException ex) {
            return String.valueOf(rawId);
        }
    }

    private String deliveryName(Order order) {
        if (order.getDeliveryType() != null && order.getDeliveryType().getName() != null && !order.getDeliveryType().getName().isEmpty()) {
            return order.getDeliveryType().getName().getOrDefault("en", order.getDeliveryType().getName().values().stream().findFirst().orElse("Delivery"));
        }
        return invoiceRepository.findFirstByOrderIdAndDeletedAtIsNull(order.getId())
                .map(Invoice::getDeliveryType)
                .map(snapshot -> snapshot.get("name"))
                .map(this::deliverySnapshotLabel)
                .orElse("Delivery");
    }

    private Object deliveryDetail(Order order) {
        if (order.getDeliveryType() != null) {
            return Map.of(
                    "name", order.getDeliveryType().getName() == null ? Map.of() : order.getDeliveryType().getName(),
                    "description", order.getDeliveryType().getDescription() == null ? Map.of() : order.getDeliveryType().getDescription()
            );
        }
        Optional<Invoice> invoice = invoiceRepository.findFirstByOrderIdAndDeletedAtIsNull(order.getId());
        if (invoice.isPresent()) {
            return invoice.get().getDeliveryType();
        }
        return Map.of();
    }

    private String deliverySnapshotLabel(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object english = map.get("en");
            if (english != null) {
                return String.valueOf(english);
            }
            return map.values().stream().findFirst().map(String::valueOf).orElse("Delivery");
        }
        return value == null ? "Delivery" : String.valueOf(value);
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
            default -> "Status " + status;
        };
    }

    private List<Map<String, Object>> statusOptions() {
        return List.of(
                Map.of("value", 0, "label", "Pending"),
                Map.of("value", 1, "label", "Processing"),
                Map.of("value", 2, "label", "Completed"),
                Map.of("value", 3, "label", "Cancelled")
        );
    }

    private String paymentStatusLabel(Integer status) {
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

    private String money(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return "GBP " + safe.setScale(2, RoundingMode.HALF_UP);
    }

    private String translated(Map<String, String> value) {
        return TranslatedText.resolve(value, DEFAULT_LOCALE);
    }

    private String format(java.time.OffsetDateTime value) {
        return value == null ? null : DATETIME_FORMAT.format(value);
    }
}
