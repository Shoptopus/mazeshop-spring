package com.shoptopus.core.ordering;

import com.shoptopus.core.common.api.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders")
    public ApiResponse<?> getAll(Authentication authentication) {
        var rows = orderService.getAll(authentication.getName());
        return ApiResponse.paged("Orders", rows, rows.size(), (long) rows.size(), null);
    }

    @GetMapping("/order/{id}")
    public ApiResponse<?> get(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.ok("Order", orderService.get(id, authentication.getName()));
    }
}
