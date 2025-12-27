package com.nhnacademy.gateway.filter;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AuthorizationFilter extends AbstractGatewayFilterFactory<AuthorizationFilter.Config> {

    private final WebClient.Builder webClientBuilder;

    public AuthorizationFilter(WebClient.Builder webClientBuilder) {
        super(Config.class);
        this.webClientBuilder = webClientBuilder;
    }

    @Getter
    @Setter
    public static class Config {
        // true: 토큰 필수 (없거나 유효하지 않으면 401)
        // false: 토큰 선택 (있으면 검증해서 헤더 주입, 없거나 실패하면 비회원으로 통과)
        private boolean required;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // Authorization 헤더 확인
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                if (config.isRequired()) {
                    log.info("Auth Gateway: Missing Authorization Header");
                    return onError(exchange, HttpStatus.UNAUTHORIZED);
                }
                // 토큰이 필수가 아니고 없으면 그냥 통과 (비회원)
                return chain.filter(exchange);
            }

            String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            // Auth Service에 검증 요청 (비동기로)
            return webClientBuilder.build()
                    .post()
                    .uri("lb://auth-service/auth/validate") // Auth 서비스 검증 API 호출
                    .header(HttpHeaders.AUTHORIZATION, token) // 토큰 그대로 전달
                    .retrieve()
                    .toBodilessEntity() // Body는 필요 없음 (헤더만 확인)
                    .flatMap(response -> {
                        // 검증 성공 시 auth service가 준 헤더 꺼내서 원본 request에 이식
                        HttpHeaders headers = response.getHeaders();
                        String memberId = headers.getFirst("X-Member-Id");
                        String memberRole = headers.getFirst("X-Member-Role");

                        ServerHttpRequest newRequest = request.mutate()
                                .header("X-Member-Id", memberId)
                                .header("X-Member-Role", memberRole)
                                .build();

                        return chain.filter(exchange.mutate().request(newRequest).build());
                    })
                    .onErrorResume(e -> {
                        // 필수 요청은 검증 실패하면 401
                        if (config.isRequired()) {
                            log.warn("Token Validation Failed: {}", e.getMessage());
                            return onError(exchange, HttpStatus.UNAUTHORIZED);
                        }
                        // 비회원 가능 경로면 토큰이 틀려도 상관없을듯
                        log.debug("Optional token validation failed, proceeding as guest: {}", e.getMessage());
                        return chain.filter(exchange);
                    });
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }
}