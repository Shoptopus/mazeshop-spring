package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedFileProductFlowTest extends AdminControllerTestBase {

    @Test
    void upload_creates_file_record_and_stores_file_on_disk() {
        String token = token();

        var upload = uploadImage(token, "products");
        assert2xx(upload);

        Map<?, ?> data = (Map<?, ?>) upload.getBody().get("data");
        UUID fileId = UUID.fromString(String.valueOf(data.get("id")));
        String storedPath = String.valueOf(data.get("path"));
        String publicUrl = String.valueOf(data.get("url"));

        assertThat(jdbc.queryForObject(
                "select original_name from file_contents where id = ?::uuid",
                String.class,
                fileId.toString()
        )).isEqualTo("cover.png");
        assertThat(jdbc.queryForObject(
                "select mime_type from file_contents where id = ?::uuid",
                String.class,
                fileId.toString()
        )).isEqualTo("image/jpeg");
        assertThat(jdbc.queryForObject(
                "select type from file_contents where id = ?::uuid",
                String.class,
                fileId.toString()
        )).isEqualTo("products");
        assertThat(jdbc.queryForObject(
                "select fileable_type from file_contents where id = ?::uuid",
                String.class,
                fileId.toString()
        )).isNull();
        assertThat(storedPath).startsWith("products/");
        assertThat(storedPath).endsWith(".jpg");
        assertThat(publicUrl).startsWith("/media/products/");
        assertThat(data.get("width")).isEqualTo(1800);
        assertThat(data.get("height")).isEqualTo(900);
        assertThat(jdbc.queryForObject(
                "select path from file_contents where id = ?::uuid",
                String.class,
                fileId.toString()
        )).isEqualTo(storedPath);
        assertThat(toStoredPath(storedPath)).exists();
    }

    @Test
    void saving_replacing_and_removing_cover_photo_manages_file_records() {
        String token = token();

        UUID firstFileId = uploadedFileId(token, "products");
        UUID secondFileId = uploadedFileId(token, "products");

        var create = authed(token, HttpMethod.POST, "/api/admin-ui/products", Map.of(
                "name", Map.of("en", "Managed file product"),
                "cover_photo", Map.of("id", firstFileId.toString())
        ));
        assert2xx(create);

        Map<?, ?> created = (Map<?, ?>) create.getBody().get("data");
        UUID productId = UUID.fromString(String.valueOf(created.get("id")));
        Map<?, ?> createdCoverPhoto = (Map<?, ?>) created.get("cover_photo");

        assertThat(createdCoverPhoto.get("id")).isEqualTo(firstFileId.toString());
        assertThat((List<?>) created.get("files")).hasSize(1);
        assertAttached(firstFileId, productId, "cover_photo");

        Path firstStoredFile = toStoredPath(String.valueOf(createdCoverPhoto.get("path")));
        assertThat(firstStoredFile).exists();
        assertThat(String.valueOf(createdCoverPhoto.get("url"))).startsWith("/media/products/");

        var replace = authed(token, HttpMethod.PATCH, "/api/admin-ui/products/" + productId, Map.of(
                "cover_photo", Map.of("id", secondFileId.toString())
        ));
        assert2xx(replace);

        Map<?, ?> replaced = (Map<?, ?>) replace.getBody().get("data");
        Map<?, ?> replacedCoverPhoto = (Map<?, ?>) replaced.get("cover_photo");
        assertThat(replacedCoverPhoto.get("id")).isEqualTo(secondFileId.toString());
        assertThat((List<?>) replaced.get("files")).hasSize(1);
        Path secondStoredFile = toStoredPath(String.valueOf(replacedCoverPhoto.get("path")));
        assertThat(secondStoredFile).exists();
        assertThat(String.valueOf(replacedCoverPhoto.get("url"))).startsWith("/media/products/");
        assertThat(jdbc.queryForObject(
                "select count(*) from file_contents where id = ?::uuid",
                Integer.class,
                firstFileId.toString()
        )).isEqualTo(0);
        assertThat(firstStoredFile).doesNotExist();
        assertAttached(secondFileId, productId, "cover_photo");

        Map<String, Object> removeBody = new LinkedHashMap<>();
        removeBody.put("cover_photo", null);
        var remove = authed(token, HttpMethod.PATCH, "/api/admin-ui/products/" + productId, removeBody);
        assert2xx(remove);

        Map<?, ?> removed = (Map<?, ?>) remove.getBody().get("data");
        assertThat(removed.get("cover_photo")).isNull();
        assertThat((List<?>) removed.get("files")).isEmpty();
        assertThat(jdbc.queryForObject(
                "select count(*) from file_contents where id = ?::uuid",
                Integer.class,
                secondFileId.toString()
        )).isEqualTo(0);
        assertThat(secondStoredFile).doesNotExist();
    }

    @Test
    void deleting_product_cleans_up_attached_files() {
        String token = token();

        UUID fileId = uploadedFileId(token, "products");
        var create = authed(token, HttpMethod.POST, "/api/admin-ui/products", Map.of(
                "name", Map.of("en", "Delete cleanup product"),
                "cover_photo", Map.of("id", fileId.toString())
        ));
        assert2xx(create);

        Map<?, ?> created = (Map<?, ?>) create.getBody().get("data");
        UUID productId = UUID.fromString(String.valueOf(created.get("id")));
        Path storedFile = toStoredPath(String.valueOf(((Map<?, ?>) created.get("cover_photo")).get("path")));
        assertThat(storedFile).exists();

        var delete = authed(token, HttpMethod.DELETE, "/api/admin/product/" + productId, null);
        assert2xx(delete);

        assertThat(jdbc.queryForObject(
                "select count(*) from file_contents where id = ?::uuid",
                Integer.class,
                fileId.toString()
        )).isEqualTo(0);
        assertThat(storedFile).doesNotExist();
        assertThat(jdbc.queryForObject(
                "select count(*) from products where id = ?::uuid and deleted_at is not null",
                Integer.class,
                productId.toString()
        )).isEqualTo(1);
    }

    private UUID uploadedFileId(String token, String folder) {
        ResponseEntity<Map> upload = uploadImage(token, folder);
        assert2xx(upload);
        return UUID.fromString(String.valueOf(((Map<?, ?>) upload.getBody().get("data")).get("id")));
    }

    private ResponseEntity<Map> uploadImage(String token, String folder) {
        return authedMultipart(
                token,
                "/api/admin-ui/media/images",
                "file",
                pngBytes(2400, 1200),
                "cover.png",
                "image/png",
                Map.of("folder", folder)
        );
    }

    private void assertAttached(UUID fileId, UUID productId, String type) {
        Map<String, Object> row = jdbc.queryForMap(
                "select fileable_type, fileable_id::text as fileable_id, type, deleted_at from file_contents where id = ?::uuid",
                fileId.toString()
        );
        assertThat(row.get("fileable_type")).isEqualTo("product");
        assertThat(row.get("fileable_id")).isEqualTo(productId.toString());
        assertThat(row.get("type")).isEqualTo(type);
        assertThat(row.get("deleted_at")).isNull();
    }

    private Path toStoredPath(String publicPath) {
        return mediaRoot().resolve(publicPath).normalize();
    }

    private byte[] pngBytes(int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(new Color(40, 120, 220, 255));
            graphics.fillRect(0, 0, width, height);
            graphics.dispose();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
