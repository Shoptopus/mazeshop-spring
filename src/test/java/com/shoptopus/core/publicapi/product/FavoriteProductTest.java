package com.shoptopus.core.publicapi.product;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FavoriteProductTest extends ProductApiTestBase {

    @Test
    void unauthenticated_users_are_not_allowed_to_favorite_products() {
        var productId = ensureProduct();
        var res = exchange(HttpMethod.POST, "/api/product/" + productId + "/favorite", null, Map.of());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void it_requires_valid_product_id() {
        String email = "favorite-invalid-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        var res = exchange(HttpMethod.POST, "/api/product/" + UUID.randomUUID() + "/favorite", token, Map.of());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("developer_message")).isEqualTo("Product not found");
    }

    @Test
    void authorised_users_can_favorite_and_unfavorite_products() {
        UUID productId = ensureProduct();
        String email = "favorite-ok-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        UUID userId = userIdByEmail(email);
        Long initialFavoriteCount = jdbc.queryForObject(
                "select favourites from products where id = ?::uuid",
                Long.class,
                productId.toString()
        );

        var favorite = exchange(HttpMethod.POST, "/api/product/" + productId + "/favorite", token, Map.of());
        assertThat(favorite.getStatusCode()).isEqualTo(HttpStatus.OK);
        var favoriteData = (Map<?, ?>) favorite.getBody().get("data");
        assertThat(favoriteData.get("product_id")).isEqualTo(productId.toString());
        assertThat(favoriteData.get("favorited")).isEqualTo(true);
        Integer afterFavorite = jdbc.queryForObject(
                "select count(*) from favorited_products where user_id = ?::uuid and product_id = ?::uuid",
                Integer.class,
                userId.toString(),
                productId.toString()
        );
        assertThat(afterFavorite).isEqualTo(1);
        Long favoriteCount = jdbc.queryForObject(
                "select favourites from products where id = ?::uuid",
                Long.class,
                productId.toString()
        );
        assertThat(favoriteCount).isEqualTo((initialFavoriteCount == null ? 0L : initialFavoriteCount) + 1L);

        var unfavorite = exchange(HttpMethod.POST, "/api/product/" + productId + "/favorite", token, Map.of());
        assertThat(unfavorite.getStatusCode()).isEqualTo(HttpStatus.OK);
        var unfavoriteData = (Map<?, ?>) unfavorite.getBody().get("data");
        assertThat(unfavoriteData.get("product_id")).isEqualTo(productId.toString());
        assertThat(unfavoriteData.get("favorited")).isEqualTo(false);
        Integer afterUnfavorite = jdbc.queryForObject(
                "select count(*) from favorited_products where user_id = ?::uuid and product_id = ?::uuid",
                Integer.class,
                userId.toString(),
                productId.toString()
        );
        assertThat(afterUnfavorite).isEqualTo(0);
        Long favoriteCountAfter = jdbc.queryForObject(
                "select favourites from products where id = ?::uuid",
                Long.class,
                productId.toString()
        );
        assertThat(favoriteCountAfter).isEqualTo(initialFavoriteCount == null ? 0L : initialFavoriteCount);
    }
}
