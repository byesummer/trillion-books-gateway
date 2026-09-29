package com.nhnacademy.gateway.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;


@Disabled("벤치마크")
class JwtValidatorBenchmarkTest {

    private static final String SECRET = "local-dev-secret-please-change-0123456789abcdef";

    @Test
    void measureLocalVerificationCost() {
        SecretKey signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        JwtValidator jwtValidator = new JwtValidator(SECRET);

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();
        ReactiveStringRedisTemplate redisTemplate = new ReactiveStringRedisTemplate(connectionFactory);

        Date now = new Date();
        String token = Jwts.builder()
                .subject("1")
                .claim("category", "ACCESS_TOKEN")
                .claim("role", "MEMBER")
                .issuedAt(new Date(now.getTime() - 60_000))
                .expiration(new Date(now.getTime() + 30 * 60 * 1000))
                .signWith(signingKey)
                .compact();

        int warmup = 10;
        int rounds = 100;
        long[] samples = new long[rounds];

        for (int i = 0; i < warmup; i++) {
            jwtValidator.validateAccessToken(token);
            redisTemplate.hasKey("BL:" + token).block();
        }

        for (int i = 0; i < rounds; i++) {
            long start = System.nanoTime();
            jwtValidator.validateAccessToken(token);
            redisTemplate.hasKey("BL:" + token).block();
            samples[i] = System.nanoTime() - start;
        }

        connectionFactory.destroy();

        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        double avgMs = Arrays.stream(samples).average().orElse(0) / 1_000_000.0;
        double p50Ms = sorted[(int) (rounds * 0.5)] / 1_000_000.0;
        double p95Ms = sorted[(int) (rounds * 0.95)] / 1_000_000.0;

        System.out.printf("avg=%.4fms  p50=%.4fms  p95=%.4fms%n", avgMs, p50Ms, p95Ms);
    }
}
