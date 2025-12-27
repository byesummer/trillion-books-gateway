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

import com.nhnacademy.gateway.filter.AuthorizationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteLocatorConfig {

    @Value("${uri.service.auth}")
    private String authServiceId;

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

    private final AuthorizationFilter authorizationFilter;

    public RouteLocatorConfig(AuthorizationFilter authorizationFilter) {
        this.authorizationFilter = authorizationFilter;
    }

    @Bean
    public RouteLocator myRoute(RouteLocatorBuilder builder) {

        // 회원 전용 (토큰 반드시 필요)
        AuthorizationFilter.Config memberOnlyConfig = new AuthorizationFilter.Config();
        memberOnlyConfig.setRequired(true);

        // 회원, 비회원 모두 가능 (토큰 있으면 인증하고 없으면 패스하기)
        AuthorizationFilter.Config guestAllowedConfig = new AuthorizationFilter.Config();
        guestAllowedConfig.setRequired(false);

        return builder.routes()
                .route("oauth2-login-service",
                        p -> p.path("/api/login/oauth2/**")
                                .filters(f -> f
                                        .filter(authorizationFilter.apply(guestAllowedConfig)))
                                .uri(authServiceId))
                .route("auth-service",
                        p -> p.path("/api/auth/**", "/api/login/**", "/api/oauth2/**")
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(guestAllowedConfig)))
                                .uri(authServiceId))
                .route("member-service-public",
                        p -> p.path(
                                        "/api/members/signup",
                                        "/api/members/dormant/**",
                                        "/api/members/emails/**",
                                        "/api/members/findEmail",
                                        "/api/members/password/**",
                                        "/api/members/social/**"
                                )
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(guestAllowedConfig)))
                                .uri(memberServiceId))
                .route("member-service-secure",
                        p -> p.path("/api/members/**")
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(memberOnlyConfig)))
                                .uri(memberServiceId))
                .route("book-service",
                        p -> p.path("/api/books/**","/api/admin/**")
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(guestAllowedConfig)))
                                .uri(bookServiceId))
                .route("order-service",
                        p -> p.path(
                                "/api/orders/**",
                                "/api/order-items/**",
                                "/api/carts/**",
                                "/api/payments/**"
                                )
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(guestAllowedConfig)))
                                .uri(orderServiceId))
                .route("coupon-service",
                        p -> p.path("/api/coupons/**")
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(memberOnlyConfig)))
                                .uri(couponServiceId))
                .route("search-service",
                        p -> p.path("/api/search/**")
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(guestAllowedConfig)))
                                .uri(searchServiceId))
                .build();
    }
}