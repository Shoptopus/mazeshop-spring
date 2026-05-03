package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NoteControllerTest extends AdminControllerTestBase {

    @Test
    void test_note_can_be_created_updated_listed_and_deleted() {
        String token = token();
        UUID userId = UUID.fromString(jdbc.queryForObject("select id::text from users where deleted_at is null limit 1", String.class));

        assert2xx(authed(token, HttpMethod.POST, "/api/admin/note", Map.of(
                "user_id", userId.toString(),
                "noteable_type", "user",
                "noteable_id", userId.toString(),
                "text", "Initial note"
        )));
        UUID noteId = UUID.fromString(jdbc.queryForObject("select id::text from notes order by created_at desc limit 1", String.class));

        assert2xx(authed(token, HttpMethod.POST, "/api/admin/note/" + noteId, Map.of("text", "Updated note")));
        String text = jdbc.queryForObject("select text from notes where id = ?", String.class, noteId);
        assertThat(text).isEqualTo("Updated note");

        var listResponse = authed(token, HttpMethod.GET, "/api/admin/notes", null);
        assert2xx(listResponse);
        var listData = (List<Map<String, Object>>) listResponse.getBody().get("data");
        assertThat(listData).isNotEmpty();
        assertThat(listData).anySatisfy(row -> {
            assertThat(row.get("id")).isEqualTo(noteId.toString());
            assertThat(row.get("text")).isEqualTo("Updated note");
            assertThat(row.get("noteable_type")).isEqualTo("user");
            assertThat(row.get("user_id")).isEqualTo(userId.toString());
            assertThat(row.get("user")).isInstanceOf(Map.class);
        });

        var showResponse = authed(token, HttpMethod.GET, "/api/admin/note/" + noteId, null);
        assert2xx(showResponse);
        var wrapper = (Map<String, Object>) showResponse.getBody().get("data");
        var data = (Map<String, Object>) wrapper.get("data");
        assertThat(data.get("id")).isEqualTo(noteId.toString());
        assertThat(data.get("text")).isEqualTo("Updated note");
        assertThat(data.get("user_id")).isEqualTo(userId.toString());
        assertThat(data.get("noteable_id")).isEqualTo(userId.toString());
        assertThat(data.get("user")).isInstanceOf(Map.class);

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/note/" + noteId, null));
        Integer deleted = jdbc.queryForObject("select count(*) from notes where id = ? and deleted_at is not null", Integer.class, noteId);
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void test_note_create_validation() {
        String token = token();
        var response = authed(token, HttpMethod.POST, "/api/admin/note", Map.of("content", ""));
        assertValidationFailed(response, "text field is required");
    }

    @Test
    void test_note_update_alias_accepts_content_and_missing_note_is_tolerated() {
        String token = token();
        UUID missingId = UUID.randomUUID();

        var updateMissing = authed(token, HttpMethod.POST, "/api/admin/note/" + missingId, Map.of("content", "Updated note"));
        assert2xx(updateMissing);
        assertThat(((Map<?, ?>) updateMissing.getBody().get("data")).get("id")).isEqualTo(missingId.toString());
    }
}
