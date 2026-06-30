package com.noya.payBridge.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:your-secret-key-change-this-in-production-at-least-32-characters-long}")
    private String jwtSecret;
    @Value("${jwt.expiration: 3600000}")
    private long jwtExpirationMs;

    /*
     * Generate a JWT token for a merchant
     * @return JWT token string
     */
    public  String generateToken(UUID merchantId, String email){
        Map<String, Object> claims = new HashMap<>();
        claims.put("merchantId", merchantId.toString());
        claims.put("email", email);
        claims.put("type", "access");
        return createToken(claims, merchantId.toString());
    }
/*
 * Generate an API key (long-lived token for server-to-server)
 * @return API key
 */
    public String generateApiKey(){
        return UUID.randomUUID().toString() + UUID.randomUUID().toString();
    }
    /*
     * Validate a JWT token
     * @param token the JWT token string
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String token){
        try {
            Jwts.parser().verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }
    /*
     * Extract merchant ID from token
     * @return merchant ID
     */
    public UUID getMerchantIdFromToken(String token){
        Claims claims = getAllClaimsFromToken(token);
        String merchantId = claims.get("merchantId", String.class);
        return UUID.fromString(merchantId);
    }
    /*
     * Extract email from token
     * @return email
     */
    public String getEmailFromToken(String token){
        return getAllClaimsFromToken(token).get("email", String.class);
    }
    /*
     * Check if token is expired
     * @return true if expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e){
            return true;
        }
    }

    public Long getExpirationInSeconds() {
        return jwtExpirationMs / 1000;
    }

    // helper methods

    private String createToken(Map<String, Object> claims, String subject){
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }
    private SecretKey getSigningKey(){
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    private Claims getAllClaimsFromToken(String token){
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}
