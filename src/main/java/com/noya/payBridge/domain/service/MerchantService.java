package com.noya.payBridge.domain.service;

import com.noya.payBridge.api.dto.LoginRequest;
import com.noya.payBridge.api.dto.MerchantResponse;
import com.noya.payBridge.api.dto.RegisterMerchantRequest;
import com.noya.payBridge.api.dto.TokenResponse;
import com.noya.payBridge.domain.entity.Merchant;
import com.noya.payBridge.domain.repository.MerchantRepository;
import com.noya.payBridge.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Register a new merchant
     */
    @Transactional
    public MerchantResponse register(RegisterMerchantRequest request) {
        log.info("Registering new merchant with email: {}", request.getEmail());

        if (merchantRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: email already exists: {}", request.getEmail());
            throw new IllegalArgumentException("Email already registered");
        }

        String apiKey = jwtTokenProvider.generateApiKey();
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        String merchantCode = generateUniqueMerchantCode(request.getName());

        Merchant merchant = Merchant.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(hashedPassword)
                .merchantCode(merchantCode)
                .apiKey(apiKey)
                .isActive(true)
                .build();

        Merchant savedMerchant = merchantRepository.save(merchant);
        log.info("Merchant registered successfully: {} ({})", savedMerchant.getId(), savedMerchant.getMerchantCode());

        return mapToResponse(savedMerchant);
    }

    /**
     * Login merchant and return JWT token
     */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        Merchant merchant = merchantRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed: merchant not found: {}", request.getEmail());
                    return new IllegalArgumentException("Invalid email or password");
                });

        if (!merchant.getIsActive()) {
            log.warn("Login failed: merchant account is inactive: {}", merchant.getId());
            throw new IllegalArgumentException("Account is inactive");
        }

        if (!passwordEncoder.matches(request.getPassword(), merchant.getPassword())) {
            log.warn("Login failed: invalid password for: {}", request.getEmail());
            throw new IllegalArgumentException("Invalid email or password");
        }

        String token = jwtTokenProvider.generateToken(merchant.getId(), merchant.getEmail());
        Long expiresIn = jwtTokenProvider.getExpirationInSeconds();

        log.info("Login successful for merchant: {} ({})", merchant.getId(), merchant.getMerchantCode());

        return TokenResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .build();
    }

    @Transactional(readOnly = true)
    public MerchantResponse getMerchantById(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found"));
        return mapToResponse(merchant);
    }

    @Transactional(readOnly = true)
    public MerchantResponse getMerchantByEmail(String email) {
        Merchant merchant = merchantRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found"));
        return mapToResponse(merchant);
    }

    @Transactional(readOnly = true)
    public MerchantResponse getMerchantByCode(String merchantCode) {
        Merchant merchant = merchantRepository.findByMerchantCode(merchantCode)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found"));
        return mapToResponse(merchant);
    }

    @Transactional(readOnly = true)
    public boolean isValidApiKey(String apiKey) {
        return merchantRepository.findByApiKey(apiKey)
                .map(Merchant::getIsActive)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Merchant getMerchantByApiKey(String apiKey) {
        return merchantRepository.findByApiKey(apiKey)
                .filter(Merchant::getIsActive)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or inactive API key"));
    }

    @Transactional
    public String regenerateApiKey(UUID merchantId) {
        log.info("Regenerating API key for merchant: {}", merchantId);

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found"));

        String newApiKey = jwtTokenProvider.generateApiKey();
        merchant.setApiKey(newApiKey);
        merchantRepository.save(merchant);

        log.info("API key regenerated for merchant: {}", merchantId);
        return newApiKey;
    }

    // ============================================================
    // Private helper methods
    // ============================================================

    /**
     * Generate a unique human-readable merchant code, e.g. MCH-7F3A2B
     * Retries on the rare collision.
     */
    private String generateUniqueMerchantCode(String name) {
        // Take first 3 letters of name, uppercase, letters only
        String prefix = name.toUpperCase()
                .replaceAll("[^A-Z]", "") // remove non-letters
                .substring(0, Math.min(3, name.replaceAll("[^a-zA-Z]", "").length()));
        while (prefix.length() < 3){
            prefix += "X";
        }
        String code;
        do {
            code = "MCH-" + prefix + "-" + randomCodeSuffix(4);
        } while (merchantRepository.existsByMerchantCode(code));
        return code;
    }

    private String randomCodeSuffix(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private MerchantResponse    mapToResponse(Merchant merchant) {
        return MerchantResponse.builder()
                .id(merchant.getId())
                .name(merchant.getName())
                .email(merchant.getEmail())
                .merchantCode(merchant.getMerchantCode())
                .apiKey(merchant.getApiKey())
                .isActive(merchant.getIsActive())
                .createdAt(merchant.getCreatedAt())
                .updatedAt(merchant.getUpdatedAt())
                .build();
    }
}