# monimo-shop

감시 대상 쇼핑몰 4종(게이트웨이 · 주문 · 결제 · 재고)과 OTel Java Agent 설정, 스레드 덤프 Extension

- 기술: Kotlin 2.2 · Spring Boot 3.5 · Java 17 · Gradle 8.14 (멀티모듈 5개: gateway · order · payment · inventory · agent-extension) · OTel Java Agent
- 상태: 뼈대만 있음 (개발환경 세팅 중)

## 폴더 구성

| 폴더 | 하는 일 |
|---|---|
| `gateway/` | 게이트웨이 (1b에 추가) |
| `order/` | 주문 (1a) |
| `payment/` | 결제 (1a) |
| `inventory/` | 재고 (1b에 추가) |
| `agent-extension/` | 스레드 덤프 명령 수신 Extension (우리가 만드는 유일한 에이전트 코드) |
| `otel/` | OTel Java Agent 설정 · 버전 고정 — `agent.properties`(전송 gRPC · 수집기 `collector:4317` · 샘플러 always_on) · `AGENT_VERSION` |
| `k6/` | 부하 · 에러 주입 시나리오 |

## 로컬 실행

필요한 것: JDK 17 (없으면 Gradle 이 자동으로 내려받는다), Docker (compose 는 준비 중)

```bash
./gradlew build                 # 5개 모듈 컴파일 + 서비스 4개 bootJar
./gradlew :order:bootRun        # 주문 서비스만 8091 에서 띄우기 (payment 는 8092, gateway 8090, inventory 8093)
```

compose 로 4개 + MySQL 을 한 번에 켜는 명령은 준비 중이다 (`docker-compose.dev.yml`).

## 환경변수

실제 값은 레포에 올리지 않는다. `.env.example` 에 이름만 적는다.

로컬 실행용 (`.env.example` 참고, 비워 두면 기본값):

| 이름 | 기본값 | 설명 |
|---|---|---|
| `MYSQL_PORT` | 13306 | MySQL 호스트 포트 |
| `GATEWAY_PORT` · `ORDER_PORT` · `PAYMENT_PORT` · `INVENTORY_PORT` | 8090 · 8091 · 8092 · 8093 | 쇼핑몰 서비스 호스트 포트 |
| `MYSQL_USER` · `MYSQL_PASSWORD` | shop · shop | 로컬 전용 계정 |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | http://localhost:4317 | OTel Java Agent 가 보낼 수집기 gRPC 주소. 컨테이너끼리는 `otel/agent.properties` 의 `http://collector:4317` (공용 네트워크 `monimo-dev`) |
| `OTEL_SERVICE_NAME` | (컨테이너별) | `shop-gateway` · `shop-order` · `shop-payment` · `shop-inventory` |

## 포트

| 서비스 | 포트 |
|---|---|
| (준비 중) | |

## 관련 문서

- [설계 문서 (결정 기록 원본)](https://github.com/2026-techeer-project-team-b/monimo-backend/tree/main/docs/design): monimo-backend 레포의 `docs/design/`
- [레포별 파일 구성](https://app.notion.com/p/3e1d7d6851ff80a8a110e8aea0b5783b)
- [깃허브 레포지토리 규칙](https://app.notion.com/p/3dcd7d6851ff8000b795f1cc609124e6)

## 기여 규칙

- `main` 직접 push 금지, PR로만 머지
- 브랜치: `feat/<이슈번호>-<설명>` · `fix/<이슈번호>-<설명>` · `chore/<설명>`
- 커밋: `<타입>(<범위>): <요약>` (타입: feat · fix · docs · chore · refactor · test)
