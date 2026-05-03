package com.shoptopus.core.user.repository;

import com.shoptopus.core.user.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {
    List<Address> findByUserIdAndDeletedAtIsNull(UUID userId);
}
