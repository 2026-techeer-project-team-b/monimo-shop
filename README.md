# monimo-shop

감시 대상 쇼핑몰 4종(게이트웨이 · 주문 · 결제 · 재고)과 OTel Java Agent 설정, 스레드 덤프 Extension

- 기술: Kotlin 2.2 · Spring Boot 3.5 · Java 17 · Gradle 8.14 (멀티모듈 5개: gateway · order · payment · inventory · agent-extension) · OTel Java Agent
- 상태: Phase 1a 주문 · 결제 호출 골격 + 에이전트 부착 + compose 완료

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

필요한 것: Docker (에이전트까지 붙여 띄울 때), JDK 17 (코드만 빌드 · 테스트할 때. 없으면 Gradle 이 자동으로 내려받는다)

### 한 번에 켜기 — compose (에이전트 부착)

MySQL · 결제 · 주문 세 컨테이너가 순서대로 뜬다(MySQL 이 준비된 뒤 주문). 이미지에 OTel Java Agent(버전 `otel/AGENT_VERSION`)가 들어 있어 `collector:4317` 로 트레이스 · 메트릭 · 로그를 보낸다. 앱 코드에는 계측이 없다(ADR #33).

```bash
docker network create monimo-dev                                 # 처음 한 번만 (backend 와 같이 쓰는 공용 네트워크)
docker compose -f docker-compose.dev.yml up -d --build --wait    # 켜기 (셋 다 healthy 가 될 때까지 기다림)
docker compose -f docker-compose.dev.yml logs order | grep -m1 "opentelemetry-javaagent - version"   # 에이전트가 붙었는지
docker compose -f docker-compose.dev.yml down                    # 끄기 (주문 데이터는 볼륨에 남음, 지우려면 down -v)

# 확인
curl -X POST localhost:8091/api/orders -H 'Content-Type: application/json' \
  -d '{"productId":"P-100","quantity":2,"amount":15000}'
```

데이터를 수집기까지 보내려면 monimo-backend 에서 `docker compose --profile collector up -d --wait` 로 수집기를 같이 켠다. 수집기가 꺼져 있어도 쇼핑몰은 정상으로 뜬다(에이전트는 전송 실패를 로그로만 남긴다).

### 코드만 빠르게 — Gradle (에이전트 없음)

```bash
./gradlew build                 # 5개 모듈 컴파일 + 테스트 + 서비스 4개 bootJar

docker compose -f docker-compose.dev.yml up -d --wait mysql     # MySQL 만 컨테이너로
./gradlew :payment:bootRun      # 8092 (터미널 하나)
./gradlew :order:bootRun        # 8091 (터미널 둘). 뜰 때 schema.sql 로 orders 표를 만들고, MySQL 이 없으면 뜨지 않는다
```

compose 로 결제 · 주문을 켜 둔 상태에서 `bootRun` 을 하면 포트가 겹친다. 둘 중 하나만 쓴다.

## API

감시 대상이라 제품 기능은 없고 호출 골격만 있다. 주문 한 건이 `order → MySQL` 과 `order → payment` 두 홉을 만든다. 파수꾼 카나리와 k6 가 이 모양을 그대로 쓴다.

| 메서드 · 경로 | 서비스 | 응답 |
|---|---|---|
| `POST /api/orders` `{productId, quantity, amount}` | order | 201 `{orderId, status: PAID, paymentId}` · 결제 실패 시 502 `{orderId, status: FAILED, reason}` |
| `GET /api/orders/{id}` | order | 200 주문 한 건 · 없으면 404 `{reason}` |
| `POST /api/payments` `{orderId, amount}` | payment (order 만 부름) | 200 `{paymentId, status: APPROVED}` · 주입 시 500 `{reason}` |

에러 · 지연 주입: 주문 요청에 헤더 `X-Shop-Fault` 를 붙이면 order 가 payment 로 넘긴다. 이름과 값은 k6 와의 약속이라 바꾸면 k6 도 같이 바꾼다.

| `X-Shop-Fault` | 동작 | 쓰임 |
|---|---|---|
| `payment-error` | payment 500 → order 502 | 5xx 비율 규칙 시험 |
| `payment-slow` | payment 가 2초 잠든 뒤 정상 응답 | p95 지연 규칙 시험 |

## 환경변수

실제 값은 레포에 올리지 않는다. `.env.example` 에 이름만 적는다.

로컬 실행용 (`.env.example` 참고, 비워 두면 기본값):

| 이름 | 기본값 | 설명 |
|---|---|---|
| `MYSQL_PORT` | 13306 | MySQL 호스트 포트 |
| `GATEWAY_PORT` · `ORDER_PORT` · `PAYMENT_PORT` · `INVENTORY_PORT` | 8090 · 8091 · 8092 · 8093 | 쇼핑몰 서비스 호스트 포트 |
| `MYSQL_USER` · `MYSQL_PASSWORD` | shop · shop | 로컬 전용 계정 |
| `MYSQL_HOST` · `MYSQL_DATABASE` | localhost · shop | 주문 서비스가 붙는 MySQL. `bootRun` 기준 기본값이고, compose 에서는 이미지 기본값 `mysql` 을 쓴다 |
| `PAYMENT_BASE_URL` | http://localhost:8092 | 주문 서비스가 부를 결제 서비스 주소. `bootRun` 기준 기본값이고, compose 에서는 이미지 기본값 `http://payment:8092` 를 쓴다 |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | http://collector:4317 | OTel Java Agent 가 보낼 수집기 gRPC 주소 (`otel/agent.properties` 와 같음, 공용 네트워크 `monimo-dev`). 다른 수집기로 보낼 때만 바꾼다 |
| `OTEL_SERVICE_NAME` | (컨테이너별) | `shop-gateway` · `shop-order` · `shop-payment` · `shop-inventory` |

## 포트

| 서비스 | 포트 | compose 서비스 이름 | 상태 |
|---|---|---|---|
| gateway | 8090 | | 빈 앱 (1b) |
| order | 8091 | `order` | 주문 API |
| payment | 8092 | `payment` | 결제 API |
| inventory | 8093 | | 빈 앱 (1b) |
| MySQL | 13306 (컨테이너 안 3306) | `mysql` | 볼륨 `shop-mysql-data` |

## 관련 문서

- [설계 문서 (결정 기록 원본)](https://github.com/2026-techeer-project-team-b/monimo-backend/tree/main/docs/design): monimo-backend 레포의 `docs/design/`
- [레포별 파일 구성](https://app.notion.com/p/3e1d7d6851ff80a8a110e8aea0b5783b)
- [깃허브 레포지토리 규칙](https://app.notion.com/p/3dcd7d6851ff8000b795f1cc609124e6)

## 기여 규칙

- `main` 직접 push 금지, PR로만 머지
- 브랜치: `feat/<이슈번호>-<설명>` · `fix/<이슈번호>-<설명>` · `chore/<설명>`
- 커밋: `<타입>(<범위>): <요약>` (타입: feat · fix · docs · chore · refactor · test)
