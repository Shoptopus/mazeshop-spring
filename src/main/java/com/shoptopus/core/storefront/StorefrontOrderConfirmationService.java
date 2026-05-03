package com.shoptopus.core.storefront;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.common.i18n.TranslatedText;
import com.shoptopus.core.catalog.repository.ProductAttributeRepository;
import com.shoptopus.core.catalog.repository.ProductAttributeOptionRepository;
import com.shoptopus.core.invoice.repository.InvoiceRepository;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.OrderItem;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.payment.Payment;
import com.shoptopus.core.payment.repository.PaymentRepository;
import com.shoptopus.core.store.Store;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StorefrontOrderConfirmationService {

    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ProductAttributeRepository productAttributeRepository;
    private final ProductAttributeOptionRepository productAttributeOptionRepository;

    public StorefrontOrderConfirmationService(
            OrderRepository orderRepository,
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            ProductAttributeRepository productAttributeRepository,
            ProductAttributeOptionRepository productAttributeOptionRepository
    ) {
        this.orderRepository = orderRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.productAttributeRepository = productAttributeRepository;
        this.productAttributeOptionRepository = productAttributeOptionRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> confirmation(UUID orderId) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found", "Order not found"));
        var invoice = invoiceRepository.findFirstByOrderIdAndDeletedAtIsNull(orderId).orElse(null);
        List<Payment> payments = paymentRepository.findByPayableTypeAndPayableIdAndDeletedAtIsNull("App\\Models\\Order", orderId.toString());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("slug", order.getSlug());
        row.put("status", order.getStatus());
        row.put("status_label", statusLabel(order.getStatus()));
        row.put("currency", order.getCurrencyCode());
        row.put("total", money(order.getTotalPrice()));
        row.put("total_amount", order.getTotalPrice());
        row.put("created_at", order.getCreatedAt() == null ? null : order.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        row.put("store", storeRow(order.getStore()));
        row.put("address", invoice == null ? null : invoice.getAddress());
        row.put("delivery", invoice == null ? null : deliveryRow(invoice.getDeliveryType()));
        row.put("totals", invoice == null ? Map.of() : invoice.getTotals());
        row.put("voucher_code", invoice == null ? Map.of() : invoice.getVoucherCode());
        row.put("items", order.getItems().stream().map(this::itemRow).toList());
        row.put("payments", payments.stream().map(this::paymentRow).toList());
        return row;
    }

    private Map<String, Object> itemRow(OrderItem item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("name", item.getName());
        row.put("quantity", item.getAmount());
        row.put("customization_value", item.getCustomizationValue());
        row.put("unit_price", item.getUnitPrice());
        row.put("original_unit_price", item.getOriginalUnitPrice());
        row.put("unit_discount", item.getUnitDiscount());
        row.put("variant", variantRow(item));
        row.put("final_price", item.getFinalPrice());
        row.put("full_price", item.getFullPrice());
        row.put("download_urls", item.getUrls());
        return row;
    }

    private Map<String, Object> variantRow(OrderItem item) {
        if (item.getProductVariant() == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getProductVariant().getId());
        row.put("slug", item.getProductVariant().getSlug());
        row.put("description", TranslatedText.resolve(item.getProductVariant().getDescription(), java.util.Locale.ENGLISH));
        List<Map<String, Object>> attributes = new java.util.ArrayList<>();
        Map<String, Object> normalized = new LinkedHashMap<>(storefrontShopServiceResolvedVariantOptionMap(item.getProductVariant()));
        normalized.forEach((attributeIdValue, optionIdValue) -> {
            UUID optionId;
            try {
                optionId = UUID.fromString(String.valueOf(optionIdValue));
            } catch (IllegalArgumentException ex) {
                return;
            }
            productAttributeOptionRepository.findById(optionId).ifPresent(option -> {
                Map<String, Object> attributeRow = new LinkedHashMap<>();
                attributeRow.put("attribute_id", option.getProductAttribute() == null ? null : option.getProductAttribute().getId());
                attributeRow.put("attribute_name", option.getProductAttribute() == null ? null : TranslatedText.resolve(option.getProductAttribute().getName(), java.util.Locale.ENGLISH));
                attributeRow.put("option_id", option.getId());
                attributeRow.put("option_name", TranslatedText.resolve(option.getName(), java.util.Locale.ENGLISH));
                attributeRow.put("option_value", option.getValue());
                attributes.add(attributeRow);
            });
        });
        row.put("attributes", attributes);
        return row;
    }

    private Map<String, Object> storefrontShopServiceResolvedVariantOptionMap(com.shoptopus.core.catalog.ProductVariant variant) {
        Map<String, Object> map = variant.attributeOptionsAsMap();
        if (!map.isEmpty()) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((attributeRef, optionRef) -> {
                var option = resolveAttributeOption(attributeRef, optionRef);
                if (option != null && option.getProductAttribute() != null) {
                    normalized.put(option.getProductAttribute().getId().toString(), option.getId().toString());
                }
            });
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Object itemRef : variant.attributeOptionsAsList()) {
            UUID optionId;
            try {
                optionId = UUID.fromString(String.valueOf(itemRef));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            productAttributeOptionRepository.findById(optionId).ifPresent(option -> {
                if (option.getProductAttribute() != null) {
                    normalized.put(option.getProductAttribute().getId().toString(), option.getId().toString());
                }
            });
        }
        return normalized;
    }

    private com.shoptopus.core.catalog.ProductAttributeOption resolveAttributeOption(Object attributeRef, Object optionRef) {
        String attributeKey = attributeRef == null ? null : String.valueOf(attributeRef);
        String optionKey = optionRef == null ? null : String.valueOf(optionRef);
        if (optionKey == null || optionKey.isBlank()) {
            return null;
        }
        try {
            UUID optionId = UUID.fromString(optionKey);
            return productAttributeOptionRepository.findById(optionId).orElse(null);
        } catch (IllegalArgumentException ignored) {
        }
        var attribute = resolveAttribute(attributeKey);
        if (attribute == null) {
            return null;
        }
        return productAttributeOptionRepository.findByProductAttributeIdAndDeletedAtIsNullAndEnabledTrue(attribute.getId()).stream()
                .filter(option -> optionKey.equalsIgnoreCase(option.getSlug()) || optionKey.equalsIgnoreCase(option.getValue()))
                .findFirst()
                .orElse(null);
    }

    private com.shoptopus.core.catalog.ProductAttribute resolveAttribute(String attributeKey) {
        if (attributeKey == null || attributeKey.isBlank()) {
            return null;
        }
        try {
            UUID attributeId = UUID.fromString(attributeKey);
            return productAttributeRepository.findById(attributeId).orElse(null);
        } catch (IllegalArgumentException ignored) {
        }
        return productAttributeRepository.findByDeletedAtIsNullAndEnabledTrue().stream()
                .filter(attribute -> attributeKey.equalsIgnoreCase(attribute.getSlug()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> paymentRow(Payment payment) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", payment.getId());
        row.put("provider", paymentProvider(payment));
        row.put("status", payment.getStatus());
        row.put("status_label", paymentStatusLabel(payment.getStatus()));
        row.put("amount", payment.getAmount());
        row.put("payment_ref", payment.getPaymentRef());
        row.put("payment_method", paymentMethod(payment));
        return row;
    }

    private Map<String, Object> deliveryRow(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>(raw);
        row.computeIfPresent("name", (key, value) -> translatedValue(value));
        row.computeIfPresent("description", (key, value) -> translatedValue(value));
        return row;
    }

    private Object translatedValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, String> translations = (Map<String, String>) map;
            return TranslatedText.resolve(translations, java.util.Locale.ENGLISH);
        }
        return value;
    }

    private String paymentProvider(Payment payment) {
        if (payment.getProof() != null && payment.getProof().get("provider") != null) {
            return String.valueOf(payment.getProof().get("provider"));
        }
        return payment.getPaymentSource() == null ? null : payment.getPaymentSource().getName();
    }

    private String paymentMethod(Payment payment) {
        if (payment.getProof() != null) {
            Object brand = payment.getProof().get("brand");
            if (brand != null) {
                return String.valueOf(brand);
            }
            Object stripe = payment.getProof().get("stripe");
            if (stripe instanceof Map<?, ?> stripeMap) {
                Object paymentMethodId = stripeMap.get("payment_method_id");
                if (paymentMethodId != null) {
                    return "Stripe " + paymentMethodId;
                }
            }
        }
        return payment.getMethodRef();
    }

    private String paymentStatusLabel(Integer status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (status) {
            case 1 -> "Paid";
            case 2 -> "Refunded";
            default -> "Pending";
        };
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

    private String money(BigDecimal value) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return "GBP " + safe.setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> storeRow(Store store) {
        if (store == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", store.getId());
        row.put("name", store.getName());
        row.put("slug", store.getSlug());
        row.put("tagline", store.getTagline());
        row.put("logo", store.getLogo());
        row.put("rating_average", store.getRatingAverage());
        row.put("rating_count", store.getRatingCount());
        return row;
    }
}
