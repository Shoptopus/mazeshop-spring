package com.shoptopus.core.mail;

import com.shoptopus.core.ordering.Order;
import com.shoptopus.core.ordering.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class StorefrontOrderMailService {

    private final ShopMailService shopMailService;
    private final OrderRepository orderRepository;

    public StorefrontOrderMailService(ShopMailService shopMailService, OrderRepository orderRepository) {
        this.shopMailService = shopMailService;
        this.orderRepository = orderRepository;
    }

    public void sendOrderConfirmed(String recipientEmail, String orderSlug, String orderId) {
        UUID parsedId;
        try {
            parsedId = UUID.fromString(orderId);
        } catch (IllegalArgumentException ex) {
            return;
        }
        Order order = orderRepository.findById(parsedId).orElse(null);
        if (order == null) {
            return;
        }
        shopMailService.sendOrderStatusUpdated(recipientEmail, order.getId(), order.getSlug(), "Confirmed");
    }
}
