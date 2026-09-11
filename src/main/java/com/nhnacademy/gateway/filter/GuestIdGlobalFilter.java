package com.nhnacademy.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class GuestIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpCookie guestCookie = request.getCookies().getFirst("guestId");

        ServerHttpRequest cleaned = request.mutate()
                .headers(h -> {
                    h.remove("X-Guest-Id");
                    if (guestCookie != null) {
                        h.set("X-Guest-Id", guestCookie.getValue());
                    }
                })
                .build();

        return chain.filter(exchange.mutate().request(cleaned).build());
    }

    @Override
    public int getOrder() {
        return -1;
    }
}