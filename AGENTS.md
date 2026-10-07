# AGENTS.md — 작업을 시작하기 전에 읽는 파일

> **사람이든 AI 도구든, 이 레포에서 무언가를 고치기 전에 이 파일을 먼저 읽는다.**
> 여기에는 레포 구조, 깨면 안 되는 규칙, 지금까지 한 일, 지금 막혀 있는 것이 들어 있다.
>
> 작업을 끝내면 **§5(지금까지 한 일)와 §6(막혀 있는 것)을 갱신해서 같은 PR 에 넣는다.**

## 0. 정본이 어디인가

| 무엇 | 정본 |
|---|---|
| 설계 결정(ADR) | `monimo-backend` 레포 `docs/design/01-decisions.md` |
| 로컬 실행 · API · 포트 · 환경변수 | 이 레포 `README.md` |
| 부하 시나리오 | `k6/README.md` |
| 에이전트 버전 | `otel/AGENT_VERSION` |

## 1. 레포 한눈에

감시 **대상**이 되는 쇼핑몰이다. 제품 기능은 없고, 모니모니터링이 볼 호출 모양을 만드는 것이 목적이다.

Kotlin 2.2 · Java 17 · Spring Boot 3.5 · Gradle 멀티모듈. 버전은 `gradle/libs.versions.toml` 한 곳에서 정하고 `monimo-backend` 와 같은 버전을 쓴다.

| 폴더 | 하는 일 | 포트 |
|---|---|---|
| `gateway/` | 입구. `/api/orders` 는 주문, `GET /api/stock` 은 재고 서비스로 그대로 넘긴다 | 8090 |
| `order/` | 주문. MySQL 에 저장하고 결제를 부른다 | 8091 |
| `payment/` | 결제. 외부 결제사(pg-stub)를 부른다 | 8092 |
| `inventory/` | 재고. MySQL(stock) + Redis 조회 캐시 | 8093 |
| `agent-extension/` | 스레드 덤프 명령 수신 Extension (Java, 의존성 0). 수집기 `/agent/commands` 롱폴링 → 덤프 회신. 우리가 만드는 유일한 에이전트 코드 | — |
| `otel/` | OTel Java Agent 설정(`agent.properties`)과 버전 고정 | — |
| `k6/` | 부하 · 에러 주입 시나리오 | — |
| `pg-stub/` | 더미 외부 결제사 (WireMock, 에이전트 없음) | 8099 |

주문 한 건이 만드는 호출: `gateway → order` · `order → MySQL` · `order → payment` · `payment → pg-stub`.

## 2. 깨면 안 되는 규칙

- **앱 코드에 계측을 넣지 않는다.** 트레이스 · 메트릭 · 로그는 이미지에 붙인 OTel Java Agent 가 만든다 (ADR `#33`). 스팬을 직접 만드는 코드를 넣으면 "에이전트만 붙이면 된다"는 제품 전제가 깨진다.
- **서비스 이름은 `shop-gateway` · `shop-order` · `shop-payment` · `shop-inventory`.** 적재 처리기가 이 이름을 `applications.name` 과 정확히 비교한다 (backend `#83`). 바꾸면 서버맵 화살표가 끊긴다.
- **서비스를 추가하면 `OTEL_INSTRUMENTATION_COMMON_PEER_SERVICE_MAPPING` 에 짝을 더한다.** 빠뜨리면 상대가 외부 시스템으로 잡힌다. `pg-stub` 은 넣지 않는다 (진짜 외부로 보여야 한다).
- **`X-Shop-Fault` 의 이름과 값은 k6 와의 약속이다.** 바꾸면 `k6/` 도 같은 PR 에서 바꾼다.
- **API 모양은 파수꾼 카나리와 k6 가 그대로 쓴다.** 경로 · 응답을 바꾸면 `monimo-watchdog` 담당(재범)에게 알린다.
- **에이전트 버전은 `otel/AGENT_VERSION` 한 곳.** Dockerfile 에 버전을 직접 적지 않는다.
- **비밀값은 레포에 올리지 않는다.** `.env.example` 에 이름만 둔다. 레포는 퍼블릭이다.

## 3. 작업 흐름

1. GitHub 이슈를 만든다. 제목은 커밋 형식과 같게 (`feat(order): ...`).
2. 브랜치를 판다. `feat/<이슈번호>-<설명>` · `fix/<이슈번호>-<설명>` · `chore/<설명>`. **`origin/develop` 에서 새로 판다.**
3. 커밋 메시지는 `<타입>(<범위>): <요약>`. 타입은 feat · fix · docs · chore · refactor · test.
4. PR 의 base 는 `develop`. `main` · `develop` 직접 push 는 막혀 있다.

