package com.shoptopus.core.payment.repository;

import com.shoptopus.core.payment.PaymentProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProviderConfigRepository extends JpaRepository<PaymentProviderConfig, Long> {
}
