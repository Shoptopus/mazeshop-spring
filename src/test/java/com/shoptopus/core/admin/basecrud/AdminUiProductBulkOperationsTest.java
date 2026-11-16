package com.shoptopus.core.admin.basecrud;

import com.shoptopus.core.adminui.AdminStoreContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.multistore.enabled=true")
class AdminUiProductBulkOperationsTest extends AdminControllerTestBase {

    private static final String PATH = "/api/admin-ui/products";
    private final List<UUID> products = new ArrayList<>();
    private final List<UUID> stores = new ArrayList<>();

    @AfterEach
    void removeFixtures() {
        products.forEach(id -> jdbc.update("delete from file_contents where fileable_type='product' and fileable_id=?", id));
        products.forEach(id -> jdbc.update("delete from products where id=?", id));
        stores.forEach(id -> jdbc.update("delete from stores where id=?", id));
    }

    @Test
    void studio_can_enable_disable_archive_and_delete_exactly_the_selected_products() throws Exception {
        String token = token();
        UUID store = defaultStore();
        String prefix = "bulk-studio-" + UUID.randomUUID();
        UUID first = seedProduct(store, prefix, 0);
        UUID second = seedProduct(store, prefix, 2);
        UUID untouched = seedProduct(store, prefix, 0);
        var body = Map.of("ids", List.of(first, second, first));
        UUID fileId = UUID.randomUUID();
        String filename = fileId + ".jpg";
        Files.createDirectories(mediaRoot());
        var mediaFile = Files.writeString(mediaRoot().resolve(filename), "test attachment");
        try {
            jdbc.update("""
                    insert into file_contents(id, name, mime_type, size, path, slug, fileable_type, fileable_id, type, created_at, updated_at)
                    values (?, 'Bulk image', 'image/jpeg', 15, ?, ?, 'product', ?, 'cover_photo', now(), now())
                    """, fileId, filename, filename, first);
            var enabled = exchange(token, store, HttpMethod.POST, PATH + "/bulk/enable", body);
            assert2xx(enabled);
            assertThat(((Map<?, ?>) enabled.getBody().get("data")).get("count")).isEqualTo(2);
            assertThat(List.of(status(first), status(second), status(untouched))).containsExactly(1, 1, 0);
            var active = exchange(token, store, HttpMethod.GET, PATH + "/selection?status=active&search=" + prefix, null);
            assertThat(selectedIds(active)).containsExactlyInAnyOrder(first.toString(), second.toString());

            assert2xx(exchange(token, store, HttpMethod.POST, PATH + "/bulk/disable", body));
            assertThat(List.of(status(first), status(second))).containsExactly(0, 0);
            assertThat(selectedIds(exchange(token, store, HttpMethod.GET, PATH + "/selection?status=active&search=" + prefix, null))).isEmpty();
            assert2xx(exchange(token, store, HttpMethod.POST, PATH + "/bulk/archive", body));
            assertThat(List.of(status(first), status(second), status(untouched))).containsExactly(2, 2, 0);
            assertThat(mediaFile).exists();

            assert2xx(exchange(token, store, HttpMethod.POST, PATH + "/bulk/delete", body));
            assertThat(List.of(deleted(first), deleted(second), deleted(untouched))).containsExactly(true, true, false);
            assertThat(mediaFile).doesNotExist();
            assertThat(selectedIds(exchange(token, store, HttpMethod.GET, PATH + "/selection?search=" + prefix, null)))
                    .containsExactly(untouched.toString());
        } finally {
            Files.deleteIfExists(mediaFile);
        }
    }

