package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentsBulkOperationsTest extends AdminControllerTestBase {

    @Test
    void can_update_multiple_payment_statuses() {
        String token = token();
        List<UUID> ids = List.of(seedPayment(0), seedPayment(0), seedPayment(0));

        var response = authed(token, HttpMethod.POST, "/api/admin/payments/bulk/status", Map.of("ids", ids, "status", 2));
        assert2xx(response);
        assertThat(jdbc.queryForObject("select status from payments where id = ?::uuid", Integer.class, ids.get(0).toString())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from payments where id = ?::uuid", Integer.class, ids.get(1).toString())).isEqualTo(2);
    }

    @Test
    void bulk_payment_status_validation_is_enforced() {
        String token = token();
        var missingStatus = authed(token, HttpMethod.POST, "/api/admin/payments/bulk/status", Map.of("ids", List.of(seedPayment(0))));
        assertValidationFailed(missingStatus, "status field is required");

        var missingIds = authed(token, HttpMethod.POST, "/api/admin/payments/bulk/status", Map.of("status", 2));
        assertValidationFailed(missingIds, "ids field is required");

        var invalidIds = authed(token, HttpMethod.POST, "/api/admin/payments/bulk/status", Map.of("ids", List.of("invalid id"), "status", 2));
        assertValidationFailed(invalidIds, "selected ids.0 is invalid");
    }

    @Test
    void bulk_payment_status_requires_super_admin() {
        String customerToken = customerToken("payments");
        List<UUID> ids = List.of(seedPayment(0));
        assertThat(authed(customerToken, HttpMethod.POST, "/api/admin/payments/bulk/status", Map.of("ids", ids, "status", 2)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID seedPayment(int status) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into payments(id, payable_type, payable_id, amount, slug, status, type, created_at, updated_at)
                values (?::uuid, 'App\\Models\\Order', ?, 10, ?, ?, 0, now(), now())
                """,
                id, UUID.randomUUID().toString(), "payment-" + id, status);
        return UUID.fromString(id);
    }

    private String customerToken(String prefix) {
        String email = prefix + "-customer-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", prefix,
                "last_name", "Customer",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "customer");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        return (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");
    }
}
