package com.nhnacademy.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;

@Component
@Slf4j
public class AccessLogFilter implements GlobalFilter, Ordered {

    private static final Logger accessLogger = LoggerFactory.getLogger("ACCESS_LOG");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();

        ServerHttpRequest req = exchange.getRequest();
        String method = req.getMethod().name();
        String path = req.getURI().getRawPath();
        String query = req.getURI().getRawQuery();
        String fullPath = (query == null) ? path : path + "?" + query;

        // (중요) IP는 null일 수 있어 안전 처리
        String ip = Optional.ofNullable(req.getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(InetAddress::getHostAddress)
                .orElse("-");

        String userAgent = req.getHeaders().getFirst("User-Agent");

        String userId = Optional.ofNullable(req.getHeaders().getFirst("X-User-Id"))
                .orElse("-");

        return chain.filter(exchange).doFinally(signalType -> {
            long tookMs = System.currentTimeMillis() - start;
            Integer status = Optional.ofNullable(exchange.getResponse().getStatusCode())
                    .map(HttpStatusCode::value)
                    .orElse(0);

            // ★ access 전용 logger로 찍는다
            accessLogger.info("{} {} {} {} {}ms user=\"{}\" ua=\"{}\"",
                    ip, method, fullPath, status, tookMs, userId, userAgent);
        });
    }

    @Override
    public int getOrder() {
        return 10;
    }
}