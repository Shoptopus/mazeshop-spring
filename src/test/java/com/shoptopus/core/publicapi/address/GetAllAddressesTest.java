package com.shoptopus.core.publicapi.address;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetAllAddressesTest extends AddressApiTestBase {

    @Test
    void unauthenticated_users_are_not_allowed_to_get_all_their_addresses() {
        var res = exchange(HttpMethod.GET, "/api/addresses", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void it_returns_correct_format_count_fields_and_only_non_deleted_user_addresses() {
        String email = "address-list-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);

        String id1 = createAddress(token, "1 List St");
        String id2 = createAddress(token, "2 List St");
        String id3 = createAddress(token, "3 List St");
        exchange(HttpMethod.DELETE, "/api/address/" + id3, token, null);

        var res = exchange(HttpMethod.GET, "/api/addresses", token, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().keySet()).contains("message", "data", "records", "total_records");
        assertThat(((Number) res.getBody().get("total_records")).intValue()).isEqualTo(2);

        var data = (java.util.List<Map<String, Object>>) res.getBody().get("data");
        assertThat(data).hasSize(2);
        assertThat(data.stream().map(d -> d.get("id").toString())).contains(id1, id2).doesNotContain(id3);
        assertThat(data.get(0).keySet()).contains("id", "name", "address_line_1", "address_line_2", "town", "post_code", "country", "lat", "lon");
    }

    @Test
    void addresses_can_be_filtered_by_id_and_accept_multiple_parameters() {
        String email = "address-filter-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);

        String id1 = createAddress(token, "11 Filter St");
        String id2 = createAddress(token, "12 Filter St");
        createAddress(token, "13 Filter St");

        var single = exchange(HttpMethod.GET, "/api/addresses?filter[id]=" + id2, token, null);
        assertThat(single.getStatusCode()).isEqualTo(HttpStatus.OK);
        var singleData = (java.util.List<Map<String, Object>>) single.getBody().get("data");
        assertThat(singleData).hasSize(1);
        assertThat(singleData.get(0).get("id").toString()).isEqualTo(id2);

        var multi = exchange(HttpMethod.GET, "/api/addresses?filter[id]=" + id1 + "," + id2, token, null);
        assertThat(multi.getStatusCode()).isEqualTo(HttpStatus.OK);
        var multiData = (java.util.List<Map<String, Object>>) multi.getBody().get("data");
        assertThat(multiData).hasSize(2);
        assertThat(multiData.stream().map(d -> d.get("id").toString())).containsExactlyInAnyOrder(id1, id2);
    }

    private String createAddress(String token, String line1) {
        var created = exchange(HttpMethod.POST, "/api/addresses", token, Map.of(
                "address_line_1", line1,
                "town", "Cardiff",
                "post_code", "CF10 1AA",
                "country", "UK",
                "name", "home"
        ));
        return ((Map<?, ?>) created.getBody().get("data")).get("id").toString();
    }
}
