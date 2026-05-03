package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUiFileOptimizationTest extends AdminControllerTestBase {

    @Test
    void files_endpoint_returns_image_dimensions_and_optimize_rewrites_image_as_jpg() throws Exception {
        String token = token();
        String relativePath = "files/optimize-" + UUID.randomUUID() + ".png";
        writeImage(relativePath, 2400, 1200);

        var create = authed(token, HttpMethod.POST, "/api/admin-ui/files", Map.of(
                "name", "catalogue-image",
                "mime_type", "image/png",
                "size", Files.size(mediaRoot().resolve(relativePath)),
                "path", relativePath
        ));
        assert2xx(create);
        String fileId = String.valueOf(((Map<?, ?>) create.getBody().get("data")).get("id"));
        assertThat(jdbc.queryForObject("select width from file_contents where id = ?::uuid", Integer.class, fileId)).isEqualTo(2400);
        assertThat(jdbc.queryForObject("select height from file_contents where id = ?::uuid", Integer.class, fileId)).isEqualTo(1200);

        var list = authed(token, HttpMethod.GET, "/api/admin-ui/files", null);
        assert2xx(list);
        Map<?, ?> row = ((List<Map<?, ?>>) ((Map<?, ?>) list.getBody().get("data")).get("items")).stream()
                .filter(item -> fileId.equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.get("width")).isEqualTo(2400);
        assertThat(row.get("height")).isEqualTo(1200);

        var optimize = authed(token, HttpMethod.POST, "/api/admin-ui/files/" + fileId + "/optimize", null);
        assert2xx(optimize);
        Map<?, ?> optimized = (Map<?, ?>) optimize.getBody().get("data");
        assertThat(optimized.get("mime_type")).isEqualTo("image/jpeg");
        assertThat(String.valueOf(optimized.get("path"))).endsWith(".jpg");
        assertThat(optimized.get("width")).isEqualTo(1800);
        assertThat(optimized.get("height")).isEqualTo(900);
        assertThat(jdbc.queryForObject("select width from file_contents where id = ?::uuid", Integer.class, fileId)).isEqualTo(1800);
        assertThat(jdbc.queryForObject("select height from file_contents where id = ?::uuid", Integer.class, fileId)).isEqualTo(900);

        Integer remainingPngs = jdbc.queryForObject(
                "select count(*) from file_contents where id = ?::uuid and path like '%.png'",
                Integer.class,
                fileId
        );
        assertThat(remainingPngs).isEqualTo(0);
        assertThat(mediaRoot().resolve(relativePath)).doesNotExist();
        assertThat(mediaRoot().resolve(String.valueOf(optimized.get("path")))).exists();
    }

    private void writeImage(String relativePath, int width, int height) throws Exception {
        Path target = mediaRoot().resolve(relativePath).normalize();
        Files.createDirectories(target.getParent());
        Files.write(target, pngBytes(width, height));
    }

    private byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(220, 120, 40, 255));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
