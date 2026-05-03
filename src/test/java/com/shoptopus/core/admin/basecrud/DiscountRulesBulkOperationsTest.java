package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountRulesBulkOperationsTest extends AdminControllerTestBase {

    @Test
    void can_expire_start_activate_and_delete_multiple_discount_rules() {
        String token = token();
        List<UUID> ids = List.of(
                seedRule(OffsetDateTime.now().plusMonths(1), OffsetDateTime.now().plusMonths(2)),
                seedRule(OffsetDateTime.now().plusMonths(1), OffsetDateTime.now().plusMonths(2)),
                seedRule(OffsetDateTime.now().plusMonths(1), OffsetDateTime.now().plusMonths(2))
        );

        var expire = authed(token, HttpMethod.POST, "/api/admin/discount-rules/bulk/expire", Map.of("ids", ids));
        assert2xx(expire);
        OffsetDateTime expiredUntil = jdbc.queryForObject("select valid_until from discount_rules where id = ?::uuid", OffsetDateTime.class, ids.get(0).toString());
        assertThat(expiredUntil).isBeforeOrEqualTo(OffsetDateTime.now().plusSeconds(2));

        var start = authed(token, HttpMethod.POST, "/api/admin/discount-rules/bulk/start", Map.of("ids", ids));
        assert2xx(start);
        OffsetDateTime startedFrom = jdbc.queryForObject("select valid_from from discount_rules where id = ?::uuid", OffsetDateTime.class, ids.get(0).toString());
        assertThat(startedFrom).isBeforeOrEqualTo(OffsetDateTime.now().plusSeconds(2));

        var activate = authed(token, HttpMethod.POST, "/api/admin/discount-rules/bulk/activate", Map.of("ids", ids, "period", 2));
        assert2xx(activate);
        OffsetDateTime activatedUntil = jdbc.queryForObject("select valid_until from discount_rules where id = ?::uuid", OffsetDateTime.class, ids.get(0).toString());
        OffsetDateTime expectedMin = OffsetDateTime.now().withHour(23).withMinute(59).withSecond(59).withNano(0).plusDays(27);
        assertThat(activatedUntil).isAfter(expectedMin);

        var delete = authed(token, HttpMethod.DELETE, "/api/admin/discount-rules/bulk", Map.of("ids", ids));
        assert2xx(delete);
        for (UUID id : ids) {
            Integer deleted = jdbc.queryForObject("select count(*) from discount_rules where id = ?::uuid and deleted_at is not null", Integer.class, id.toString());
            assertThat(deleted).isEqualTo(1);
        }
    }

    @Test
    void bulk_discount_rule_validation_is_enforced() {
        String token = token();
        var start = authed(token, HttpMethod.POST, "/api/admin/discount-rules/bulk/start", Map.of());
        assertValidationFailed(start, "ids field is required");

        UUID existingId = seedRule(OffsetDateTime.now(), OffsetDateTime.now().plusDays(1));
        var activate = authed(token, HttpMethod.POST, "/api/admin/discount-rules/bulk/activate", Map.of("ids", List.of(existingId)));
        assertValidationFailed(activate, "period field is required");

        var invalidIds = authed(token, HttpMethod.DELETE, "/api/admin/discount-rules/bulk", Map.of("ids", List.of("invalid id")));
        assertValidationFailed(invalidIds, "selected ids.0 is invalid");
    }

    @Test
    void bulk_discount_rule_operations_require_super_admin_role() {
        String email = "discount-bulk-customer-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Bulk",
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
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        List<UUID> ids = List.of(seedRule(OffsetDateTime.now(), OffsetDateTime.now().plusDays(1)));

        assertThat(authed(token, HttpMethod.POST, "/api/admin/discount-rules/bulk/expire", Map.of("ids", ids)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(token, HttpMethod.POST, "/api/admin/discount-rules/bulk/start", Map.of("ids", ids)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(token, HttpMethod.POST, "/api/admin/discount-rules/bulk/activate", Map.of("ids", ids, "period", 1)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(token, HttpMethod.DELETE, "/api/admin/discount-rules/bulk", Map.of("ids", ids)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID seedRule(OffsetDateTime validFrom, OffsetDateTime validUntil) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into discount_rules(id, type, name, amount, valid_from, valid_until, enabled, slug, created_at, updated_at)
                values (?::uuid, 1, ?::jsonb, 10, ?, ?, true, ?, now(), now())
                """,
                id, "{\"en\":\"Bulk Discount\"}", validFrom.truncatedTo(ChronoUnit.SECONDS), validUntil.truncatedTo(ChronoUnit.SECONDS), "discount-rule-" + id);
        return UUID.fromString(id);
    }
}
