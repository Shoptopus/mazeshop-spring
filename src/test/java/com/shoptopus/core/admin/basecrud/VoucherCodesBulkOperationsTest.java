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

class VoucherCodesBulkOperationsTest extends AdminControllerTestBase {

    @Test
    void can_expire_start_activate_and_delete_multiple_voucher_codes() {
        String token = token();
        List<UUID> ids = List.of(
                seedVoucherCode(OffsetDateTime.now().plusMonths(1), OffsetDateTime.now().plusMonths(2)),
                seedVoucherCode(OffsetDateTime.now().plusMonths(1), OffsetDateTime.now().plusMonths(2)),
                seedVoucherCode(OffsetDateTime.now().plusMonths(1), OffsetDateTime.now().plusMonths(2))
        );

        var expire = authed(token, HttpMethod.POST, "/api/admin/voucher-codes/bulk/expire", Map.of("ids", ids));
        assert2xx(expire);
        OffsetDateTime expiredUntil = jdbc.queryForObject("select valid_until from voucher_codes where id = ?::uuid", OffsetDateTime.class, ids.get(0).toString());
        assertThat(expiredUntil).isBeforeOrEqualTo(OffsetDateTime.now().plusSeconds(2));

        var start = authed(token, HttpMethod.POST, "/api/admin/voucher-codes/bulk/start", Map.of("ids", ids));
        assert2xx(start);
        OffsetDateTime startedFrom = jdbc.queryForObject("select valid_from from voucher_codes where id = ?::uuid", OffsetDateTime.class, ids.get(0).toString());
        assertThat(startedFrom).isBeforeOrEqualTo(OffsetDateTime.now().plusSeconds(2));

        var activate = authed(token, HttpMethod.POST, "/api/admin/voucher-codes/bulk/activate", Map.of("ids", ids, "period", 2));
        assert2xx(activate);
        OffsetDateTime activatedUntil = jdbc.queryForObject("select valid_until from voucher_codes where id = ?::uuid", OffsetDateTime.class, ids.get(0).toString());
        OffsetDateTime expectedMin = OffsetDateTime.now().withHour(23).withMinute(59).withSecond(59).withNano(0).plusDays(27);
        assertThat(activatedUntil).isAfter(expectedMin);

        var delete = authed(token, HttpMethod.DELETE, "/api/admin/voucher-codes/bulk", Map.of("ids", ids));
        assert2xx(delete);
        for (UUID id : ids) {
            Integer deleted = jdbc.queryForObject("select count(*) from voucher_codes where id = ?::uuid and deleted_at is not null", Integer.class, id.toString());
            assertThat(deleted).isEqualTo(1);
        }
    }

    @Test
    void bulk_voucher_code_validation_is_enforced() {
        String token = token();
        var start = authed(token, HttpMethod.POST, "/api/admin/voucher-codes/bulk/start", Map.of());
        assertValidationFailed(start, "ids field is required");

        UUID existingId = seedVoucherCode(OffsetDateTime.now(), OffsetDateTime.now().plusDays(1));
        var activate = authed(token, HttpMethod.POST, "/api/admin/voucher-codes/bulk/activate", Map.of("ids", List.of(existingId)));
        assertValidationFailed(activate, "period field is required");

        var invalidIds = authed(token, HttpMethod.DELETE, "/api/admin/voucher-codes/bulk", Map.of("ids", List.of("invalid id")));
        assertValidationFailed(invalidIds, "selected ids.0 is invalid");
    }

    @Test
    void bulk_voucher_code_operations_require_super_admin_role() {
        String email = "voucher-bulk-customer-" + UUID.randomUUID() + "@example.com";
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

        List<UUID> ids = List.of(seedVoucherCode(OffsetDateTime.now(), OffsetDateTime.now().plusDays(1)));

        var expire = authed(token, HttpMethod.POST, "/api/admin/voucher-codes/bulk/expire", Map.of("ids", ids));
        assertThat(expire.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var start = authed(token, HttpMethod.POST, "/api/admin/voucher-codes/bulk/start", Map.of("ids", ids));
        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var activate = authed(token, HttpMethod.POST, "/api/admin/voucher-codes/bulk/activate", Map.of("ids", ids, "period", 1));
        assertThat(activate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var delete = authed(token, HttpMethod.DELETE, "/api/admin/voucher-codes/bulk", Map.of("ids", ids));
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID seedVoucherCode(OffsetDateTime validFrom, OffsetDateTime validUntil) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into voucher_codes(id, code, name, discount_type, amount, enabled, valid_from, valid_until, slug, created_at, updated_at)
                values (?::uuid, ?, ?, 1, 10, true, ?, ?, ?, now(), now())
                """,
                id, "BULK-" + id.substring(0, 8), "Bulk Voucher", validFrom.truncatedTo(ChronoUnit.SECONDS),
                validUntil.truncatedTo(ChronoUnit.SECONDS), "voucher-" + id);
        return UUID.fromString(id);
    }
}