    @Test
    void selection_filters_by_store_and_rejects_mixed_or_stale_batches_without_changes() {
        String token = token();
        UUID local = defaultStore();
        UUID other = UUID.randomUUID();
        jdbc.update("insert into stores(id,name,slug,status,created_at,updated_at) values (?, 'Bulk test', ?, 'ACTIVE', now(), now())", other, "bulk-test-" + other);
        stores.add(other);
        String prefix = "bulk-scope-" + UUID.randomUUID();
        UUID allowed = seedProduct(local, prefix, 0);
        UUID outside = seedProduct(other, prefix, 0);
        UUID removed = seedProduct(local, prefix, 0);
        jdbc.update("update products set deleted_at=now() where id=?", removed);

        assertThat(selectedIds(exchange(token, local, HttpMethod.GET, PATH + "/selection?search=" + prefix, null)))
                .containsExactly(allowed.toString());
        assertThat(selectedIds(exchange(token, other, HttpMethod.GET, PATH + "/selection?search=" + prefix, null)))
                .containsExactly(outside.toString());
        for (UUID invalid : List.of(outside, removed, UUID.randomUUID())) {
            assertThat(exchange(token, local, HttpMethod.POST, PATH + "/bulk/archive", Map.of("ids", List.of(allowed, invalid)))
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(exchange(token, local, HttpMethod.POST, PATH + "/bulk/delete", Map.of("ids", List.of(allowed, invalid)))
                    .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(status(allowed)).isZero();
            assertThat(deleted(allowed)).isFalse();
        }
        assertThat(status(outside)).isZero();
        assertThat(deleted(outside)).isFalse();
    }

    @Test
    void bulk_actions_require_manage_permission_and_well_formed_selections() {
        UUID store = defaultStore();
        UUID product = seedProduct(store, "bulk-permission", 0);
        String email = "product-viewer-" + UUID.randomUUID() + "@example.com";
        assert2xx(restTemplate.postForEntity("/api/auth/register", Map.of("first_name", "Product", "last_name", "Viewer",
                "email", email, "password", "password123", "password_confirmation", "password123"), Map.class));
        String role = "product-viewer-" + UUID.randomUUID();
        assignRole(email, role);
        assignPermissionToRole(role, "products.view");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of("email", email, "password", "password123"), Map.class);
        String viewer = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");
        assert2xx(exchange(viewer, store, HttpMethod.GET, PATH, null));
        assertThat(exchange(viewer, store, HttpMethod.GET, PATH + "/selection", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(viewer, store, HttpMethod.POST, PATH + "/bulk/enable", Map.of("ids", List.of(product)))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String manager = token();
        for (Object ids : List.of(List.of(), List.of(product, "bad-id"), "all")) {
            assertThat(exchange(manager, store, HttpMethod.POST, PATH + "/bulk/enable", Map.of("ids", ids))
                    .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(exchange(manager, store, HttpMethod.POST, PATH + "/bulk/publish", Map.of("ids", List.of(product)))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(status(product)).isZero();
    }

    private ResponseEntity<Map> exchange(String token, UUID store, HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set(AdminStoreContext.STORE_HEADER, store.toString());
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private List<String> selectedIds(ResponseEntity<Map> response) {
        assert2xx(response);
        return ((List<?>) ((Map<?, ?>) response.getBody().get("data")).get("ids")).stream()
                .map(String.class::cast).toList();
    }

    private UUID defaultStore() {
        return jdbc.queryForObject("select id from stores where deleted_at is null order by created_at asc limit 1", UUID.class);
    }

    private UUID seedProduct(UUID store, String prefix, int status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into products(id, store_id, name, slug, short_description, description, price, stock, virtual, status, purchase_count, created_at, updated_at)
                values (?, ?, '{"en":"Studio bulk product"}'::jsonb, ?, '{}'::jsonb, '{}'::jsonb, 10, 4, false, ?, 0, now(), now())
                """, id, store, prefix + "-" + id, status);
        products.add(id);
        return id;
    }

    private int status(UUID id) {
        return jdbc.queryForObject("select status from products where id=?", Integer.class, id);
    }

    private boolean deleted(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select deleted_at is not null from products where id=?", Boolean.class, id));
    }
}
