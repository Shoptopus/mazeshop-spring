package com.shoptopus.core.ordering;

import com.shoptopus.core.ordering.dto.OrderResponse;
import com.shoptopus.core.ordering.repository.OrderRepository;
import com.shoptopus.core.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    public List<OrderResponse> getAll(String userEmail) {
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail).orElseThrow();
        return orderRepository.findByUserIdAndDeletedAtIsNull(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public OrderResponse get(UUID id, String userEmail) {
        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(userEmail).orElseThrow();
        var order = orderRepository.findById(id).orElseThrow();
        if (order.getUser() == null || !order.getUser().getId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        }
        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUser() == null ? null : order.getUser().getId(),
                order.getAddress().getId(),
                order.getTotalPrice(),
                order.getSubtotal(),
                order.getTotalDiscount(),
                order.getStatus(),
                order.getCurrencyCode()
        );
    }
}
