# __SSL_PASSWORD__s-gateway

**Trillion** - MSA 기반 온라인 서점 플랫폼

- API 게이트웨이 — 모든 외부 요청의 단일 진입점

## 기능

- 라우팅 — `RouteLocatorConfig` 경로 기반 서비스 매핑
- 인증 위임 검증 — auth `/auth/validate` 호출로 서명·만료·블랙리스트 확인 후 `X-Member-Id` / `X-Member-Role` 주입
- 비회원 식별 — `guestId` 쿠키 → `X-Guest-Id` 헤더
- 액세스 로그 — 요청/응답/레이턴시를 전용 로거로 기록

## 기술 스택

| 분류 | 스택 |
|---|---|
| Language | Java 21 |
| Framework | Spring Cloud Gateway (WebFlux) |
| Cloud | Spring Cloud 2025.0.0 (Gateway, Eureka Client, Config Client) |
| Base | Spring Boot 3.5.7 |
| Build | Maven |

## 핵심 설계 판단

### 1. 게이트웨이 = 정책 시행 지점(PEP), 인증 = auth 위임

게이트웨이는 토큰을 직접 파싱하지 않고 auth `/auth/validate` 로 검증을 위임한 뒤, 그 결과(`X-Member-Id` / `X-Member-Role`)를 원본 요청에 주입한다. 다운스트림 서비스는 인증 코드·시크릿 없이 이 헤더만 신뢰하며, 서비스는 외부에 노출되지 않고 게이트웨이가 유일한 진입점이다.

### 2. 회원/비회원 흐름 분리

토큰이 있으면 검증 후 회원 헤더를, 없으면 `guestId` 쿠키를 발급·전달(`X-Guest-Id`)해 장바구니 등 비회원 기능을 지원한다. 라우트별로 "인증 필수 / 비회원 허용"을 `RouteLocatorConfig` 로 구분한다.

### 3. Spring Security 미적용

게이트웨이는 인증 주체가 아니라 검증 위임·헤더 주입만 담당하므로, Spring Security 필터 체인 대신 경량 `GatewayFilter` / `GlobalFilter` 로 구현했다. 인증 로직(비밀번호·세션·OAuth2)은 auth 서비스에만 둔다.
