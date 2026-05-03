package com.shoptopus.core.unit;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.user.Address;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.AddressRepository;
import com.shoptopus.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AddressTest extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Test
    void it_has_expected_fields_and_belongs_to_user() {
        User user = createUser("address-user");

        Address address = new Address();
        address.setUser(user);
        address.setSlug("address-" + UUID.randomUUID());
        address.setName("Home");
        address.setAddressLine1("1 Test St");
        address.setAddressLine2("Flat 2");
        address.setTown("London");
        address.setPostCode("E1 1AA");
        address.setCountry("UK");
        address.setLat("51.5072");
        address.setLon("-0.1276");
        Address saved = addressRepository.save(address);

        Address loaded = addressRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getAddressLine1()).isEqualTo("1 Test St");
        assertThat(loaded.getAddressLine2()).isEqualTo("Flat 2");
        assertThat(loaded.getTown()).isEqualTo("London");
        assertThat(loaded.getPostCode()).isEqualTo("E1 1AA");
        assertThat(loaded.getCountry()).isEqualTo("UK");
        assertThat(loaded.getName()).isEqualTo("Home");
        assertThat(loaded.getLat()).isEqualTo("51.5072");
        assertThat(loaded.getLon()).isEqualTo("-0.1276");
        assertThat(loaded.getSlug()).startsWith("address-");
        assertThat(loaded.getUser().getId()).isEqualTo(user.getId());
    }

    private User createUser(String prefix) {
        User user = new User();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user.setFirstName(prefix);
        user.setLastName("test");
        user.setName(prefix + " test");
        user.setEmail(prefix + "-" + suffix + "@example.com");
        user.setSlug("user-" + UUID.randomUUID());
        user.setTemporary(false);
        return userRepository.save(user);
    }
}
