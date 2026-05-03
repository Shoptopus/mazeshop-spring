package com.shoptopus.core.publicapi.address;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetAddressTest extends AddressApiTestBase {

    @Test
    void unauthenticated_users_are_not_allowed_to_get_addresses() {
        String ownerToken = registerAndLogin("address-get-owner-" + UUID.randomUUID() + "@example.com");
        String addressId = createAddress(ownerToken, "1 Get St");

        var unauthenticated = exchange(HttpMethod.GET, "/api/address/" + addressId, null, null);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String otherToken = registerAndLogin("address-get-other-" + UUID.randomUUID() + "@example.com");
        var unauthorized = exchange(HttpMethod.GET, "/api/address/" + addressId, otherToken, null);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(unauthorized.getBody().get("developer_message")).isEqualTo("This action is unauthorized.");
    }

    @Test
    void it_can_return_an_address_by_its_id_and_required_fields() {
        String ownerToken = registerAndLogin("address-get-ok-" + UUID.randomUUID() + "@example.com");
        String addressId = createAddress(ownerToken, "10A Couzens Place");

        var res = exchange(HttpMethod.GET, "/api/address/" + addressId, ownerToken, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) res.getBody().get("data");
        assertThat(data.get("id").toString()).isEqualTo(addressId);
        assertThat(data.get("address_line_1")).isEqualTo("10A Couzens Place");
        assertThat(data.keySet().stream().map(String::valueOf).toList())
                .contains("name", "address_line_1", "address_line_2", "town", "post_code", "country", "lat", "lon");
    }

    private String createAddress(String token, String line1) {
        var created = exchange(HttpMethod.POST, "/api/addresses", token, Map.of(
                "address_line_1", line1,
                "town", "Bristol",
                "post_code", "BS34 8PL",
                "country", "UK",
                "name", "home"
        ));
        return ((Map<?, ?>) created.getBody().get("data")).get("id").toString();
    }
}
