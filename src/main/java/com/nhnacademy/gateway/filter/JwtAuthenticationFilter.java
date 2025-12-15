/*
 * +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 * + Copyright 2025. NHN Academy Corp. All rights reserved.
 * + * While every precaution has been taken in the preparation of this resource,  assumes no
 * + responsibility for errors or omissions, or for damages resulting from the use of the information
 * + contained herein
 * + No part of this resource may be reproduced, stored in a retrieval system, or transmitted, in any
 * + form or by any means, electronic, mechanical, photocopying, recording, or otherwise, without the
 * + prior written permission.
 * +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 */

package com.nhnacademy.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    @Value("${jwt.secret}")
    private String secretKey;

    private final ReactiveStringRedisTemplate redisTemplate;
    private JwtParser jwtParser;

    public JwtAuthenticationFilter(ReactiveStringRedisTemplate redisTemplate) {
        super(Config.class);
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        jwtParser = Jwts.parser().verifyWith(key).build();
    }

    @Setter
    @Getter
    public static class Config{
        private boolean required = true;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return ((exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // Authorization 헤더 검사
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return config.isRequired()
                        ? onError(exchange, "Authorization header가 존재하지 않습니다.", HttpStatus.UNAUTHORIZED)
                        : chain.filter(exchange);
            }

            String authorizationHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            String token = extractJwt(authorizationHeader);

            // 토큰 형식 검사
            if (token == null) {
                return config.isRequired()
                        ? onError(exchange, "Authorization header의 형식이 올바르지 않습니다.", HttpStatus.UNAUTHORIZED)
                        : chain.filter(exchange);
            }

            // Redis 블랙리스트 확인 및 토큰 검증 (비동기 체이닝)
            return redisTemplate.hasKey("BL:" + token)
                    .flatMap(isBlacklisted -> {
                        if (Boolean.TRUE.equals(isBlacklisted)) {
                            return onError(exchange, "Token is blacklisted", HttpStatus.UNAUTHORIZED);
                        }
                        return processJwtValidation(exchange, chain, token);
                    });
        });
    }

    // JWT 검증 및 헤더 변환
    private Mono<Void> processJwtValidation(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain, String token) {
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            // 헤더에 정보 주입
            ServerHttpRequest newRequest = exchange.getRequest().mutate()
                    .header("X-Member-Id", claims.getSubject())
                    .header("X-Member-Role", claims.get("role", String.class))
                    .build();
            return chain.filter(exchange.mutate().request(newRequest).build());

        } catch (ExpiredJwtException e) {
            return onError(exchange, "Token has expired", HttpStatus.UNAUTHORIZED);
        } catch (Exception e) {
            log.error("JWT Verification Failed: {}", e.getMessage());
            return onError(exchange, "Invalid Token", HttpStatus.UNAUTHORIZED);
        }
    }

    private String extractJwt(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        log.error("Gateway Filter Error: {}", err);

        byte[] bytes = err.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        response.getHeaders().add("Content-Type", "application/json");
        return response.writeWith(Mono.just(buffer));
    }
}