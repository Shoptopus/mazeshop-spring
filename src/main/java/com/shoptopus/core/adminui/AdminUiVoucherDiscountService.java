package com.shoptopus.core.adminui;

import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductCategory;
import com.shoptopus.core.admin.AdminDiscountRuleService;
import com.shoptopus.core.admin.AdminVoucherCodeService;
import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.discount.DiscountRule;
import com.shoptopus.core.discount.repository.DiscountRuleRepository;
import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.voucher.VoucherCode;
import com.shoptopus.core.voucher.repository.VoucherCodeRepository;
import com.shoptopus.core.catalog.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminUiVoucherDiscountService {

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<Integer> USED_ORDER_STATUSES = List.of(2, 3, 4, 5, 6);

    private final VoucherCodeRepository voucherCodeRepository;
    private final DiscountRuleRepository discountRuleRepository;
    private final OrderRepository orderRepository;
    private final AdminVoucherCodeService adminVoucherCodeService;
    private final AdminDiscountRuleService adminDiscountRuleService;
    private final AdminStoreContext adminStoreContext;
    private final ProductRepository productRepository;

    public AdminUiVoucherDiscountService(
            VoucherCodeRepository voucherCodeRepository,
            DiscountRuleRepository discountRuleRepository,
            OrderRepository orderRepository,
            AdminVoucherCodeService adminVoucherCodeService,
            AdminDiscountRuleService adminDiscountRuleService,
            AdminStoreContext adminStoreContext,
            ProductRepository productRepository
    ) {
        this.voucherCodeRepository = voucherCodeRepository;
        this.discountRuleRepository = discountRuleRepository;
        this.orderRepository = orderRepository;
        this.adminVoucherCodeService = adminVoucherCodeService;
        this.adminDiscountRuleService = adminDiscountRuleService;
        this.adminStoreContext = adminStoreContext;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> vouchers(String search, String status, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 12 : Math.min(perPage, 100);

        List<VoucherCode> filtered = scopedVouchers().stream()
                .filter(voucher -> matchesVoucherSearch(voucher, search))
                .filter(voucher -> matchesVoucherStatus(voucher, status))
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        return Map.of(
                "items", filtered.subList(fromIndex, toIndex).stream().map(this::voucherListItem).toList(),
                "meta", pageMeta(safePage, safePerPage, total),
                "filters", Map.of(
                        "search", search == null ? "" : search,
                        "status", status == null ? "all" : status,
                        "statuses", statusOptions()
                )
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> voucher(String id) {
        VoucherCode voucher = findVoucher(id);
        List<Map<String, Object>> orders = orderRepository.findAll().stream()
                .filter(order -> voucher.getId().equals(order.getVoucherCodeId()))
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .limit(5)
                .map(order -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", order.getId());
                    row.put("slug", order.getSlug());
                    row.put("status", order.getStatus());
                    row.put("total_price", order.getTotalPrice());
                    return row;
                })
                .toList();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", voucher.getId());
        row.put("code", voucher.getCode());
        row.put("name", voucher.getName());
        row.put("type", voucher.getType());
        row.put("type_label", voucherTypeLabel(voucher.getType()));
        row.put("amount", voucher.getAmount());
        row.put("value", voucherValue(voucher));
        row.put("enabled", voucher.isEnabled());
        row.put("status", voucherStatus(voucher));
        row.put("status_label", voucherStatusLabel(voucher));
        row.put("valid_from", format(voucher.getValidFrom()));
        row.put("valid_until", format(voucher.getValidUntil()));
        row.put("used_count", usedCount(voucher.getId()));
        row.put("recent_orders", orders);
        row.put("form_options", voucherFormOptions());
        return row;
    }

    public Map<String, Object> voucherFormOptions() {
        return Map.of(
                "types", List.of(
                        Map.of("value", 1, "label", "Percentage"),
                        Map.of("value", 2, "label", "Fixed amount")
                ),
                "activation_periods", List.of(
                        Map.of("value", 0, "label", "1 day"),
                        Map.of("value", 1, "label", "1 week"),
                        Map.of("value", 2, "label", "1 month"),
                        Map.of("value", 3, "label", "1 year")
                )
        );
    }

    public Map<String, Object> createVoucher(Map<String, Object> body) {
        Map<String, Object> created = adminVoucherCodeService.create(body);
        scopedVoucher(String.valueOf(created.get("id")));
        return voucher(String.valueOf(created.get("id")));
    }

    public Map<String, Object> updateVoucher(String id, Map<String, Object> body) {
        scopedVoucher(id);
        adminVoucherCodeService.update(id, body);
        scopedVoucher(id);
        return voucher(id);
    }

    public Map<String, Object> activateVoucher(String id, Map<String, Object> body) {
        scopedVoucher(id);
        adminVoucherCodeService.bulkActivate(Map.of(
                "ids", List.of(id),
                "period", body == null ? null : body.get("period")
        ));
        return voucher(id);
    }

    public Map<String, Object> deleteVoucher(String id) {
        scopedVoucher(id);
        adminVoucherCodeService.delete(id);
        return Map.of("id", id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> discountRules(String search, String status, Integer page, Integer perPage) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePerPage = perPage == null || perPage < 1 ? 12 : Math.min(perPage, 100);

        List<DiscountRule> filtered = scopedRules().stream()
                .filter(rule -> matchesRuleSearch(rule, search))
                .filter(rule -> matchesRuleStatus(rule, status))
                .toList();

        int total = filtered.size();
        int fromIndex = Math.min((safePage - 1) * safePerPage, total);
        int toIndex = Math.min(fromIndex + safePerPage, total);

        return Map.of(
                "items", filtered.subList(fromIndex, toIndex).stream().map(this::ruleListItem).toList(),
                "meta", pageMeta(safePage, safePerPage, total),
                "filters", Map.of(
                        "search", search == null ? "" : search,
                        "status", status == null ? "all" : status,
                        "statuses", statusOptions()
                )
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> discountRule(String id) {
        DiscountRule rule = findRule(id);
        List<Map<String, Object>> products = rule.getProducts().stream()
                .sorted(Comparator.comparing(Product::getCreatedAt))
                .map(product -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", product.getId());
                    item.put("name", displayName(product.getName()));
                    item.put("slug", product.getSlug());
                    return item;
                })
                .toList();
        List<Map<String, Object>> categories = rule.getCategories().stream()
                .sorted(Comparator.comparing(ProductCategory::getCreatedAt))
                .map(category -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", category.getId());
                    item.put("name", displayName(category.getName()));
                    item.put("slug", category.getSlug());
                    return item;
                })
                .toList();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rule.getId());
        row.put("name", rule.getName());
        row.put("name_display", displayName(rule.getName()));
        row.put("type", rule.getType());
        row.put("type_label", voucherTypeLabel(rule.getType()));
        row.put("amount_value", rule.getAmount());
        row.put("amount", displayValue(rule.getType(), rule.getAmount()));
        row.put("enabled", rule.isEnabled());
        row.put("valid", isRuleValid(rule));
        row.put("status_label", ruleStatusLabel(rule));
        row.put("valid_from", format(rule.getValidFrom()));
        row.put("valid_until", format(rule.getValidUntil()));
        row.put("products", products);
        row.put("categories", categories);
        row.put("form_options", discountRuleFormOptions(id));
        return row;
    }

    public Map<String, Object> discountRuleFormOptions() {
        return Map.of(
                "types", List.of(
                        Map.of("value", 1, "label", "Percentage"),
                        Map.of("value", 2, "label", "Fixed amount")
                ),
                "activation_periods", List.of(
                        Map.of("value", 0, "label", "1 day"),
                        Map.of("value", 1, "label", "1 week"),
                        Map.of("value", 2, "label", "1 month"),
                        Map.of("value", 3, "label", "1 year")
                )
        );
    }

    public Map<String, Object> discountRuleFormOptions(String id) {
        Map<String, Object> base = new LinkedHashMap<>(discountRuleFormOptions());
        base.put("available_products", availableProducts(id));
        base.put("available_categories", availableCategories(id));
        return base;
    }

    public Map<String, Object> createDiscountRule(Map<String, Object> body) {
        Map<String, Object> created = adminDiscountRuleService.create(body);
        scopedRule(String.valueOf(created.get("id")));
        return discountRule(String.valueOf(created.get("id")));
    }

    public Map<String, Object> updateDiscountRule(String id, Map<String, Object> body) {
        scopedRule(id);
        adminDiscountRuleService.update(id, body);
        scopedRule(id);
        return discountRule(id);
    }

    public Map<String, Object> activateDiscountRule(String id, Map<String, Object> body) {
        scopedRule(id);
        adminDiscountRuleService.bulkActivate(Map.of(
                "ids", List.of(id),
                "period", body == null ? null : body.get("period")
        ));
        return discountRule(id);
    }

    public Map<String, Object> deleteDiscountRule(String id) {
        scopedRule(id);
        adminDiscountRuleService.delete(id);
        return Map.of("id", id);
    }

    public Map<String, Object> attachDiscountRuleProduct(String id, String productId) {
        scopedRule(id);
        adminDiscountRuleService.attachProduct(id, productId);
        return discountRule(id);
    }

    public Map<String, Object> detachDiscountRuleProduct(String id, String productId) {
        scopedRule(id);
        adminDiscountRuleService.detachProduct(id, productId);
        return discountRule(id);
    }

    public Map<String, Object> attachDiscountRuleCategory(String id, String categoryId) {
        scopedRule(id);
        adminDiscountRuleService.attachCategory(id, categoryId);
        return discountRule(id);
    }

    public Map<String, Object> detachDiscountRuleCategory(String id, String categoryId) {
        scopedRule(id);
        adminDiscountRuleService.detachCategory(id, categoryId);
        return discountRule(id);
    }

    private List<Map<String, Object>> availableProducts(String id) {
        Object data = adminDiscountRuleService.availableProducts(id).get("data");
        if (data instanceof List<?> list) {
            Set<String> allowedProductIds = adminStoreContext.multiStoreEnabled()
                    ? productIdsForActiveStore()
                    : null;
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .filter(item -> allowedProductIds == null || allowedProductIds.contains(String.valueOf(item.get("id"))))
                    .map(item -> Map.of(
                            "id", item.get("id"),
                            "name", displayName(castStringMap(item.get("name"))),
                            "slug", item.get("slug")
                    ))
                    .toList();
        }
        return List.of();
    }

    private List<Map<String, Object>> availableCategories(String id) {
        Object data = adminDiscountRuleService.availableCategories(id).get("data");
        if (data instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .map(item -> Map.of(
                            "id", item.get("id"),
                            "name", displayName(castStringMap(item.get("name"))),
                            "slug", item.get("slug")
                    ))
                    .toList();
        }
        return List.of();
    }

    private Map<String, Object> voucherListItem(VoucherCode voucher) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", voucher.getId());
        row.put("code", voucher.getCode());
        row.put("name", voucher.getName());
        row.put("type_label", voucherTypeLabel(voucher.getType()));
        row.put("value", voucherValue(voucher));
        row.put("status", voucherStatus(voucher));
        row.put("status_label", voucherStatusLabel(voucher));
        row.put("used_count", usedCount(voucher.getId()));
        row.put("valid_until", format(voucher.getValidUntil()));
        row.put("created_at", format(voucher.getCreatedAt()));
        return row;
    }

    private Map<String, Object> ruleListItem(DiscountRule rule) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rule.getId());
        row.put("name", displayName(rule.getName()));
        row.put("type_label", voucherTypeLabel(rule.getType()));
        row.put("amount", displayValue(rule.getType(), rule.getAmount()));
        row.put("enabled", rule.isEnabled());
        row.put("valid", isRuleValid(rule));
        row.put("status_label", ruleStatusLabel(rule));
        row.put("products_count", rule.getProducts().size());
        row.put("categories_count", rule.getCategories().size());
        row.put("valid_until", format(rule.getValidUntil()));
        row.put("created_at", format(rule.getCreatedAt()));
        return row;
    }

    private Map<String, Object> pageMeta(int page, int perPage, int total) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", page);
        meta.put("per_page", perPage);
        meta.put("total", total);
        meta.put("total_pages", total == 0 ? 1 : (int) Math.ceil((double) total / perPage));
        return meta;
    }

    private List<Map<String, Object>> statusOptions() {
        return List.of(
                Map.of("value", "all", "label", "All"),
                Map.of("value", "active", "label", "Active"),
                Map.of("value", "scheduled", "label", "Scheduled"),
                Map.of("value", "expired", "label", "Expired"),
                Map.of("value", "disabled", "label", "Disabled")
        );
    }

    private VoucherCode findVoucher(String id) {
        try {
            return voucherCodeRepository.findByIdAndDeletedAtIsNull(UUID.fromString(id))
                    .filter(this::matchesActiveStore)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Voucher not found", "Voucher not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Voucher not found", "Voucher not found");
        }
    }

    private DiscountRule findRule(String id) {
        try {
            return discountRuleRepository.findByIdAndDeletedAtIsNull(UUID.fromString(id))
                    .filter(this::matchesActiveStore)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Discount rule not found", "Discount rule not found"));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Discount rule not found", "Discount rule not found");
        }
    }

    private boolean matchesVoucherSearch(VoucherCode voucher, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return contains(voucher.getCode(), term) || contains(voucher.getName(), term);
    }

    private boolean matchesRuleSearch(DiscountRule rule, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return displayName(rule.getName()).toLowerCase(Locale.ROOT).contains(term);
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(term);
    }

    private boolean matchesVoucherStatus(VoucherCode voucher, String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "active" -> voucherStatus(voucher) == 1;
            case "scheduled" -> voucherStatus(voucher) == 2;
            case "expired" -> voucherStatus(voucher) == 3;
            case "disabled" -> voucherStatus(voucher) == 0;
            default -> true;
        };
    }

    private boolean matchesRuleStatus(DiscountRule rule, String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "active" -> isRuleValid(rule) && rule.isEnabled();
            case "scheduled" -> rule.getValidFrom() != null && rule.getValidFrom().isAfter(OffsetDateTime.now());
            case "expired" -> rule.getValidUntil() != null && rule.getValidUntil().isBefore(OffsetDateTime.now());
            case "disabled" -> !rule.isEnabled();
            default -> true;
        };
    }

    private long usedCount(UUID voucherCodeId) {
        return orderRepository.findAll().stream()
                .filter(order -> voucherCodeId.equals(order.getVoucherCodeId()))
                .filter(order -> USED_ORDER_STATUSES.contains(order.getStatus()))
                .count();
    }

    private int voucherStatus(VoucherCode voucher) {
        if (!voucher.isEnabled()) {
            return 0;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (voucher.getValidFrom() != null && voucher.getValidUntil() != null) {
            if (voucher.getValidFrom().isBefore(now) && voucher.getValidUntil().isAfter(now)) {
                return 1;
            }
            if (voucher.getValidFrom().isAfter(now)) {
                return 2;
            }
            if (voucher.getValidUntil().isBefore(now)) {
                return 3;
            }
        }
        return 0;
    }

    private String voucherStatusLabel(VoucherCode voucher) {
        return switch (voucherStatus(voucher)) {
            case 1 -> "Active";
            case 2 -> "Scheduled";
            case 3 -> "Expired";
            default -> "Disabled";
        };
    }

    private boolean isRuleValid(DiscountRule rule) {
        if (!rule.isEnabled()) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (rule.getValidFrom() != null && rule.getValidFrom().isAfter(now)) {
            return false;
        }
        if (rule.getValidUntil() != null && rule.getValidUntil().isBefore(now)) {
            return false;
        }
        return true;
    }

    private String ruleStatusLabel(DiscountRule rule) {
        if (!rule.isEnabled()) {
            return "Disabled";
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (rule.getValidFrom() != null && rule.getValidFrom().isAfter(now)) {
            return "Scheduled";
        }
        if (rule.getValidUntil() != null && rule.getValidUntil().isBefore(now)) {
            return "Expired";
        }
        return "Active";
    }

    private String voucherTypeLabel(Integer type) {
        if (type == null) {
            return "Unknown";
        }
        return switch (type) {
            case 1 -> "Percentage";
            case 2 -> "Fixed amount";
            default -> "Type " + type;
        };
    }

    private String voucherValue(VoucherCode voucher) {
        return displayValue(voucher.getType(), voucher.getAmount());
    }

    private String displayValue(Integer type, BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount;
        if (type != null && type == 1) {
            return safe.stripTrailingZeros().toPlainString() + "%";
        }
        return "GBP " + safe.setScale(2, RoundingMode.HALF_UP);
    }

    private String displayName(Map<String, String> name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (name.containsKey("en")) {
            return String.valueOf(name.get("en"));
        }
        return name.values().stream().findFirst().map(String::valueOf).orElse("");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> castStringMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item == null ? null : String.valueOf(item)));
            return result;
        }
        return Map.of();
    }

    private String format(OffsetDateTime value) {
        return value == null ? null : DATETIME_FORMAT.format(value);
    }

    private VoucherCode scopedVoucher(String id) {
        VoucherCode voucher = findVoucher(id);
        if (adminStoreContext.multiStoreEnabled() && voucher.getStore() == null) {
            voucher.setStore(adminStoreContext.activeStoreForCurrentRequest()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Store not found", "No active store is available.")));
            return voucherCodeRepository.save(voucher);
        }
        return voucher;
    }

    private DiscountRule scopedRule(String id) {
        DiscountRule rule = findRule(id);
        if (adminStoreContext.multiStoreEnabled() && rule.getStore() == null) {
            rule.setStore(adminStoreContext.activeStoreForCurrentRequest()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Store not found", "No active store is available.")));
            return discountRuleRepository.save(rule);
        }
        return rule;
    }

    private Set<String> productIdsForActiveStore() {
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        if (activeStore == null) {
            return Set.of();
        }
        return productRepository.findAll().stream()
                .filter(product -> product.getDeletedAt() == null)
                .filter(product -> product.getStore() != null)
                .filter(product -> activeStore.getId().equals(product.getStore().getId()))
                .map(product -> product.getId().toString())
                .collect(Collectors.toSet());
    }

    private boolean matchesActiveStore(VoucherCode voucher) {
        return matchesActiveStore(voucher.getStore());
    }

    private boolean matchesActiveStore(DiscountRule rule) {
        return matchesActiveStore(rule.getStore());
    }

    private List<VoucherCode> scopedVouchers() {
        if (!adminStoreContext.multiStoreEnabled()) {
            return voucherCodeRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        if (activeStore == null) {
            return List.of();
        }
        return voucherCodeRepository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(activeStore.getId());
    }

    private List<DiscountRule> scopedRules() {
        if (!adminStoreContext.multiStoreEnabled()) {
            return discountRuleRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        if (activeStore == null) {
            return List.of();
        }
        return discountRuleRepository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(activeStore.getId());
    }

    private boolean matchesActiveStore(Store store) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = adminStoreContext.activeStoreForCurrentRequest().orElse(null);
        return activeStore != null && store != null && activeStore.getId().equals(store.getId());
    }
}
