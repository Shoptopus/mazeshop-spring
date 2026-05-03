package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUiPageMenuControllerTest extends AdminControllerTestBase {

    @Test
    void page_menu_can_be_loaded_and_saved_with_shop_and_nested_pages() {
        String token = token();

        var homeCreate = authed(token, HttpMethod.POST, "/api/admin-ui/pages", Map.of(
                "title", Map.of("en", "Home " + UUID.randomUUID()),
                "slug", "home-" + UUID.randomUUID(),
                "status", 1,
                "show_in_menu", true,
                "home_page", true
        ));
        var aboutCreate = authed(token, HttpMethod.POST, "/api/admin-ui/pages", Map.of(
                "title", Map.of("en", "About " + UUID.randomUUID()),
                "slug", "about-" + UUID.randomUUID(),
                "status", 1,
                "show_in_menu", true,
                "home_page", false
        ));

        assert2xx(homeCreate);
        assert2xx(aboutCreate);

        String homeId = String.valueOf(dataObject(homeCreate).get("id"));
        String aboutId = String.valueOf(dataObject(aboutCreate).get("id"));

        var menu = authed(token, HttpMethod.GET, "/api/admin-ui/pages/menu", null);
        assertThat(menu.getStatusCode()).isEqualTo(HttpStatus.OK);
        var initialTree = list(dataObject(menu).get("tree"));
        assertThat(initialTree).isNotEmpty();
        assertThat(initialTree).extracting(item -> item.get("kind")).contains("shop");

        var update = authed(token, HttpMethod.PUT, "/api/admin-ui/pages/menu", Map.of(
                "tree", List.of(
                        Map.of("kind", "page", "page_id", homeId, "children", List.of(
                                Map.of("kind", "page", "page_id", aboutId, "children", List.of())
                        )),
                        Map.of("kind", "shop", "children", List.of())
                )
        ));
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);

        var updatedTree = list(dataObject(update).get("tree"));
        assertThat(updatedTree).hasSize(2);
        assertThat(updatedTree.get(0).get("kind")).isEqualTo("page");
        assertThat(updatedTree.get(1).get("kind")).isEqualTo("shop");
        assertThat(list(updatedTree.get(0).get("children"))).hasSize(1);
        assertThat(list(updatedTree.get(0).get("children")).get(0).get("page_id")).isEqualTo(aboutId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataObject(org.springframework.http.ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }
}
