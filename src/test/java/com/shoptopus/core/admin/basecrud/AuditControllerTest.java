package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditControllerTest extends AdminControllerTestBase {

    @Test
    void audits_can_be_listed_and_shown() {
        String token = token();
        UUID userId = UUID.fromString(jdbc.queryForObject("select id::text from users where deleted_at is null limit 1", String.class));
        UUID auditId = UUID.randomUUID();

        logsJdbc().update("""
                insert into audits(
                    id, user_id, operation, entity_type, entity_id, before_state, after_state, created_at, updated_at
                ) values (
                    ?::uuid, ?::uuid, 'update', 'Product', ?, ?::jsonb, ?::jsonb, now(), now()
                )
                """,
                auditId.toString(),
                userId.toString(),
                UUID.randomUUID().toString(),
                "{\"name\":\"before\"}",
                "{\"name\":\"after\"}");

        var list = authed(token, HttpMethod.GET, "/api/admin/audits", null);
        assert2xx(list);
        Map<?, ?> row = ((List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> auditId.toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.get("operation")).isEqualTo("update");
        assertThat(row.get("entity_type")).isEqualTo("Product");
        assertThat(row.get("user_id")).isEqualTo(userId.toString());
        assertThat(row.get("user")).isInstanceOf(Map.class);

        var show = authed(token, HttpMethod.GET, "/api/admin/audit/" + auditId, null);
        assert2xx(show);
        Map<?, ?> data = (Map<?, ?>) show.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(auditId.toString());
        assertThat(data.get("before_state")).isEqualTo(Map.of("name", "before"));
        assertThat(data.get("after_state")).isEqualTo(Map.of("name", "after"));
        assertThat(data.get("user")).isInstanceOf(Map.class);
    }

    @Test
    void unknown_audit_id_is_tolerated() {
        String token = token();
        UUID unknownId = UUID.randomUUID();

        var response = authed(token, HttpMethod.GET, "/api/admin/audit/" + unknownId, null);
        assert2xx(response);
        assertThat(((Map<?, ?>) response.getBody().get("data")).get("id")).isEqualTo(unknownId.toString());
    }
}
