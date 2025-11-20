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

import com.nhnacademy.gateway.filter.JwtAuthenticationFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteLocatorConfig {

    JwtAuthenticationFilter jwtAuthenticationFilter;

    public RouteLocatorConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public RouteLocator myRoute(RouteLocatorBuilder builder) {

        JwtAuthenticationFilter.Config memberOnlyConfig = new JwtAuthenticationFilter.Config();
        memberOnlyConfig.setRequired(true);

        JwtAuthenticationFilter.Config guestAllowedConfig = new JwtAuthenticationFilter.Config();
        guestAllowedConfig.setRequired(false);

        return builder.routes()
                .route("member-login",
                        p -> p.path("/api/auth/**")
                                .filters(f -> f.filter(jwtAuthenticationFilter.apply(guestAllowedConfig)))
                                .uri("lb://MEMBER-SERVICE"))
                .route("member-service",
                        p -> p.path("/api/members/**")
                                .filters(f -> f.filter(jwtAuthenticationFilter.apply(memberOnlyConfig)))
                                .uri("lb://MEMBER-SERVICE"))
                .route("book-service",
                        p -> p.path("/api/books/**")
                                .filters(f -> f.filter(jwtAuthenticationFilter.apply(guestAllowedConfig)))
                                .uri("lb://BOOK-SERVICE"))
                .route("order-service",
                        p -> p.path("/api/orders/**")
                                .filters(f -> f.filter(jwtAuthenticationFilter.apply(guestAllowedConfig)))
                                .uri("lb://ORDER-SERVICE"))
                .route("coupon-service",
                        p -> p.path("/api/coupons/**")
                               .filters(f -> f.filter(jwtAuthenticationFilter.apply(guestAllowedConfig)))
                                .uri("lb://COUPON-SERVICE"))
                .route("cart-service",
                        p -> p.path("/api/carts/**")
                                .filters(f -> f.filter(jwtAuthenticationFilter.apply(guestAllowedConfig)))
                                .uri("lb://CART-SERVICE"))
                .build();
    }
}
