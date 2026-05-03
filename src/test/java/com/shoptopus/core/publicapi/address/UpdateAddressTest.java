package com.shoptopus.core.publicapi.address;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateAddressTest extends AddressApiTestBase {

    @Test
    void unauthorised_users_are_not_allowed_to_update_addresses() {
        String ownerToken = registerAndLogin("address-update-owner-" + UUID.randomUUID() + "@example.com");
        String addressId = createAddress(ownerToken);

        var unauthenticated = exchange(HttpMethod.PATCH, "/api/address/" + addressId, null, validPayload("10 New St"));
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String otherToken = registerAndLogin("address-update-other-" + UUID.randomUUID() + "@example.com");
        var unauthorized = exchange(HttpMethod.PATCH, "/api/address/" + addressId, otherToken, validPayload("11 New St"));
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(unauthorized.getBody().get("developer_message")).isEqualTo("This action is unauthorized.");
    }

    @Test
    void authorised_users_can_update_their_addresses() {
        String ownerEmail = "address-update-ok-" + UUID.randomUUID() + "@example.com";
        String ownerToken = registerAndLogin(ownerEmail);
        UUID ownerId = userIdByEmail(ownerEmail);
        String addressId = createAddress(ownerToken);

        var updated = exchange(HttpMethod.PATCH, "/api/address/" + addressId, ownerToken, validPayload("10A Couzens Place"));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer count = jdbc.queryForObject("""
                select count(*)
                from addresses
                where id = ?::uuid
                  and user_id = ?::uuid
                  and address_line_1 = '10A Couzens Place'
                  and town = 'Bristol'
                  and post_code = 'BS34 8PL'
                  and country = 'UK'
                  and name = 'home'
                """, Integer.class, addressId, ownerId.toString());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void longitude_and_latitude_must_be_valid_coordinates() {
        String ownerToken = registerAndLogin("address-update-latlon-" + UUID.randomUUID() + "@example.com");
        String addressId = createAddress(ownerToken);

        var invalid = exchange(HttpMethod.PATCH, "/api/address/" + addressId, ownerToken, Map.of(
                "address_line_1", "10A Couzens Place",
                "town", "Bristol",
                "post_code", "BS34 8PL",
                "country", "UK",
                "name", "home",
                "lat", "2000",
                "lon", "2000.768"
        ));
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(invalid.getBody().get("errors"))).contains("lat", "lon");
    }

    private String createAddress(String token) {
        var created = exchange(HttpMethod.POST, "/api/addresses", token, Map.of(
                "address_line_1", "1 Update St",
                "town", "Bristol",
                "post_code", "BS1 1AA",
                "country", "UK",
                "name", "home"
        ));
        return ((Map<?, ?>) created.getBody().get("data")).get("id").toString();
    }

    private Map<String, Object> validPayload(String line1) {
        return Map.of(
                "address_line_1", line1,
                "town", "Bristol",
                "post_code", "BS34 8PL",
                "country", "UK",
                "name", "home"
        );
    }
}

