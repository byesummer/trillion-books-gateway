package com.nhnacademy.gateway;

import com.nhnacademy.gateway.filter.AuthorizationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthorizationFilterTest {

    private AuthorizationFilter authorizationFilter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        // 토큰 없음 경로만 타는 테스트들은 auth 서비스를 호출조차 안 하므로 빈 builder로 충분
        authorizationFilter = new AuthorizationFilter(WebClient.builder());
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    /**
     * WebClient가 실제 네트워크를 타지 않고 정해진 응답을 즉시 돌려주게 하는 필터 인스턴스.
     * ExchangeFunction = WebClient 내부에서 실제로 소켓을 여는 지점 — 여기를 mock하면
     * lb://auth-service 로 나가는 호출 자체가 발생하지 않는다.
     */
    private AuthorizationFilter filterWithFakeAuthResponse(ClientResponse response) {
        ExchangeFunction exchangeFunction = mock(ExchangeFunction.class);
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(response));
        return new AuthorizationFilter(WebClient.builder().exchangeFunction(exchangeFunction));
    }

    @Test
    @DisplayName("클라이언트가 보낸 X-Member-Id와 X-Member-Role은 제거된다.")
    void deleteClientHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/books/1")
                .header("X-Member-Id", "999")
                .header("X-Member-Role", "ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AuthorizationFilter.Config config = new AuthorizationFilter.Config();
        config.setRequired(false);

        GatewayFilter filter = authorizationFilter.apply(config);
        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());

        ServerHttpRequest forwarded = captor.getValue().getRequest();
        assertThat(forwarded.getHeaders().get("X-Member-Id")).isNull();
        assertThat(forwarded.getHeaders().get("X-Member-Role")).isNull();
    }

    @Test
    @DisplayName("토큰 없고 required면 401")
    void noTokenAndRequiredToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/members/me").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AuthorizationFilter.Config config = new AuthorizationFilter.Config();
        config.setRequired(true);

        GatewayFilter filter = authorizationFilter.apply(config);
        filter.filter(exchange, chain).block();

        // onError()가 이 필터 자신 안에서 직접 exchange.getResponse()에 상태코드를 쓰므로 확인 가능
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // 401로 끝났으니 다음 단계로 넘어가면 안 됨
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("토큰 없고 optional이면 비회원으로 통과")
    void noTokenAndOptional() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/members/me").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AuthorizationFilter.Config config = new AuthorizationFilter.Config();
        config.setRequired(false);

        GatewayFilter filter = authorizationFilter.apply(config);
        filter.filter(exchange, chain).block();
        // 이 경로는 필터가 응답을 직접 쓰지 않고 다음 단계로 넘기기만 함
        verify(chain).filter(any());
    }

    @Test
    @DisplayName("유효 토큰이면 검증된 헤더 주입")
    void ifTokenValidatedThenInjectHeader() {
        AuthorizationFilter filter = filterWithFakeAuthResponse(
                ClientResponse.create(HttpStatus.OK)
                        .header("X-Member-Id", "1")
                        .header("X-Member-Role", "MEMBER")
                        .build());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AuthorizationFilter.Config config = new AuthorizationFilter.Config();
        config.setRequired(true);

        filter.apply(config).filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerHttpRequest forwarded = captor.getValue().getRequest();
        assertThat(forwarded.getHeaders().getFirst("X-Member-Id")).isEqualTo("1");
        assertThat(forwarded.getHeaders().getFirst("X-Member-Role")).isEqualTo("MEMBER");
    }

    @Test
    @DisplayName("검증 실패시 optional이면 통과하되 클라 헤더는 제거")
    void unAuthorizedAndIsOptionalThenDeleteClientHeader() {
        AuthorizationFilter filter = filterWithFakeAuthResponse(
                ClientResponse.create(HttpStatus.UNAUTHORIZED).build());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                .header("X-Member-Id", "999")
                .header("X-Member-Role", "ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AuthorizationFilter.Config config = new AuthorizationFilter.Config();
        config.setRequired(false);

        filter.apply(config).filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerHttpRequest forwarded = captor.getValue().getRequest();
        assertThat(forwarded.getHeaders().get("X-Member-Id")).isNull();
        assertThat(forwarded.getHeaders().get("X-Member-Role")).isNull();
    }
}
