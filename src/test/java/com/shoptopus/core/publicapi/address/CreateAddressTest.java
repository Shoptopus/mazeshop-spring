package com.shoptopus.core.publicapi.address;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateAddressTest extends AddressApiTestBase {

    @Test
    void unauthenticated_users_are_not_allowed_to_create_addresses() {
        var res = exchange(HttpMethod.POST, "/api/addresses", null, Map.of(
                "address_line_1", "10 Test St",
                "town", "Bristol",
                "post_code", "BS1 1AA",
                "country", "UK"
        ));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody().get("user_message")).isEqualTo("Unauthenticated");
    }

    @Test
    void it_has_all_required_fields() {
        String email = "address-required-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);

        var res = exchange(HttpMethod.POST, "/api/addresses", token, Map.of(
                "address_line_1", "",
                "town", "",
                "post_code", "",
                "country", ""
        ));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(res.getBody().get("errors")))
                .contains("addressLine1", "town", "postCode", "country");
    }

    @Test
    void authenticated_users_can_create_addresses_and_it_is_saved_for_current_user() {
        String email = "address-create-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        UUID userId = userIdByEmail(email);

        var res = exchange(HttpMethod.POST, "/api/addresses", token, Map.of(
                "address_line_1", "10A Couzens Place",
                "town", "Bristol",
                "post_code", "BS34 8PL",
                "country", "UK",
                "name", "home"
        ));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        String id = ((Map<?, ?>) res.getBody().get("data")).get("id").toString();
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
                """, Integer.class, id, userId.toString());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void longitude_and_latitude_must_be_valid_coordinates() {
        String email = "address-latlon-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);

        var res = exchange(HttpMethod.POST, "/api/addresses", token, Map.of(
                "address_line_1", "10A Couzens Place",
                "town", "Bristol",
                "post_code", "BS34 8PL",
                "country", "UK",
                "lat", "2000",
                "lon", "2000.768"
        ));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(res.getBody().get("errors"))).contains("lat", "lon");
    }
}

