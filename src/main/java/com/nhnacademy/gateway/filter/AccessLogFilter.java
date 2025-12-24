package com.nhnacademy.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class AccessLogFilter implements GlobalFilter, Ordered {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS_LOG");

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String path = req.getURI().getPath();

        if (shouldSkip(path)) {
            return chain.filter(exchange);
        }

        long startNs = System.nanoTime();

        // traceId/spanId 확보: MDC 우선, 없으면 헤더에서 복구
        String traceId = firstNonBlank(
                MDC.get("traceId"),
                MDC.get("trace_id"),
                req.getHeaders().getFirst("X-B3-TraceId"),
                req.getHeaders().getFirst("traceId"),
                extractTraceIdFromTraceParent(req.getHeaders().getFirst("traceparent"))
        );
        String spanId = firstNonBlank(
                MDC.get("spanId"),
                MDC.get("span_id"),
                req.getHeaders().getFirst("X-B3-SpanId"),
                req.getHeaders().getFirst("spanId")
        );

        // client ip (X-Forwarded-For 우선)
        String clientIp = firstNonBlank(
                firstIp(req.getHeaders().getFirst("X-Forwarded-For")),
                req.getHeaders().getFirst("X-Real-IP"),
                exchange.getRequest().getRemoteAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : ""
        );

        String method = req.getMethod() != null ? req.getMethod().name() : "UNKNOWN";

        return chain.filter(exchange)
                .doFinally(sig -> {
                    ServerHttpResponse res = exchange.getResponse();
                    int status = res.getStatusCode() != null ? res.getStatusCode().value() : 0;
                    long tookMs = (System.nanoTime() - startNs) / 1_000_000;

                    // JSON 한 줄 (필요 최소)
                    // querystring/headers/body 안 찍음
                    String json = String.format(
                            "{\"@timestamp\":\"%s\",\"type\":\"http_access\",\"method\":\"%s\",\"path\":\"%s\",\"status\":%d,\"duration_ms\":%d,\"client_ip\":\"%s\",\"traceId\":\"%s\",\"spanId\":\"%s\"}",
                            Instant.now(),
                            escape(method),
                            escape(path),
                            status,
                            tookMs,
                            escape(clientIp),
                            escape(traceId),
                            escape(spanId)
                    );

                    ACCESS_LOG.info(json);
                });
    }

    private boolean shouldSkip(String path) {
        return path == null
                || path.startsWith("/actuator")
                || "/favicon.ico".equals(path);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private static String firstIp(String xff) {
        if (xff == null || xff.isBlank()) return "";
        // "client, proxy1, proxy2" 형태라 첫번째가 원본일 가능성이 큼
        String[] parts = xff.split(",");
        return parts.length > 0 ? parts[0].trim() : xff.trim();
    }

    /**
     * W3C traceparent: "00-<trace-id>-<span-id>-<flags>"
     * trace-id는 32 hex
     */
    private static String extractTraceIdFromTraceParent(String traceparent) {
        if (traceparent == null) return "";
        String[] parts = traceparent.split("-");
        if (parts.length >= 4) {
            return parts[1];
        }
        return "";
    }

    // JSON-safe 최소 이스케이프
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
