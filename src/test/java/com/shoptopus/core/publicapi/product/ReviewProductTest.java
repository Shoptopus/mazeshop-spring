package com.shoptopus.core.publicapi.product;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewProductTest extends ProductApiTestBase {

    @Test
    void product_review_can_be_saved() {
        UUID productId = ensureProduct();
        String email = "review-save-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        UUID userId = userIdByEmail(email);

        var res = exchange(HttpMethod.POST, "/api/product/" + productId + "/rating", token, Map.of(
                "rating", 5,
                "title", "Great",
                "description", "Excellent"
        ));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer rows = jdbc.queryForObject(
                "select count(*) from ratings where user_id = ?::uuid and ratable_type = 'product' and ratable_id = ?::uuid and rating = 5",
                Integer.class,
                userId.toString(),
                productId.toString()
        );
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void product_review_requires_valid_token() {
        UUID productId = ensureProduct();
        var res = exchange(HttpMethod.POST, "/api/product/" + productId + "/rating", null, Map.of("rating", 5));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void product_review_rating_needs_to_be_between_1_and_5() {
        UUID productId = ensureProduct();
        String token = registerAndLogin("review-range-" + UUID.randomUUID() + "@example.com");

        var tooLow = exchange(HttpMethod.POST, "/api/product/" + productId + "/rating", token, Map.of("rating", 0));
        assertThat(tooLow.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(tooLow.getBody().get("errors"))).contains("rating");

        var tooHigh = exchange(HttpMethod.POST, "/api/product/" + productId + "/rating", token, Map.of("rating", 6));
        assertThat(tooHigh.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(tooHigh.getBody().get("errors"))).contains("rating");
    }

    @Test
    void product_review_rating_has_all_required_fields() {
        UUID productId = ensureProduct();
        String token = registerAndLogin("review-required-" + UUID.randomUUID() + "@example.com");
        var res = exchange(HttpMethod.POST, "/api/product/" + productId + "/rating", token, Map.of());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(res.getBody().get("errors"))).contains("rating");
    }
}
