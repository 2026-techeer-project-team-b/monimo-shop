# 2026-10-06 재시작해도 그대로인 에이전트 식별자

- 관련 이슈 : `#30`
- 쓴 도구 : 웹 리서치 서브에이전트 1개, 로컬 ClickHouse 조회, 에이전트 jar 직접 열기
- 결과물 : `docker-compose.dev.yml` 3서비스에 `OTEL_RESOURCE_ATTRIBUTES`, README 쿠버네티스 Downward API 조각, 이 폴더 (설계 한 장은 [`README.md`](README.md))

## 프롬프트

시작 (승조, 원문) :

```
아 그리고 지금 쇼핑몰 order를 재시작하면 agent_id가 새로운 uuid로 바뀐대 그래서 재시작할때마다 옛에이전트가 사라지고 새 에이전트가 붙은 것처럼 보이니까 문제가 있다고하넹
그래서 급한건아닌데 쇼핑몰 AGENTS.md 에 있던 service.instance.id 채우기 할 때 파드 이름(Downward API)으로 넣어 주면된대 그러면 재시작해도 이름이 유지돼서 나중에 파드 단위로도 알릴 수 있다네용
이렇게 피드백이 왔는데 이거 부터 해결하자 

Backend에서 사용하는 방법(리서치, 프롬프트, 작업 등등 이 들어간 하네스 엔지니어링)으로 진행하자 
백엔드 파일에 docs/seungjo파일을 보면 될 거 같다
```

조사 단계 (메인 대화 → 웹 리서치 서브에이전트, 원문) :

```
조사 질문 (출처 링크 필수, 공식 문서 → 오픈소스 코드 → 블로그 순, 링크 없는 주장은 "미확인"으로 표시). 한국어로 답해줘.

배경: Spring Boot 앱에 OpenTelemetry Java Agent 2.31.1 을 -javaagent 로 붙여 OTLP gRPC 로 보낸다. 우리 백엔드는 파드 식별자를 service.instance.id → k8s.pod.name → host.name 순으로 고른다. 앱을 재시작하면 service.instance.id 가 새 UUID 로 바뀌어 에이전트가 새로 붙은 것처럼 보인다는 피드백을 받았다. 해결 제안은 "k8s Downward API 로 파드 이름을 service.instance.id 에 넣어라".

알고 싶은 것:
1. OTel Java Agent(2.x) 는 service.instance.id 를 언제부터, 어떤 코드에서 랜덤 UUID 로 채우나? (resource provider 클래스 이름, 버전, 설정으로 끌 수 있나) 
2. OTEL_RESOURCE_ATTRIBUTES 나 -Dotel.resource.attributes 로 service.instance.id 를 주면 그 UUID 를 덮어쓰나? 우선순위 근거.
3. OpenTelemetry semantic conventions 의 service.instance.id 권장값 (UUID v5, 파드 이름 등)과 k8s 에서의 권장 방식. OTel Operator 가 주입할 때 service.instance.id 를 어떻게 만드나 (namespace.podName.containerName 형태인지).
4. k8s Downward API 로 metadata.name 을 env 로 넣고 OTEL_RESOURCE_ATTRIBUTES=service.instance.id=$(POD_NAME) 처럼 쓰는 정석 예시 (k8s 공식 문서의 dependent env var $(VAR) 문법).
5. Deployment 파드 이름은 재시작 시 유지되나? (컨테이너 재시작 vs 파드 재생성/롤링 업데이트 시 이름이 바뀌는지) StatefulSet 과 차이.
6. docker compose 로컬 개발에서는 파드가 없는데, 고정 식별자를 주는 흔한 방법 (서비스 이름+인덱스, hostname 고정 등).
7. Pinpoint 는 agentId 를 어떻게 정하나 (재시작 시 유지? 파드 이름 쓰나) — 비교용.

결론은 질문 번호별로 짧게, 각 답에 출처 URL.
```

구현 단계 (research 8 절, 승조 결정 원문. AI 가 빠진 것 세 가지와 되돌리는 조건을 끼워 넣자고 제안 → 승조가 받아들임) :

```
방식이 3가지 방식이 있는데 환경변수에 사용하는 방식 사용과 이름은 파드 이름 설정으로 가자
시스템 프로퍼티의 경우 Dockerfile을 고치는 방법인데 파드마다 다른 값을 넣으려면 ENTRYPOINT를 셸 스크립트로 바꿔야 하고
exec를 빼먹으면 종료 신호가 java까지 전달되지 않기도 하고 값 변경 시 이미지 빌드를 다시해야하고 가장 센 자리라 배포 쪽(매니페스트)에서 덮을 수도 없으니까 이러한 문제들이 있으니까 패스하고 agent.properties는 모든 파드가 같은 내용이라 이름표 넣는게 무의미 하니까
환경 변수 에 적는 방법으로 하고 이 방식이 파드 이름 설정으로 하는 거랑 또 이어지니까 k8s의 Downward API로 파드 이름을 자동으로 채울 수 있으니까 이방식으로 가자
환경변수로 넣으면 agent.properties의 deployment.environment.name=local 이 사라지니까 같이 적어주고
로컬 compose는 Downward API가 없으니까 shop-order-local-1 처럼 이름을 직접 적자
나중에 두 네임스페이스(staging · prod)의 쇼핑몰이 같은 백엔드로 같이 들어오게 되면 이름이 겹칠 수 있으니까 그때는 네임스페이스.파드 로 바꾸자
```

이어서 승조 : "그 내용을 프롬프트에 넣고 이슈 만들고 이슈 브랜치 만들고 구현을 시작해줘 /oh-my-claudecode:ralph" (이슈 `#30` · 브랜치는 조사 전에 이미 만들어 둠)

## 결과 (조사 단계)

- 한 번에 됐나 : 예 (되물음 0회)
- 답 중 그대로 믿지 않고 확인한 것 : "2.31.1 에 incubator 판과 stable 판 중 무엇이 들었나 미확인" → jar 를 받아 열어 incubator 판임을 확인
- 아직 확인 안 한 것 : 환경변수가 `agent.properties` 의 같은 키를 통째로 바꾸는지 (로컬 재현 예정)
