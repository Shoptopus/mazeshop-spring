package com.shoptopus.core.payment;

import com.shoptopus.core.common.api.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment")
@Validated
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{provider}/settings")
    public ApiResponse<?> getClientSettings(@PathVariable String provider) {
        return ApiResponse.ok("Payment settings", paymentService.getClientSettings(provider));
    }

    @PostMapping("/execute")
    public ApiResponse<?> execute(@RequestParam @NotBlank String provider, @RequestParam @NotBlank String paymentRef) {
        return ApiResponse.ok("Payment executed", paymentService.execute(provider, paymentRef));
    }
}