CI 는 **build**(Gradle 컴파일 + 테스트) → **smoke**(compose 로 띄운 뒤 k6 초당 1건 · 10초, checks 100%)를 돈다. smoke 가 깨지면 호출 골격이 깨진 것이다.

## 4. 담당

| 폴더 | 담당 |
|---|---|
| 전체 | 승조 `@SeungJo-02` |
| `agent-extension/` | 승조 · 재범 `@jaebeom79` |

## 5. 지금까지 한 일

- OTel Java Agent 설정 — gRPC · `collector:4317` · 샘플러 always_on · 버전 2.31.1 (`#4`)
- Gradle 멀티모듈 뼈대 5개 모듈 (`#6`)
- 결제 승인 골격과 `X-Shop-Fault` 에러 · 지연 주입 (`#8` 1단계), 주문 API 와 MySQL 저장 · 결제 호출 (`#8` 3단계), 컨트롤러 테스트 6건 (`#8` 5·6단계)
- order · payment Dockerfile 에 에이전트 부착 (`#12`), `docker-compose.dev.yml` 로 한 번에 실행하고 `monimo-dev` 네트워크로 수집기 연결 (`#14`)
- k6 주문 부하 + 에러 · 지연 주입 시나리오 (`#16`), PR 마다 build + smoke CI (`#18`)
- 더미 외부 결제사 pg-stub 호출과 `pg-error` · `pg-slow` 주입 (`#20`)
- 서버맵에서 order → payment 가 외부로 잡히던 문제를 peer-service-mapping 으로 해결 (`#23`)
- 얇은 게이트웨이. 진입 주소를 8090 으로 (`#25`)
- 에이전트 이름표 `service.instance.id` 를 환경변수로 고정. compose 는 `shop-<서비스>-local-1`, 쿠버네티스는 Downward API 로 파드 이름 (`#30`). 재시작해도 `agent_id` 가 그대로다
- 스레드 덤프 Extension 첫 검증 (`#34`, backend `#122`). Java Extension 이 수집기에 롱폴링으로 명령을 받아 jstack 모양 덤프를 회신. 네 서비스 모두 덤프 200, Extension 호출 스팬 0건, 수집기 재시작 4~5초 뒤 재연결, 별도 리뷰 MEDIUM 4건 반영
- 재고 서비스 + Redis 캐시 (`#32`). 주문이 결제 전에 재고를 조건부 UPDATE 로 차감하고 모자라면 409, 결제 실패면 복원(주문 번호로 한 번만). 조회만 cache-aside, Redis 헬스체크는 끔(고아 스팬), timeout 500ms + DB 폴백. k6 에 재고 조회 손님 · inventory-slow · inventory-error · 품절 추가
- `AGENTS.md` · `CLAUDE.md`, README 「AI 와 일한 방법」 절, `docs/prompts/`(프롬프트 로그 : 코드와 같은 PR 에). 하네스 정본은 backend `docs/seungjo/harness.md` 한 곳

## 6. 지금 막혀 있는 것

| 무엇 | 안 풀면 |
|---|---|
| 에이전트 3.0 이 오면 서버맵의 DB 분류가 깨진다 | 새 버전은 `db.system` 대신 `db.system.name` 을 붙이는데 backend `mv_server_map_1m` 은 `db.system` 만 본다. 에이전트를 올리기 전에 backend 가 두 키를 다 보게 해야 한다 (`#32` research 2.2) |
| 스레드 덤프 API 서버 팬아웃 · 화면 · CH 저장이 없다 | 수집기 `POST /internal/thread-dump` 까지만 있다. API 서버 팬아웃(`agentUuid` → service · instance, 503 재시도)은 재범, CH `thread_dumps` 저장(Q16)은 조회 쪽. 그 전까지는 curl 로만 뜬다 |
| Redis 연결을 맺을 때 부모 없는 스팬 3개(`HELLO` · `CLIENT SETINFO`)가 남는다 | 앱 시작 · 재연결 때만 생겨 양이 작다. 헬스체크 `INFO` 는 `#32` 에서 껐다 |

## 7. 참고

- 설계 문서: https://github.com/2026-techeer-project-team-b/monimo-backend/tree/HEAD/docs/design
- 수집기까지 데이터를 보내려면 `monimo-backend` 에서 `docker compose --profile collector up -d --wait`
