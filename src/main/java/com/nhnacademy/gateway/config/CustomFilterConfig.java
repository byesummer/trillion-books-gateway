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

package com.nhnacademy.gateway.config;

import java.time.Duration;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

@Configuration
public class CustomFilterConfig {

    private static final String GUEST_COOKIE_NAME = "guestId";
    private static final String GUEST_HEADER_NAME = "X-GUEST-ID";

    @Bean
    public GlobalFilter guestIdCookieFilter() {
        return (exchange, chain) -> {
            String guestId = getCookieValue(exchange, GUEST_COOKIE_NAME);

            boolean needIssue = (guestId == null || guestId.isBlank());
            if (needIssue) {
                guestId = generateGuestId();

                // 쿠키 발급 (응답에 Set-Cookie)
                ResponseCookie cookie = ResponseCookie.from(GUEST_COOKIE_NAME, guestId)
                        .path("/")
                        .httpOnly(true)                 // JS로 접근 못하게(권장)
                        .secure(true)                   // HTTPS 환경이면 true 권장 (로컬 HTTP면 false로)
                        .sameSite("Lax")                // 일반적으로 Lax 무난
                        .maxAge(Duration.ofDays(1))
                        .build();

                exchange.getResponse().addCookie(cookie);
            }

            // 다운스트림으로 헤더 전달 (선택 but 추천)
            String finalGuestId = guestId;
            ServerHttpRequest newRequest = exchange.getRequest().mutate()
                    .headers(h -> h.set(GUEST_HEADER_NAME, finalGuestId))
                    .build();

            ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(newRequest)
                    .build();

            return chain.filter(mutatedExchange);
        };
    }

    private String getCookieValue(ServerWebExchange exchange, String name) {
        var cookie = exchange.getRequest().getCookies().getFirst(name);
        return cookie != null ? cookie.getValue() : null;
    }

    private String generateGuestId() {
        String uniqueId = UUID.randomUUID().toString().replace("-", "").toLowerCase();
        return "guest-" + uniqueId.substring(0, 8);
    }
}
