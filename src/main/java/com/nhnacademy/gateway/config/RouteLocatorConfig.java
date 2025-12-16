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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteLocatorConfig {

    @Value("${uri.service.member}")
    private String memberServiceId;

    @Value("${uri.service.book}")
    private String bookServiceId;

    @Value("${uri.service.order}")
    private String orderServiceId;

    @Value("${uri.service.coupon}")
    private String couponServiceId;

    @Value("${uri.service.search}")
    private String searchServiceId;

    @Bean
    public RouteLocator myRoute(RouteLocatorBuilder builder) {


        return builder.routes()
                // 로그인, 토큰 재발급 토큰 검사 스킵
                .route("auth-service",
                        p -> p.path("/api/auth/**")
                                .filters(f -> f.stripPrefix(1))
                                .uri("lb://" + memberServiceId))
                // 회원가입, 휴면해제, 이메일/ID 찾기 토큰 검사 스킵
                .route("member-service-public",
                        p -> p.path(
                                        "/api/members/signup",
                                        "/api/members/dormant/**",
                                        "/api/members/emails/**",
                                        "/api/members/findEmail"
                                )
                                .filters(f -> f.stripPrefix(1))
                                .uri("lb://" + memberServiceId))
                // 그 외 모든 기능은 토큰 검사
                .route("member-service-secure",
                        p -> p.path("/api/members/**")
                                .filters(f -> f.stripPrefix(1))
                                .uri("lb://" + memberServiceId))
                .route("book-service",
                        p -> p.path("/api/books/**")
                                .filters(f -> f.stripPrefix(1))
                                .uri("lb://" + bookServiceId))
                // 비회원 장바구니/주문 가능 시 false, 회원 전용이면 true 변경
                .route("order-service",
                        p -> p.path("/api/orders/**", "/api/carts/**")
                                .filters(f -> f.stripPrefix(1))
                                .uri("lb://" + orderServiceId))
                .route("coupon-service",
                        p -> p.path("/api/coupons/**")
                                .filters(f -> f.stripPrefix(1))
                                .uri("lb://" + couponServiceId))
                .route("search-service",
                        p -> p.path("/api/search/**")
                                .filters(f -> f.stripPrefix(1))
                                .uri("lb://" + searchServiceId))
                .build();
    }
}