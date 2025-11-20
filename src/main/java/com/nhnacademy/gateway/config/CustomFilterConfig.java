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

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import java.util.UUID;

@Configuration
public class CustomFilterConfig {
    @Bean
    public GlobalFilter uniqueIdFilter() {
        return (exchange, chain) -> {
            String uniqueId = UUID.randomUUID().toString();
            ServerWebExchange mutatedChange = exchange.mutate()
                    .request(r -> r.header("X-Request-Id", uniqueId))
                    .build();
            return chain.filter(mutatedChange);
        };
    }
}
