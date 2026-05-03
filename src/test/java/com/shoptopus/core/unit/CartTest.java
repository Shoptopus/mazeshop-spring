package com.shoptopus.core.unit;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.cart.Cart;
import com.shoptopus.core.cart.CartItem;
import com.shoptopus.core.cart.repository.CartItemRepository;
import com.shoptopus.core.cart.repository.CartRepository;
import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.user.User;
import com.shoptopus.core.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CartTest extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void it_has_an_ip_address_field() {
        Cart cart = new Cart();
        cart.setSlug("cart-" + UUID.randomUUID());
        cart.setIpAddress("127.0.0.1");
        Cart saved = cartRepository.save(cart);
        assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void it_may_belong_to_a_user() {
        User user = createUser("cart-user");
        Cart cart = new Cart();
        cart.setUser(user);
        cart.setSlug("cart-" + UUID.randomUUID());
        Cart saved = cartRepository.save(cart);
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void it_may_belongs_to_many_products() {
        Cart cart = new Cart();
        cart.setSlug("cart-" + UUID.randomUUID());
        cart = cartRepository.save(cart);

        Product p1 = createProduct("Cart Product 1");
        Product p2 = createProduct("Cart Product 2");

        CartItem i1 = new CartItem();
        i1.setCart(cart);
        i1.setProduct(p1);
        i1.setQuantity(1L);
        cartItemRepository.save(i1);

        CartItem i2 = new CartItem();
        i2.setCart(cart);
        i2.setProduct(p2);
        i2.setQuantity(2L);
        cartItemRepository.save(i2);

        assertThat(cartItemRepository.findByCartId(cart.getId())).hasSize(2);
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

    private Product createProduct(String name) {
        Product p = new Product();
        p.setName(Map.of("en", name));
        p.setSlug(name.toLowerCase().replace(" ", "-") + "-" + UUID.randomUUID());
        p.setShortDescription(Map.of("en", "short"));
        p.setDescription(Map.of("en", "desc"));
        p.setPrice(BigDecimal.TEN);
        p.setStock(10L);
        p.setVirtual(false);
        return productRepository.save(p);
    }
}
