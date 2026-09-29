# trillion-books-gateway

**Trillion** — MSA 기반 온라인 서점의 API 게이트웨이 (모든 외부 요청의 단일 진입점)

> 8인 팀 프로젝트의 게이트웨이 중 **인증·인가 필터(신뢰 헤더 초기화, 직접 서명 검증, 라우트별 role 인가)를 담당**했다. 라우팅 설정(`RouteLocatorConfig`)은 팀 공동 작업이다. 팀 프로젝트 종료 후 보안·성능 문제를 찾아 개선했다. 인증 정책 결정은 [auth](https://github.com/byesummer/trillion-books-auth)가 전담한다.

## 기능

- 라우팅 — `RouteLocatorConfig` 경로 기반 서비스 매핑, 관리자 경로는 `requiredRole`로 일반 라우트보다 먼저 등록
- **직접 서명 검증** — `JwtValidator`가 auth 호출 없이 서명·만료·카테고리를 직접 검증, 통과한 토큰만 Redis 블랙리스트 조회
- **신뢰 헤더 초기화** — 토큰이 없거나 검증에 실패한 경로를 포함한 모든 경로에서 클라이언트가 보낸 `X-Member-*` 헤더를 먼저 제거하고, 게이트웨이가 확정한 값만 주입
- 비회원 식별 — `guestId` 쿠키 → `X-Guest-Id` 헤더
- 액세스 로그 — 요청/응답/레이턴시를 전용 로거로 기록

## 기술 스택

| 분류 | 스택 |
|---|---|
| Language | Java 21 |
| Framework | Spring Cloud Gateway (WebFlux) |
| Cloud | Spring Cloud 2025.0.0 (Gateway, Eureka Client, Config Client) |
| Base | Spring Boot 3.5.7 |
| Store | Redis (`ReactiveStringRedisTemplate`) — 블랙리스트 직접 조회 |
| Build | Maven |

## 핵심 구현

- **신뢰 헤더 초기화** — 모든 경로에서 `X-Member-*` 헤더를 먼저 제거한 뒤 검증된 값만 주입, 관리자 경로는 `requiredRole`로 강제
- **서명 직접 검증** — `JwtValidator`가 auth 호출 없이 서명을 직접 검증(PEP), 인증 판단 시간 2.3ms → 0.53ms(약 4.3배) 단축
- **회원/비회원 흐름 분리** — 토큰 유무에 따라 회원 헤더 또는 `guestId`(`X-Guest-Id`)로 `RouteLocatorConfig` 분리
- **경량 필터 체인** — 검증·헤더 주입만 담당하므로 Spring Security 대신 `GatewayFilter`/`GlobalFilter`로 구현
