package com.shoptopus.core.admin;

import com.shoptopus.core.common.error.ApiException;
import com.shoptopus.core.adminui.AdminStoreContext;
import com.shoptopus.core.payment.Payment;
import com.shoptopus.core.payment.stripe.StripePaymentGateway;
import com.shoptopus.core.payment.repository.PaymentRepository;
import com.shoptopus.core.store.Store;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminPaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final StripePaymentGateway stripePaymentGateway;
    private final AdminStoreContext adminStoreContext;

    public AdminPaymentService(PaymentRepository paymentRepository, UserRepository userRepository, StripePaymentGateway stripePaymentGateway, AdminStoreContext adminStoreContext) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.stripePaymentGateway = stripePaymentGateway;
        this.adminStoreContext = adminStoreContext;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list() {
        List<Map<String, Object>> data = scopedPayments().stream()
                .map(this::toRow)
                .toList();
        return listResponse("payments", data);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> show(String id) {
        UUID paymentId = uuid(id).orElse(null);
        if (paymentId == null) {
            return Map.of("id", id);
        }
        return paymentRepository.findById(paymentId)
                .filter(payment -> payment.getDeletedAt() == null)
                .filter(this::matchesActiveStore)
                .map(this::toDetail)
                .orElseGet(() -> Map.of("id", paymentId));
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> create(Map<String, Object> body) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setPayableType(required(body, "payable_type"));
        payment.setPayableId(required(body, "payable_id"));
        payment.setAmount(decimal(body, "amount", true, null));
        payment.setSlug(str(body, "slug", "payment-" + payment.getId()));
        payment.setStatus(intVal(body, "status", 0));
        payment.setType(intVal(body, "type", 0));
        payment.setDescription(str(body, "description", null));
        payment.setPaymentRef(str(body, "payment_ref", null));
        payment.setMethodRef(str(body, "method_ref", null));
        payment.setUser(resolveUser(body));
        payment.setStore(activeStore());
        paymentRepository.save(payment);
        return Map.of("id", payment.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> update(String id, Map<String, Object> body) {
        UUID paymentId = uuid(id).orElse(null);
        if (paymentId == null) {
            return Map.of("id", id);
        }
        Optional<Payment> payment = paymentRepository.findById(paymentId).filter(item -> item.getDeletedAt() == null);
        payment = payment.filter(this::matchesActiveStore);
        if (payment.isEmpty()) {
            return Map.of("id", paymentId);
        }
        Payment entity = payment.orElseThrow();
        if (body != null && body.containsKey("amount")) {
            entity.setAmount(decimal(body, "amount", true, entity.getAmount()));
        }
        if (body != null && body.containsKey("status")) {
            entity.setStatus(intVal(body, "status", entity.getStatus()));
        }
        if (body != null && body.containsKey("type")) {
            entity.setType(intVal(body, "type", entity.getType()));
        }
        if (body != null && body.containsKey("description")) {
            entity.setDescription(str(body, "description", entity.getDescription()));
        }
        if (body != null && body.containsKey("payment_ref")) {
            entity.setPaymentRef(str(body, "payment_ref", entity.getPaymentRef()));
        }
        if (body != null && body.containsKey("method_ref")) {
            entity.setMethodRef(str(body, "method_ref", entity.getMethodRef()));
        }
        if (body != null && body.containsKey("user_id")) {
            entity.setUser(resolveUser(body));
        }
        paymentRepository.save(entity);
        return Map.of("id", entity.getId());
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> delete(String id) {
        UUID paymentId = uuid(id).orElse(null);
        if (paymentId == null) {
            return Map.of("id", id);
        }
        paymentRepository.findById(paymentId).filter(item -> item.getDeletedAt() == null).filter(this::matchesActiveStore).ifPresent(payment -> {
            payment.setDeletedAt(OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS));
            paymentRepository.save(payment);
        });
        return Map.of("id", paymentId);
    }

    @Transactional("mainTransactionManager")
    public Map<String, Object> refund(String id, BigDecimal amount) {
        UUID paymentId = uuid(id).orElse(null);
        if (paymentId == null) {
            return Map.of("id", id);
        }
        paymentRepository.findById(paymentId).filter(item -> item.getDeletedAt() == null).filter(this::matchesActiveStore).ifPresent(payment -> {
            if (isStripePayment(payment) && payment.getPaymentRef() != null && !payment.getPaymentRef().isBlank()) {
                BigDecimal requested = amount == null || amount.signum() <= 0 ? payment.getAmount() : amount;
                Map<String, Object> refund = stripePaymentGateway.refundPaymentIntent(payment.getPaymentRef(), requested);
                BigDecimal refundedAmount = decimalFromProof(payment.getProof().get("refunded_amount")).add(decimalFromProof(refund.get("amount")));
                payment.getProof().put("provider", "stripe");
                payment.getProof().put("refunded_amount", refundedAmount);
                payment.getProof().put("refundable_amount", payment.getAmount().subtract(refundedAmount).max(BigDecimal.ZERO));
                payment.getProof().put("last_refund", refund);
                payment.setStatus(refundedAmount.compareTo(payment.getAmount()) >= 0 ? 3 : 1);
            } else {
                payment.setStatus(3);
            }
            paymentRepository.save(payment);
        });
        return Map.of("id", paymentId);
    }


    private boolean isStripePayment(Payment payment) {
        Object provider = payment.getProof() == null ? null : payment.getProof().get("provider");
        return provider != null && "stripe".equalsIgnoreCase(String.valueOf(provider));
    }

    private BigDecimal decimalFromProof(Object raw) {
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        try {
            return new BigDecimal(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private Map<String, Object> toDetail(Payment payment) {
        Map<String, Object> row = toRow(payment);
        row.put("proof", payment.getProof());
        row.put("description", payment.getDescription());
        row.put("payment_ref", payment.getPaymentRef());
        row.put("method_ref", payment.getMethodRef());
        return row;
    }

    private Map<String, Object> toRow(Payment payment) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", payment.getId());
        row.put("slug", payment.getSlug());
        row.put("amount", payment.getAmount());
        row.put("status", payment.getStatus());
        row.put("type", payment.getType());
        row.put("payable_type", payment.getPayableType());
        row.put("payable_id", payment.getPayableId());
        row.put("payment_ref", payment.getPaymentRef());
        row.put("method_ref", payment.getMethodRef());
        row.put("description", payment.getDescription());
        User user = payment.getUser();
        row.put("user_id", user == null ? null : user.getId());
        if (user == null) {
            row.put("user", null);
        } else {
            Map<String, Object> userRow = new LinkedHashMap<>();
            userRow.put("id", user.getId());
            userRow.put("email", user.getEmail());
            userRow.put("first_name", user.getFirstName());
            userRow.put("last_name", user.getLastName());
            row.put("user", userRow);
        }
        return row;
    }

    private Map<String, Object> listResponse(String message, List<Map<String, Object>> data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", message);
        response.put("data", data);
        response.put("records", data.size());
        response.put("total_records", data.size());
        response.put("next", null);
        return response;
    }

    private List<Payment> scopedPayments() {
        Store activeStore = activeStore();
        if (activeStore == null) {
            return paymentRepository.findByDeletedAtIsNull();
        }
        return paymentRepository.findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDesc(activeStore.getId());
    }

    private boolean matchesActiveStore(Payment payment) {
        if (!adminStoreContext.multiStoreEnabled()) {
            return true;
        }
        Store activeStore = activeStore();
        return activeStore != null && payment.getStore() != null && activeStore.getId().equals(payment.getStore().getId());
    }

    private Store activeStore() {
        return adminStoreContext.activeStoreForCurrentRequest().orElse(null);
    }

    private User resolveUser(Map<String, Object> body) {
        UUID userId = uuid(str(body, "user_id", null)).orElse(null);
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The selected user_id is invalid."));
    }

    private String required(Map<String, Object> body, String key) {
        String value = str(body, key, null);
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is required.");
        }
        return value;
    }

    private String str(Map<String, Object> body, String key, String fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        return String.valueOf(body.get(key));
    }

    private Integer intVal(Map<String, Object> body, String key, Integer fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return fallback;
        }
        Object raw = body.get(key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is invalid.");
        }
    }

    private BigDecimal decimal(Map<String, Object> body, String key, boolean required, BigDecimal fallback) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            if (required && fallback == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is required.");
            }
            return fallback;
        }
        try {
            return new BigDecimal(String.valueOf(body.get(key)));
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "The " + key + " field is invalid.");
        }
    }

    private Optional<UUID> uuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
