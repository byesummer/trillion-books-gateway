package com.nhnacademy.gateway.filter;

import com.nhnacademy.gateway.auth.JwtValidator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorizationFilter extends AbstractGatewayFilterFactory<AuthorizationFilter.Config> {

    private final JwtValidator jwtValidator;
    private final ReactiveStringRedisTemplate redisTemplate;

    @Getter
    @Setter
    public static class Config {
        // true: 토큰 필수 (없거나 유효하지 않으면 401)
        // false: 토큰 선택 (있으면 검증해서 헤더 주입, 없거나 실패하면 비회원으로 통과)
        private boolean required;
        private String requiredRole;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // 신뢰 헤더 초기화
            ServerHttpRequest cleaned = request.mutate()
                    .headers(h -> {
                        h.remove("X-Member-Id");
                        h.remove("X-Member-Role");
                    })
                    .build();

            ServerWebExchange ex = exchange.mutate().request(cleaned).build();

            if (request.getHeaders().containsKey("X-Member-Id")
                    || request.getHeaders().containsKey("X-Member-Role")) {
                log.warn("Spoofed identity headers from client, stripping. path={}",
                        request.getPath());
            }

            // Authorization 헤더 확인
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                if (config.isRequired()) {
                    return onError(ex, HttpStatus.UNAUTHORIZED, null);
                }
                // 비회원은 회원 헤더 없이 통과
                return chain.filter(ex);
            }

            String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            String rawToken = token.startsWith("Bearer ") ? token.substring(7) : token;

            JwtValidator.ValidationResult result = jwtValidator.validateAccessToken(rawToken);
            if (!result.valid()) {
                if (config.isRequired()) {
                    return onError(ex, HttpStatus.UNAUTHORIZED, result.failureReason());
                }
                return chain.filter(ex);
            }

            return redisTemplate.hasKey("BL:" + rawToken)
                    .flatMap(blacklisted -> {
                        if (Boolean.TRUE.equals(blacklisted)) {
                            if (config.isRequired()) {
                                return onError(ex, HttpStatus.UNAUTHORIZED, "token_blacklisted");
                            }
                            return chain.filter(ex);
                        }
                        if (config.getRequiredRole() != null
                                && !config.getRequiredRole().equals(result.role())) {
                            return onError(ex, HttpStatus.FORBIDDEN, null);
                        }
                        ServerHttpRequest newRequest = cleaned.mutate()
                                .header("X-Member-Id", String.valueOf(result.memberId()))
                                .header("X-Member-Role", result.role())
                                .build();
                        return chain.filter(ex.mutate().request(newRequest).build());
                    });
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus httpStatus, String authError) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        if(authError != null){
            response.getHeaders().add("X-Auth-Error",authError);
        }
        return response.setComplete();
    }
}