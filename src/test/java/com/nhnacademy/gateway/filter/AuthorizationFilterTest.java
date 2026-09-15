package com.nhnacademy.gateway.filter;

import com.nhnacademy.gateway.auth.JwtValidator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Stage C 전환 후 AuthorizationFilter는 WebClient로 auth-service를 호출하지 않고
 * JwtValidator(로컬 서명 검증) + ReactiveStringRedisTemplate(블랙리스트 직접 조회)로 동작한다.
 * 그래서 WebClient/ExchangeFunction mock 대신, 실제 JwtValidator 객체에 픽스처 토큰을 넣어 검증한다.
 */
class AuthorizationFilterTest {

    private static final String SECRET = "test-jwt-secret-please-change-0123456789abcdef";
    private static final String OTHER_SECRET = "another-completely-different-secret-9876543210zy";

    private SecretKey signingKey;
    private JwtValidator jwtValidator;
    private ReactiveStringRedisTemplate redisTemplate;
    private AuthorizationFilter authorizationFilter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        jwtValidator = new JwtValidator(SECRET);
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        // 기본값: 블랙리스트에 없음. 블랙리스트 테스트에서만 개별로 덮어씀.
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        authorizationFilter = new AuthorizationFilter(jwtValidator, redisTemplate);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private String buildToken(SecretKey key, String category, Long memberId, String role, long expiresInMillisFromNow) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("category", category)
                .claim("role", role)
                .issuedAt(new Date(now.getTime() - 60_000))
                .expiration(new Date(now.getTime() + expiresInMillisFromNow))
                .signWith(key)
                .compact();
    }

    private String validAccessToken(Long memberId, String role) {
        return buildToken(signingKey, "ACCESS_TOKEN", memberId, role, 30 * 60 * 1000);
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

        authorizationFilter.apply(config).filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("토큰 없고 optional이면 비회원으로 통과")
    void noTokenAndOptional() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/members/me").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AuthorizationFilter.Config config = new AuthorizationFilter.Config();
        config.setRequired(false);

        authorizationFilter.apply(config).filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("유효 토큰이면 검증된 헤더를 주입하고, 블랙리스트를 조회한다")
    void ifTokenValidatedThenInjectHeader() {
        String token = validAccessToken(1L, "MEMBER");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AuthorizationFilter.Config config = new AuthorizationFilter.Config();
        config.setRequired(true);

        authorizationFilter.apply(config).filter(exchange, chain).block();

        // 서명 검증을 통과한 뒤에만 블랙리스트를 조회해야 함 (순서 보장)
        verify(redisTemplate).hasKey("BL:" + token);

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerHttpRequest forwarded = captor.getValue().getRequest();
        assertThat(forwarded.getHeaders().getFirst("X-Member-Id")).isEqualTo("1");
        assertThat(forwarded.getHeaders().getFirst("X-Member-Role")).isEqualTo("MEMBER");
    }

    @Test
    @DisplayName("만료된 토큰이면 401과 X-Auth-Error: token_expired를 반환하고, Redis는 조회하지 않는다")
    void expiredTokenThenUnauthorized() {
        String expiredToken = buildToken(signingKey, "ACCESS_TOKEN", 1L, "MEMBER", -1_000);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AuthorizationFilter.Config config = new AuthorizationFilter.Config();
        config.setRequired(true);

        authorizationFilter.apply(config).filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Auth-Error")).isEqualTo("token_expired");
        verify(chain, never()).filter(any());
        // 서명·만료 검증에서 이미 걸러졌으니 Redis까지 갈 필요가 없음 — 낭비 조회 방지 확인
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("다른 시크릿으로 서명된 토큰이면 401과 X-Auth-Error: token_invalid")
    void wrongSignatureTokenThenUnauthorized() {
        SecretKey otherKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));
        String forgedToken = buildToken(otherKey, "ACCESS_TOKEN", 1L, "MEMBER", 30 * 60 * 1000);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AuthorizationFilter.Config config = new AuthorizationFilter.Config();
        config.setRequired(true);

        authorizationFilter.apply(config).filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Auth-Error")).isEqualTo("token_invalid");
        verify(chain, never()).filter(any());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("블랙리스트에 등록된 토큰이면 401과 X-Auth-Error: token_blacklisted")
    void blacklistedTokenThenUnauthorized() {
        String token = validAccessToken(1L, "MEMBER");
        when(redisTemplate.hasKey("BL:" + token)).thenReturn(Mono.just(true));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AuthorizationFilter.Config config = new AuthorizationFilter.Config();
        config.setRequired(true);

        authorizationFilter.apply(config).filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Auth-Error")).isEqualTo("token_blacklisted");
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("MEMBER role이면 ADMIN 라우트는 403")
    void memberRoleThenAdminRouteForbidden() {
        String token = validAccessToken(1L, "MEMBER");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/books/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AuthorizationFilter.Config config = new AuthorizationFilter.Config();
        config.setRequired(true);
        config.setRequiredRole("ADMIN");

        authorizationFilter.apply(config).filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("ADMIN role이면 통과")
    void adminRoleThenPass() {
        String token = validAccessToken(1L, "ADMIN");
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/admin/books/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AuthorizationFilter.Config config = new AuthorizationFilter.Config();
        config.setRequired(true);
        config.setRequiredRole("ADMIN");

        authorizationFilter.apply(config).filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerHttpRequest forwarded = captor.getValue().getRequest();
        assertThat(forwarded.getHeaders().getFirst("X-Member-Id")).isEqualTo("1");
        assertThat(forwarded.getHeaders().getFirst("X-Member-Role")).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("검증 실패시 optional이면 통과하되 클라 헤더는 제거되고, 회원 헤더는 안 붙는다")
    void unAuthorizedAndIsOptionalThenDeleteClientHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/members/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer garbage-not-a-jwt")
                .header("X-Member-Id", "999")
                .header("X-Member-Role", "ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AuthorizationFilter.Config config = new AuthorizationFilter.Config();
        config.setRequired(false);

        authorizationFilter.apply(config).filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerHttpRequest forwarded = captor.getValue().getRequest();
        assertThat(forwarded.getHeaders().get("X-Member-Id")).isNull();
        assertThat(forwarded.getHeaders().get("X-Member-Role")).isNull();
    }
}
