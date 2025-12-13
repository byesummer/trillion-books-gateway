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
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.SecretKey;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    @Value("${jwt.secret}")
    private String secretKey;
    private JwtParser jwtParser;

    @PostConstruct
    public void init() {
        SecretKey key;
        key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
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
            String authorization = request.getHeaders().getFirst("Authorization");

            if (authorization == null || authorization.isBlank()) {
                if (config.isRequired()) return onError(exchange, "로그인이 필요합니다.", 401);
                return chain.filter(exchange); // 게스트 허용이면 그냥 통과
            }

            if (!authorization.startsWith("Bearer ")) {
                if (config.isRequired()) return onError(exchange, "잘못된 인증 형식입니다.", 401);
                return chain.filter(exchange);
            }

            String token = extractJwt(authorization);
            try{
                Map<String, Object> map = validateJwtTokenAndGetClaims(token);
                ServerHttpRequest newRequest = request.mutate().
                        headers(h -> {
                            h.remove("X-USER-ID");
                            h.remove("X-USER-ROLE");
                            h.set("X-USER-ID", map.get("X-USER-ID").toString());
                            h.set("X-USER-ROLE", map.get("X-USER-ROLE").toString());
                        }).build();
                return chain.filter(exchange.mutate().request(newRequest).build());
            } catch (Exception e) {
                log.info("만료된 authorization 토큰: {}", e.getMessage());
                return onError(exchange, "만료된 사용자입니다.", 401);
            }
        });
    }

    private String extractJwt(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7); // "Bearer " (7글자) 제거
        }
        return authorization;
    }

    private Map<String, Object> validateJwtTokenAndGetClaims(String token) {
        Claims claims = jwtParser.parseSignedClaims(token).getPayload();
        return Map.of("X-USER-ID", claims.get("userId", Integer.class),
                "X-USER-ROLE", claims.get("role", String.class));
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, int httpStatusCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatusCode.valueOf(httpStatusCode));
        log.error(err);

        byte[] bytes = err.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
