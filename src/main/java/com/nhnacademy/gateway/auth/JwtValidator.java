package com.nhnacademy.gateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtValidator {

    private final SecretKey secretKey;

    public JwtValidator(@Value("${spring.jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public record ValidationResult(boolean valid, String failureReason, Long memberId, String role) {
        static ValidationResult success(Long memberId, String role) {
            return new ValidationResult(true, null, memberId, role);
        }
        static ValidationResult failure(String reason) {
            return new ValidationResult(false, reason, null, null);
        }
    }

    public ValidationResult validateAccessToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build()
                    .parseSignedClaims(token).getPayload();

            if (!TokenKinds.ACCESS_TOKEN.name().equals(claims.get("category", String.class))) {
                return ValidationResult.failure("token_invalid");
            }
            Long memberId = Long.valueOf(claims.getSubject());
            String role = claims.get("role", String.class);
            return ValidationResult.success(memberId, role);
        } catch (ExpiredJwtException e) {
            return ValidationResult.failure("token_expired");
        } catch (JwtException | IllegalArgumentException e) {
            return ValidationResult.failure("token_invalid");
        }
    }
}