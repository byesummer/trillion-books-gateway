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

import java.util.UUID;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                //__SSL_PASSWORD__.shop/api 로 들어로는 모든 경로에
                .route("tillion_book_route", r -> r.host("__SSL_PASSWORD__.shop")
                        .and()
                        .path("/api/**")
                        .filters(o -> o.filter((exchange, chain) -> {
                            String uniqueId = UUID.randomUUID().toString();
                            ServerWebExchange mutatedChange = exchange.mutate()
                                    .request(originalRequest -> originalRequest.header("X-Request-Id", uniqueId))
                                    .build();
                            return chain.filter(mutatedChange);
                        })).uri("lb://book-backend"))
                .build();
    }
}
