package com.shoptopus.core.admin;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.adminui.AdminStoreContext;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.voucher.VoucherCode;
import com.shoptopus.core.voucher.repository.VoucherCodeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminVoucherCodeService {

    private static final List<Integer> USED_ORDER_STATUSES = List.of(2, 3, 4, 5, 6);

    private final VoucherCodeRepository voucherCodeRepository;
    private final OrderRepository orderRepository;
    private final AdminStoreContext adminStoreContext;

    public AdminVoucherCodeService(VoucherCodeRepository voucherCodeRepository, OrderRepository orderRepository, AdminStoreContext adminStoreContext) {
        this.voucherCodeRepository = voucherCodeRepository;
        this.orderRepository = orderRepository;
        this.adminStoreContext = adminStoreContext;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list() {
        List<Map<String, Object>> data = scopedVouchers().stream()
                .map(this::toListRow)
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "voucher-codes");
        response.put("data", data);
        response.put("records", data.size());
        response.put("total_records", data.size());
        response.put("next", null);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> show(String id) {
        return toDetailRow(findVoucherCode(id));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(Map<String, Object> body) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        VoucherCode voucherCode = new VoucherCode();
        voucherCode.setType(readRequiredType(payload));
        voucherCode.setAmount(readRequiredAmount(payload));
        voucherCode.setValidFrom(readRequiredDate(payload, "valid_from"));
        voucherCode.setValidUntil(readRequiredDate(payload, "valid_until"));
        voucherCode.setEnabled(readBoolean(payload, "enabled").orElse(true));
        voucherCode.setCode(optionalString(payload, "code").orElseGet(this::generateCode));
        voucherCode.setName(optionalString(payload, "name").orElse(voucherCode.getCode()));
        voucherCode.setSlug(optionalString(payload, "slug").orElse("voucher-" + UUID.randomUUID()));
        voucherCode.setStore(activeStore());
        voucherCodeRepository.save(voucherCode);
        return toListRow(voucherCode);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String id, Map<String, Object> body) {
        VoucherCode voucherCode = findVoucherCode(id);
        Map<String, Object> payload = body == null ? Map.of() : body;
        voucherCode.setType(readRequiredType(payload));
        if (payload.containsKey("amount")) {
            voucherCode.setAmount(readDecimal(payload.get("amount"), "The amount must be a number."));
        }
        if (payload.containsKey("valid_from")) {
            voucherCode.setValidFrom(readDate(payload.get("valid_from"), "The valid_from is not a valid date."));
        }
        if (payload.containsKey("valid_until")) {
            voucherCode.setValidUntil(readDate(payload.get("valid_until"), "The valid_until is not a valid date."));
        }
        if (payload.containsKey("enabled")) {
            voucherCode.setEnabled(readBoolean(payload, "enabled").orElse(voucherCode.isEnabled()));
        }
        optionalString(payload, "code").ifPresent(voucherCode::setCode);
        optionalString(payload, "name").ifPresent(voucherCode::setName);
        voucherCodeRepository.save(voucherCode);
        return toListRow(voucherCode);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String id) {
        VoucherCode voucherCode = findVoucherCode(id);
        voucherCode.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        voucherCodeRepository.save(voucherCode);
        return Map.of("id", voucherCode.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkExpire(Map<String, Object> body) {
        List<VoucherCode> vouchers = requireBulkVoucherCodes(body);
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        vouchers.forEach(voucher -> voucher.setValidUntil(now));
        voucherCodeRepository.saveAll(vouchers);
        return bulkResult(vouchers);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkStart(Map<String, Object> body) {
        List<VoucherCode> vouchers = requireBulkVoucherCodes(body);
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        vouchers.forEach(voucher -> voucher.setValidFrom(now));
        voucherCodeRepository.saveAll(vouchers);
        return bulkResult(vouchers);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkActivate(Map<String, Object> body) {
        List<VoucherCode> vouchers = requireBulkVoucherCodes(body);
        if (body == null || body.get("period") == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The period field is required.");
        }
        int period;
        try {
            period = Integer.parseInt(String.valueOf(body.get("period")));
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The period field is invalid.");
        }
        OffsetDateTime validFrom = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime validUntil = validFrom.withHour(23).withMinute(59).withSecond(59).withNano(0);
        validUntil = switch (period) {
            case 0 -> validUntil.plusDays(1);
            case 1 -> validUntil.plusWeeks(1);
            case 2 -> validUntil.plusMonths(1);
            case 3 -> validUntil.plusYears(1);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The period field is invalid.");
        };
        for (VoucherCode voucher : vouchers) {
            voucher.setEnabled(true);
            voucher.setValidFrom(validFrom);
            voucher.setValidUntil(validUntil);
        }
        voucherCodeRepository.saveAll(vouchers);
        return bulkResult(vouchers);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> bulkDelete(Map<String, Object> body) {
        List<VoucherCode> vouchers = requireBulkVoucherCodes(body);
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        vouchers.forEach(voucher -> voucher.setDeletedAt(now));
        voucherCodeRepository.saveAll(vouchers);
        return bulkResult(vouchers);
    }

    private VoucherCode findVoucherCode(String id) {
        UUID voucherId;
        try {
            voucherId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ids.0 is invalid.");
        }
        return voucherCodeRepository.findByIdAndDeletedAtIsNull(voucherId)
                .filter(this::matchesActiveStore)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Not found", "Voucher code not found."));
    }

    private List<VoucherCode> requireBulkVoucherCodes(Map<String, Object> body) {
        if (body == null || !(body.get("ids") instanceof List<?> rawIds) || rawIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The ids field is required.");
        }
        List<VoucherCode> vouchers = new ArrayList<>();
        int index = 0;
        for (Object rawId : rawIds) {
            final int currentIndex = index;
            UUID id;
            try {
                id = UUID.fromString(String.valueOf(rawId));
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ids." + currentIndex + " is invalid.");
            }
            vouchers.add(voucherCodeRepository.findByIdAndDeletedAtIsNull(id)
                    .filter(this::matchesActiveStore)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected ids." + currentIndex + " is invalid.")));
            index++;
        }
        return vouchers;
    }

    private Map<String, Object> bulkResult(List<VoucherCode> vouchers) {
        return Map.of("ids", vouchers.stream().map(VoucherCode::getId).toList(), "count", vouchers.size());
    }

    private Map<String, Object> toListRow(VoucherCode voucherCode) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", voucherCode.getId());
        row.put("type", voucherCode.getType());
        row.put("amount", voucherCode.getAmount());
        row.put("value", displayValue(voucherCode));
        row.put("code", voucherCode.getCode());
        row.put("valid_from", voucherCode.getValidFrom());
        row.put("valid_until", voucherCode.getValidUntil());
        row.put("used", usedCount(voucherCode.getId()));
        row.put("enabled", voucherCode.isEnabled());
        row.put("status", status(voucherCode));
        return row;
    }

    private Map<String, Object> toDetailRow(VoucherCode voucherCode) {
        Map<String, Object> row = new LinkedHashMap<>(toListRow(voucherCode));
        row.put("orders", List.of());
        row.put("notes", List.of());
        return row;
    }

    private long usedCount(UUID voucherCodeId) {
        return orderRepository.findByDeletedAtIsNull().stream()
                .filter(order -> voucherCodeId.equals(order.getVoucherCodeId()))
                .filter(order -> USED_ORDER_STATUSES.contains(order.getStatus()))
                .count();
    }

    private List<VoucherCode> scopedVouchers() {
        Store activeStore = activeStore();
        if (activeStore == null) {
            return voucherCodeRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        }
        return voucherCodeRepository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(activeStore.getId());
    }

    private boolean matchesActiveStore(VoucherCode voucherCode) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = activeStore();
        return activeStore != null
                && voucherCode.getStore() != null
                && activeStore.getId().equals(voucherCode.getStore().getId());
    }

    private Store activeStore() {
        return adminStoreContext.activeStoreForCurrentRequest().orElse(null);
    }

    private int status(VoucherCode voucherCode) {
        if (!voucherCode.isEnabled()) {
            return 0;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (voucherCode.getValidFrom() != null && voucherCode.getValidUntil() != null) {
            if (voucherCode.getValidFrom().isBefore(now) && voucherCode.getValidUntil().isAfter(now)) {
                return 1;
            }
            if (voucherCode.getValidFrom().isAfter(now)) {
                return 2;
            }
            if (voucherCode.getValidUntil().isBefore(now)) {
                return 3;
            }
        }
        return 0;
    }

    private String displayValue(VoucherCode voucherCode) {
        if (voucherCode.getType() != null && voucherCode.getType() == 1) {
            return strip(voucherCode.getAmount()) + "%";
        }
        return "GBP " + strip(voucherCode.getAmount());
    }

    private String strip(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private Integer readRequiredType(Map<String, Object> payload) {
        Object raw = payload.get("type");
        if (raw == null) {
            raw = payload.get("discount_type");
        }
        if (raw == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The type field is required.");
        }
        int type;
        try {
            type = Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected type is invalid.");
        }
        if (type != 1 && type != 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected type is invalid.");
        }
        return type;
    }

    private BigDecimal readRequiredAmount(Map<String, Object> payload) {
        if (!payload.containsKey("amount")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The amount field is required.");
        }
        return readDecimal(payload.get("amount"), "The amount must be a number.");
    }

    private BigDecimal readDecimal(Object raw, String message) {
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", message);
        }
    }

    private OffsetDateTime readRequiredDate(Map<String, Object> payload, String key) {
        if (!payload.containsKey(key) || payload.get(key) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is required.");
        }
        return readDate(payload.get(key), "The " + key + " is not a valid date.");
    }

    private OffsetDateTime readDate(Object raw, String message) {
        try {
            String value = String.valueOf(raw);
            if (value.length() == 10) {
                return OffsetDateTime.parse(value + "T00:00:00Z");
            }
            if (value.endsWith("Z") || value.contains("+")) {
                return OffsetDateTime.parse(value);
            }
            return OffsetDateTime.parse(value.replace(" ", "T") + "Z");
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", message);
        }
    }

    private Optional<Boolean> readBoolean(Map<String, Object> payload, String key) {
        if (!payload.containsKey(key)) {
            return Optional.empty();
        }
        Object raw = payload.get(key);
        if (raw instanceof Boolean b) {
            return Optional.of(b);
        }
        String value = String.valueOf(raw).toLowerCase(Locale.ROOT);
        if ("true".equals(value) || "1".equals(value)) {
            return Optional.of(true);
        }
        if ("false".equals(value) || "0".equals(value)) {
            return Optional.of(false);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field must be true or false.");
    }

    private Optional<String> optionalString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return Optional.empty();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private String generateCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        } while (voucherCodeRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(code));
        return code;
    }
}
