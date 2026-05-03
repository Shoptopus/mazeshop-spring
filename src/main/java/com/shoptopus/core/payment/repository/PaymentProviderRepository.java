package com.shoptopus.core.payment.repository;

import com.shoptopus.core.payment.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentProviderRepository extends JpaRepository<PaymentProvider, Long> {
    Optional<PaymentProvider> findByNameIgnoreCase(String name);
    java.util.List<PaymentProvider> findAllByOrderByNameAsc();
}
