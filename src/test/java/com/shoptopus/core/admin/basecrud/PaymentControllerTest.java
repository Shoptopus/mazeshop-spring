package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentControllerTest extends AdminControllerTestBase {

    @Test
    void payments_can_be_created_listed_shown_updated_refunded_and_deleted() {
        String token = token();
        String userId = jdbc.queryForObject("select id::text from users where deleted_at is null limit 1", String.class);

        var create = authed(token, HttpMethod.POST, "/api/admin/payment", Map.of(
                "payable_type", "App\\Models\\Order",
                "payable_id", UUID.randomUUID().toString(),
                "amount", "15.25",
                "status", 1,
                "type", 0,
                "user_id", userId,
                "description", "Initial payment"
        ));
        assert2xx(create);
        String paymentId = String.valueOf(((Map<?, ?>) create.getBody().get("data")).get("id"));

        var list = authed(token, HttpMethod.GET, "/api/admin/payments", null);
        assert2xx(list);
        Map<?, ?> row = ((List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> paymentId.equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.get("status")).isEqualTo(1);
        assertThat(row.get("user_id")).isEqualTo(userId);

        var show = authed(token, HttpMethod.GET, "/api/admin/payment/" + paymentId, null);
        assert2xx(show);
        Map<?, ?> data = (Map<?, ?>) show.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(paymentId);
        assertThat(data.get("user")).isInstanceOf(Map.class);
        assertThat(data.get("description")).isEqualTo("Initial payment");

        assert2xx(authed(token, HttpMethod.POST, "/api/admin/payment/" + paymentId, Map.of(
                "amount", "18.55",
                "status", 2,
                "payment_ref", "pay-ref-1"
        )));
        assertThat(jdbc.queryForObject("select status from payments where id = ?::uuid", Integer.class, paymentId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select amount::text from payments where id = ?::uuid", String.class, paymentId)).startsWith("18.55");

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/payment/" + paymentId + "/refund", null));
        assertThat(jdbc.queryForObject("select status from payments where id = ?::uuid", Integer.class, paymentId)).isEqualTo(3);

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/payment/" + paymentId, null));
        assertThat(jdbc.queryForObject("select count(*) from payments where id = ?::uuid and deleted_at is not null", Integer.class, paymentId)).isEqualTo(1);
    }

    @Test
    void payment_validation_and_permissions_are_enforced() {
        String token = token();

        var missingAmount = authed(token, HttpMethod.POST, "/api/admin/payment", Map.of(
                "payable_type", "App\\Models\\Order",
                "payable_id", UUID.randomUUID().toString()
        ));
        assertValidationFailed(missingAmount, "amount field is required");

        var invalidAmount = authed(token, HttpMethod.POST, "/api/admin/payment", Map.of(
                "payable_type", "App\\Models\\Order",
                "payable_id", UUID.randomUUID().toString(),
                "amount", "abc"
        ));
        assertValidationFailed(invalidAmount, "amount field is invalid");

        String email = "payments-manager-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Payments",
                "last_name", "Manager",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "payments_manager");
        assignPermissionToRole("payments_manager", "payments.manage");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String permittedToken = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(permittedToken, HttpMethod.GET, "/api/admin/payments", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        String blockedEmail = "payments-blocked-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Payments",
                "last_name", "Blocked",
                "email", blockedEmail,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(blockedEmail, "customer");
        var blockedLogin = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", blockedEmail,
                "password", "password123"
        ), Map.class);
        String blockedToken = (String) ((Map<?, ?>) ((Map<?, ?>) blockedLogin.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(blockedToken, HttpMethod.GET, "/api/admin/payments", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(blockedToken, HttpMethod.POST, "/api/admin/payment", Map.of(
                "payable_type", "App\\Models\\Order",
                "payable_id", UUID.randomUUID().toString(),
                "amount", "10.00"
        )).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
