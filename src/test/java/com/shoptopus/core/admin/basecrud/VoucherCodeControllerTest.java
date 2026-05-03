package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherCodeControllerTest extends AdminControllerTestBase {

    @Test
    void voucher_codes_can_be_listed_and_shown() {
        String token = token();
        seedVoucherCode("LIST-A-" + UUID.randomUUID().toString().substring(0, 6), 1, "10.00", true,
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(7));
        UUID shownId = seedVoucherCode("LIST-B-" + UUID.randomUUID().toString().substring(0, 6), 2, "5.00", true,
                OffsetDateTime.now().minusDays(2), OffsetDateTime.now().plusDays(5));
        seedOrderUsingVoucher(shownId, 2);

        var list = authed(token, HttpMethod.GET, "/api/admin/voucher-codes", null);
        assert2xx(list);
        Map<?, ?> row = ((List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> shownId.toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.get("type")).isEqualTo(2);
        assertThat(row.get("value")).isEqualTo("GBP 5");
        assertThat(row.get("used")).isEqualTo(1);
        assertThat(row.get("status")).isEqualTo(1);

        var show = authed(token, HttpMethod.GET, "/api/admin/voucher-code/" + shownId, null);
        assert2xx(show);
        Map<?, ?> data = (Map<?, ?>) show.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(shownId.toString());
        assertThat(data.get("orders")).isInstanceOf(List.class);
        assertThat(data.get("notes")).isInstanceOf(List.class);
    }

    @Test
    void voucher_code_can_be_created_updated_and_deleted() {
        String token = token();
        OffsetDateTime validFrom = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime validUntil = validFrom.plusMonths(1);

        var create = authed(token, HttpMethod.POST, "/api/admin/voucher-code", Map.of(
                "amount", 10,
                "type", 1,
                "valid_from", validFrom.toString(),
                "valid_until", validUntil.toString()
        ));
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID id = UUID.fromString(String.valueOf(((Map<?, ?>) create.getBody().get("data")).get("id")));

        String code = jdbc.queryForObject("select code from voucher_codes where id = ?::uuid", String.class, id.toString());
        Integer type = jdbc.queryForObject("select discount_type from voucher_codes where id = ?::uuid", Integer.class, id.toString());
        BigDecimal amount = jdbc.queryForObject("select amount from voucher_codes where id = ?::uuid", BigDecimal.class, id.toString());
        assertThat(code).isNotBlank();
        assertThat(type).isEqualTo(1);
        assertThat(amount).isEqualByComparingTo("10.00");

        OffsetDateTime updateUntil = validFrom.plusWeeks(1);
        var update = authed(token, HttpMethod.PATCH, "/api/admin/voucher-code/" + id, Map.of(
                "amount", 5,
                "type", 2,
                "valid_from", validFrom.toString(),
                "valid_until", updateUntil.toString()
        ));
        assert2xx(update);

        type = jdbc.queryForObject("select discount_type from voucher_codes where id = ?::uuid", Integer.class, id.toString());
        amount = jdbc.queryForObject("select amount from voucher_codes where id = ?::uuid", BigDecimal.class, id.toString());
        assertThat(type).isEqualTo(2);
        assertThat(amount).isEqualByComparingTo("5.00");

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/voucher-code/" + id, null));
        Integer deleted = jdbc.queryForObject("select count(*) from voucher_codes where id = ?::uuid and deleted_at is not null", Integer.class, id.toString());
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void voucher_code_creation_validation() {
        String token = token();
        var response = authed(token, HttpMethod.POST, "/api/admin/voucher-code", Map.of("amount", 10));
        assertValidationFailed(response, "type field is required");
    }

    @Test
    void voucher_code_update_validation() {
        String token = token();
        UUID voucherId = seedVoucherCode("UPDATE-" + UUID.randomUUID().toString().substring(0, 6), 1, "10.00", true,
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(30));

        var response = authed(token, HttpMethod.PATCH, "/api/admin/voucher-code/" + voucherId, Map.of(
                "amount", "thirtytwo",
                "type", 2,
                "valid_from", OffsetDateTime.now().toString(),
                "valid_until", OffsetDateTime.now().plusDays(7).toString()
        ));
        assertValidationFailed(response, "amount must be a number");
    }

    @Test
    void voucher_code_crud_requires_super_admin_role() {
        String email = "voucher-customer-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Voucher",
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

        var create = authed(token, HttpMethod.POST, "/api/admin/voucher-code", Map.of(
                "amount", 10,
                "type", 1,
                "valid_from", OffsetDateTime.now().toString(),
                "valid_until", OffsetDateTime.now().plusDays(7).toString()
        ));
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        UUID voucherId = seedVoucherCode("DENY-" + UUID.randomUUID().toString().substring(0, 6), 1, "8.00", true,
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(7));
        var update = authed(token, HttpMethod.PATCH, "/api/admin/voucher-code/" + voucherId, Map.of(
                "amount", 5,
                "type", 2,
                "valid_from", OffsetDateTime.now().toString(),
                "valid_until", OffsetDateTime.now().plusDays(7).toString()
        ));
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        var delete = authed(token, HttpMethod.DELETE, "/api/admin/voucher-code/" + voucherId, null);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID seedVoucherCode(String code, int type, String amount, boolean enabled, OffsetDateTime validFrom, OffsetDateTime validUntil) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into voucher_codes(id, code, name, discount_type, amount, enabled, valid_from, valid_until, slug, created_at, updated_at)
                values (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """,
                id, code, code, type, new BigDecimal(amount), enabled, validFrom, validUntil, "voucher-" + id);
        return UUID.fromString(id);
    }

    private void seedOrderUsingVoucher(UUID voucherId, int status) {
        String userId = jdbc.queryForObject(
                "select id::text from users where lower(email) = lower(?) limit 1",
                String.class,
                rootUserUsername
        );
        String addressId = jdbc.query("""
                select id::text from addresses where user_id = ?::uuid limit 1
                """, rs -> rs.next() ? rs.getString(1) : null, userId);
        if (addressId == null) {
            addressId = UUID.randomUUID().toString();
            jdbc.update("""
                    insert into addresses(id, user_id, address_line_1, town, post_code, country, slug, created_at, updated_at)
                    values (?::uuid, ?::uuid, '1 Admin Street', 'London', 'E1 1AA', 'UK', ?, now(), now())
                    """,
                    addressId, userId, "address-" + addressId);
        }
        jdbc.update("""
                insert into orders(id, user_id, address_id, status, original_price, subtotal, total_price, currency_code, slug, voucher_code_id, created_at, updated_at)
                values (?::uuid, ?::uuid, ?::uuid, ?, 10, 10, 10, 'GBP', ?, ?::uuid, now(), now())
                """,
                UUID.randomUUID().toString(), userId, addressId, status, "order-" + UUID.randomUUID(), voucherId.toString());
    }
}
