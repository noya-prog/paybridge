package com.noya.payBridge.domain.repository;

import com.noya.payBridge.domain.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository <Merchant, UUID> {
    Optional<Merchant> findByEmail(String email);

    Optional<Merchant> findByApiKey(String apiKey);

    Optional<Merchant> findByMerchantCode(String merchantCode);

    boolean existsByEmail(String email);

    boolean existsByMerchantCode(String merchantCode);
}
