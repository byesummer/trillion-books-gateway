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

        // 관리자 전용
        AuthorizationFilter.Config adminOnlyConfig = new AuthorizationFilter.Config();
        adminOnlyConfig.setRequired(true);
        adminOnlyConfig.setRequiredRole("ADMIN");

        // 회원, 비회원 모두 가능 (토큰 있으면 인증하고 없으면 패스하기)
        AuthorizationFilter.Config guestAllowedConfig = new AuthorizationFilter.Config();
        guestAllowedConfig.setRequired(false);

        return builder.routes()
                // 인증 서비스
                .route("auth-service-public",
                        p -> p.path("/api/auth/**", "/api/login/**", "/api/oauth2/**")
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .preserveHostHeader()
                                        .filter(authorizationFilter.apply(guestAllowedConfig)))
                                .uri(authServiceId))

                // 회원 서비스
                .route("member-service-admin",
                        p ->p.path(
                                "/api/members/admin/**")
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(adminOnlyConfig)))
                                        .uri(memberServiceId))
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
                .route("member-service-private",
                        p -> p.path("/api/members/**")
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(memberOnlyConfig)))
                                .uri(memberServiceId))

                // 쿠폰 서비스
                .route("coupon-service-admin",
                        p -> p.path("/api/coupons/admin/**")
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(adminOnlyConfig)))
                                .uri(couponServiceId))
                .route("coupon-service-private",
                        p -> p.path("/api/coupons/**","/api/book-coupons/**","/api/member-coupons/**")
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(memberOnlyConfig)))
                                .uri(couponServiceId))

                // 도서 서비스
                .route("book-service-public",
                        p -> p.path("/api/books/**")
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(guestAllowedConfig)))
                                .uri(bookServiceId))

                // 장바구니 서비스
                .route("cart-service-private",
                        p -> p.path("/api/carts/merge")
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(memberOnlyConfig)))
                                .uri(orderServiceId))

                // 주문 서비스
                .route("order-service-admin",
                        p -> p.path(
                                        "/api/orders/admin/**"
                                )
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(adminOnlyConfig)))
                                .uri(orderServiceId))
                .route("order-service-public",
                        p -> p.path(
                                        "/api/orders/non-members/**"
                                )
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(guestAllowedConfig)))
                                .uri(orderServiceId))
                .route("order-service-private",
                        p -> p.path(
                                        "/api/orders/**",
                                        "/api/order-items/**",
                                        "/api/carts/**",
                                        "/api/payments/**"
                                )
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(memberOnlyConfig)))
                                .uri(orderServiceId))

                // 검색 서비스
                .route("search-service-public",
                        p -> p.path("/api/search/**", "/api/review-summary/**")
                                .filters(f -> f
                                        .stripPrefix(1)
                                        .filter(authorizationFilter.apply(guestAllowedConfig)))
                                .uri(searchServiceId))
                .build();
    }
}