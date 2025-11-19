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

            if (!request.getHeaders().containsKey("Authorization")) {
                if (config.isRequired()){
                    return onError(exchange, "로그인이 필요합니다.", 401);
                } else {
                    return chain.filter(exchange);
                }
            }


            String authorization = request.getHeaders().getFirst("Authorization");
            String token = extractJwt(authorization);
            try{
                Map<String, Object> map = validateJwtTokenAndGetClaims(token);
                ServerHttpRequest newRequest = request.mutate().
                        header("X-User-Id", map.get("X-User-Id").toString()).
                        header("X-User-Role", map.get("X-User-Role").toString()).build();
                return chain.filter(exchange.mutate().request(newRequest).build());
            } catch (Exception e) {
                return onError(exchange, "만료된 토큰 " +e.getMessage(), 401);
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
        return Map.of("X-User-Id", claims.get("username", String.class),
                "X-User-Role", claims.get("role", String.class));
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
