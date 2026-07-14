package com.noya.payBridge.api.controller;

import com.noya.payBridge.api.dto.LoginRequest;
import com.noya.payBridge.api.dto.MerchantResponse;
import com.noya.payBridge.api.dto.RegisterMerchantRequest;
import com.noya.payBridge.api.dto.TokenResponse;
import com.noya.payBridge.domain.service.MerchantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final MerchantService merchantService;

//    Register a new merchant
    @PostMapping("/register")
    public ResponseEntity<MerchantResponse> register(@Valid @RequestBody RegisterMerchantRequest request){
        log.info("Register request received for email: {}", request.getEmail());
        MerchantResponse response = merchantService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

//    Login merchant and get JWT token
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request){
        log.info("Login request received for email: {}", request.getEmail());
        TokenResponse response = merchantService.login(request);
        return ResponseEntity.ok(response);
    }

//    Get current merchant profile
    @GetMapping("/me")
    public ResponseEntity<MerchantResponse> getCurrentMerchant(Authentication authentication) {
    UUID merchantId = (UUID) authentication.getPrincipal();
    MerchantResponse response = merchantService.getMerchantById(merchantId);
    return ResponseEntity.ok(response);
}
//    Regenerate API key for current merchant
    @PostMapping("/regenerate-api-key")
    public ResponseEntity<String> regenerateApiKey(Authentication authentication){
        UUID merchantId = (UUID) authentication.getPrincipal();
        String newApiKey = merchantService.regenerateApiKey(merchantId);
        return ResponseEntity.ok(newApiKey);
    }
}
