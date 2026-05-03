package com.shoptopus.core.publicapi.address;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeleteAddressTest extends AddressApiTestBase {

    @Test
    void unauthorised_users_are_not_allowed_to_delete_addresses() {
        String ownerEmail = "address-delete-owner-" + UUID.randomUUID() + "@example.com";
        String ownerToken = registerAndLogin(ownerEmail);
        String addressId = createAddress(ownerToken);

        var unauthenticated = exchange(HttpMethod.DELETE, "/api/address/" + addressId, null, null);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String otherToken = registerAndLogin("address-delete-other-" + UUID.randomUUID() + "@example.com");
        var unauthorized = exchange(HttpMethod.DELETE, "/api/address/" + addressId, otherToken, null);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(unauthorized.getBody().get("developer_message")).isEqualTo("This action is unauthorized.");
    }

    @Test
    void authorised_users_can_delete_their_addresses() {
        String ownerEmail = "address-delete-ok-" + UUID.randomUUID() + "@example.com";
        String ownerToken = registerAndLogin(ownerEmail);
        String addressId = createAddress(ownerToken);

        var deletedAtBefore = jdbc.queryForObject(
                "select deleted_at from addresses where id = ?::uuid",
                OffsetDateTime.class,
                addressId
        );
        assertThat(deletedAtBefore).isNull();

        var res = exchange(HttpMethod.DELETE, "/api/address/" + addressId, ownerToken, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        var deletedAtAfter = jdbc.queryForObject(
                "select deleted_at from addresses where id = ?::uuid",
                OffsetDateTime.class,
                addressId
        );
        assertThat(deletedAtAfter).isNotNull();
    }

    private String createAddress(String token) {
        var created = exchange(HttpMethod.POST, "/api/addresses", token, Map.of(
                "address_line_1", "1 Delete St",
                "town", "London",
                "post_code", "E1 1AA",
                "country", "UK"
        ));
        return ((Map<?, ?>) created.getBody().get("data")).get("id").toString();
    }
}

