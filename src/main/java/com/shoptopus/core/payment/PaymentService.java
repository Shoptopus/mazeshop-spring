package com.shoptopus.core.payment;

import com.shoptopus.core.payment.dto.PaymentProviderSettingsResponse;
import com.shoptopus.core.payment.repository.PaymentProviderRepository;
import com.shoptopus.core.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class PaymentService {

    private final PaymentProviderRepository paymentProviderRepository;

    public PaymentService(PaymentProviderRepository paymentProviderRepository) {
        this.paymentProviderRepository = paymentProviderRepository;
    }

    @Transactional("mainTransactionManager")
    public PaymentProviderSettingsResponse getClientSettings(String provider) {
        var p = paymentProviderRepository.findByNameIgnoreCase(provider)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Unsupported payment provider"));
        var settings = p.getConfigs().stream()
                .filter(PaymentProviderConfig::isPublicValue)
                .collect(java.util.stream.Collectors.toMap(
                        PaymentProviderConfig::getSetting,
                        c -> p.isTestMode() && c.getTestValue() != null ? c.getTestValue() : c.getValue(),
                        (a, b) -> b,
                        java.util.LinkedHashMap::new
                ));
        if (settings.containsKey("publishable_key") && !settings.containsKey("public_key")) {
            settings.put("public_key", settings.get("publishable_key"));
        }
        return new PaymentProviderSettingsResponse(p.getName(), p.isEnabled(), settings);
    }

    public Map<String, String> execute(String provider, String paymentRef) {
        paymentProviderRepository.findByNameIgnoreCase(provider)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", "Unsupported payment provider"));
        return Map.of("provider", provider, "payment_ref", paymentRef, "status", "accepted");
    }
}
