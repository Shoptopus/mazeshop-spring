package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FileControllerTest extends AdminControllerTestBase {

    @Test
    void file_can_be_created_updated_listed_downloaded_and_deleted() {
        String token = token();
        String uploadedPath = "files/file-controller-" + UUID.randomUUID() + ".pdf";
        writeMediaFile(uploadedPath);

        var create = authed(token, HttpMethod.POST, "/api/admin/file", Map.of(
                "name", "guide.pdf",
                "mime_type", "application/pdf",
                "size", 123,
                "path", uploadedPath
        ));
        assert2xx(create);
        UUID fileId = UUID.fromString(String.valueOf(((Map<?, ?>) create.getBody().get("data")).get("id")));

        String updatedPath = "files/file-controller-updated-" + UUID.randomUUID() + ".pdf";
        writeMediaFile(updatedPath);
        var update = authed(token, HttpMethod.POST, "/api/admin/file/" + fileId, Map.of(
                "name", "guide-updated.pdf",
                "path", updatedPath
        ));
        assert2xx(update);

        String name = jdbc.queryForObject("select name from file_contents where id = ?::uuid", String.class, fileId.toString());
        String path = jdbc.queryForObject("select path from file_contents where id = ?::uuid", String.class, fileId.toString());
        assertThat(name).isEqualTo("guide-updated.pdf");
        assertThat(path).isEqualTo(updatedPath);

        var list = authed(token, HttpMethod.GET, "/api/admin/files", null);
        assert2xx(list);
        Map<?, ?> listRow = ((List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> fileId.toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(listRow.get("name")).isEqualTo("guide-updated.pdf");
        assertThat(listRow.get("download_url")).isEqualTo("/media/" + updatedPath);

        var show = authed(token, HttpMethod.GET, "/api/admin/file/" + fileId, null);
        assert2xx(show);
        Map<?, ?> data = (Map<?, ?>) show.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(fileId.toString());
        assertThat(data.get("path")).isEqualTo(updatedPath);

        var download = authed(token, HttpMethod.POST, "/api/admin/file/" + fileId + "/download", null);
        assert2xx(download);
        Map<?, ?> downloadData = (Map<?, ?>) download.getBody().get("data");
        assertThat(downloadData.get("id")).isEqualTo(fileId.toString());
        assertThat(downloadData.get("url")).isEqualTo("/media/" + updatedPath);

        var delete = authed(token, HttpMethod.DELETE, "/api/admin/file/" + fileId, null);
        assert2xx(delete);
        Integer deleted = jdbc.queryForObject("select count(*) from file_contents where id = ?::uuid", Integer.class, fileId.toString());
        assertThat(deleted).isEqualTo(0);
        assertThat(mediaRoot().resolve(updatedPath)).doesNotExist();
    }

    @Test
    void file_validation_and_unknown_alias_routes_are_tolerated() {
        String token = token();

        var missingName = authed(token, HttpMethod.POST, "/api/admin/file", Map.of("path", "/files/test.txt"));
        assertValidationFailed(missingName, "name field is required");

        var create = authed(token, HttpMethod.POST, "/api/admin/file", Map.of("name", "test.txt"));
        assert2xx(create);
        UUID fileId = UUID.fromString(String.valueOf(((Map<?, ?>) create.getBody().get("data")).get("id")));

        var invalidSize = authed(token, HttpMethod.PATCH, "/api/admin/file/" + fileId, Map.of("size", "huge"));
        assertValidationFailed(invalidSize, "size field must be a number");

        UUID unknownId = UUID.randomUUID();
        var updateUnknown = authed(token, HttpMethod.POST, "/api/admin/file/" + unknownId, Map.of("name", "renamed.txt"));
        assert2xx(updateUnknown);
        assertThat(((Map<?, ?>) updateUnknown.getBody().get("data")).get("id")).isEqualTo(unknownId.toString());

        var downloadUnknown = authed(token, HttpMethod.POST, "/api/admin/file/" + unknownId + "/download", null);
        assert2xx(downloadUnknown);
        assertThat(((Map<?, ?>) downloadUnknown.getBody().get("data")).get("url")).isEqualTo("/files/" + unknownId);

        var deleteUnknown = authed(token, HttpMethod.DELETE, "/api/admin/files/" + unknownId, null);
        assert2xx(deleteUnknown);
        assertThat(((Map<?, ?>) deleteUnknown.getBody().get("data")).get("id")).isEqualTo(unknownId.toString());
    }

    private void writeMediaFile(String path) {
        try {
            java.nio.file.Path target = mediaRoot().resolve(path).normalize();
            Files.createDirectories(target.getParent());
            Files.writeString(target, "dummy");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
