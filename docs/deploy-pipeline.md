# 배포 파이프라인 (팀 운영 환경 참고)

> 이 저장소는 기록용으로 CI(`./mvnw -B verify`)만 유지한다.
> 팀 운영 당시의 CD 흐름을 기록으로 남긴다.

## 개요

`push → dev` 시 GitHub Actions 가:

1. Maven 빌드 (`mvn clean package -DskipTests`)
2. 컨테이너 이미지 빌드 → GHCR(`ghcr.io/<org>/gateway:latest`) push
3. SSH 로 운영 서버 접속 → 최신 이미지 pull → 게이트웨이 컨테이너 무중단 교체

게이트웨이는 `trillion-gateway-peer` 로 이중화되어 있고, 배포 시 peer 를 순차 재시작한다.

## 서버 측 스크립트 (요지)

```bash
SERVICE_NAME="trillion-gateway-peer"

cd ~/msa-project
echo "$GHCR_TOKEN" | docker login ghcr.io -u "$REGISTRY_OWNER" --password-stdin

docker compose rm -f "$SERVICE_NAME"
docker compose pull "$SERVICE_NAME"
docker compose up -d --no-deps "$SERVICE_NAME"

docker logout ghcr.io
```

## 필요 Secrets (팀 저장소 기준)

| Secret | 용도 |
|---|---|
| `GHCR_TOKEN` | GHCR push / pull |
| `SSH_IP` / `SSH_ID` / `SSH_PASSWORD` / `SSH_PORT` | 운영 서버 접속 |
| `SONAR_HOST` / `SONAR_TOKEN` | (선택) SonarQube 분석 |

## 관측성

전체 스택은 Eureka(서비스 디스커버리) + docker-compose 로 구성되며,
로그는 Fluentd → Elasticsearch → Kibana 로 수집한다 (`logback-spring.xml` 의 `JSON_FILE` 앱펜더, `!dev & !local` 프로파일에서만 활성).
