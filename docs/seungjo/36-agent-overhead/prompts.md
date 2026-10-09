# 2026-10-08 ~ 09 에이전트 오버헤드 측정

- 관련 이슈 : `#36`
- 쓴 도구 : 웹 리서치 서브에이전트 1개, backend `10-requirements.md` · FN-10 · ADR `#14` `#33` 읽기, ClickHouse `monimo.metrics_raw` · `spans` 조회, 로컬 파일럿 측정(붙임 · 뗌 3회, 부하 0 · 40건, 샘플링 10%, 로그 · 메트릭, 힙 고정, NMT)
- 결과물 : `perf/overhead/` 측정 스크립트, [`results.md`](results.md). 설계 한 장은 [`README.md`](README.md)

## 프롬프트

시작 (승조, 원문) :

```
오케이 일단 그럼 오버헤드 부터 하면 된다는 거지?
```
```
시작하자
```

조사 단계 (메인 대화 → 웹 리서치 서브에이전트, 원문) :

```
Research for measuring the overhead of the OpenTelemetry Java Agent v2.31.1 (SDK 1.65) on a Spring Boot 3.5 / Java 17 app running in Docker. Target budget: app CPU overhead ≤ 3% and memory overhead ≤ 100MB. Answer in Korean, each answer with source links (prefer official opentelemetry.io docs, the opentelemetry-java-instrumentation GitHub repo, OpenJDK/Oracle docs, Docker docs). Be precise; say "확인 못 함" when not verifiable.

Questions:
1. Does OTel publish official overhead guidance / numbers? (opentelemetry.io "Performance" page for the Java agent; the repo's `benchmark-overhead` module — what it measures (startup time, CPU, heap, GC, throughput, p95) and its methodology: petclinic + k6, none vs agent, warmup, repeats. Any recent published result numbers.)
2. What does "CPU 3%" usually mean in APM overhead measurements: relative increase of process CPU at same request rate vs absolute percentage points? How do others (Pinpoint, Elastic APM, Datadog, New Relic docs) state their overhead targets?
3. Memory: How to separate agent memory cost — heap vs non-heap (metaspace, code cache, threads, direct buffers). Use of `-XX:NativeMemoryTracking=summary` + `jcmd <pid> VM.native_memory summary` (overhead of NMT itself), container RSS via `docker stats` / cgroup memory.current, and JVM metrics jvm.memory.used. Which is the fair number for "100MB"?
4. How to run the same image without the agent fairly: `OTEL_JAVAAGENT_ENABLED=false` (otel.javaagent.enabled) — does the agent then skip installing everything (and extensions)? Residual cost? Alternative: remove -javaagent via entrypoint override.
5. Agent settings that dominate overhead: sampler (always_on), BatchSpanProcessor queue sizes defaults (max queue 2048, export batch 512, schedule 5s), metric export interval default (60s), logs export, number of instrumentations; how heap usage scales with queue size; `otel.javaagent.experimental...` not needed. Also JVM container flags: default MaxRAMPercentage 25% in containers.
6. CPU measurement tools: docker stats (how it computes CPU%), cgroup cpu.stat usage_usec, `process.cpu.time` / `jvm.cpu.time` OTel metric, `ps`. Recommended way to get stable comparison (fixed k6 constant-arrival-rate, warm up JIT, repeat N times, compare medians).
7. Startup time overhead of the agent (typical seconds) — relevant since it's measured too.

Keep the report focused, ≤ 900 Korean words plus a source list.
```

1차 질문 · 2차 정리 (승조) : research 3 절 Q1 ~ Q10. 2차 질문은 패스했다.

구현 단계 (research 8 절, 승조 결정 원문. 승조가 Q10 에 쓴 자기 정리를 AI 가 틀린 곳만 고치고 미정 항목 A ~ H 를 추천안으로 덧붙였고, 승조가 그대로 확정) :

```
힙에서 스팬 데이터가 만들어지고 큐에 있다가 데이터가 전송이 되면 쓰레기가 되고, GC가 돌 때 힙이 비워진다 (그래서 에이전트는 메모리보다 GC 일거리로 CPU를 더 쓴다)
비힙에서는

원래 앱 중간중간에 추적 코드를 넣어줘야 하는데 그 추적코드를 비힙에 넣어야지 실행이 가능 그래서 추적할 대상이 많을 수록 비힙이 많이 찬다 (즉 기존 코드에 없는 게 많아지면 비힙이 많이 찬다) 여기에 에이전트 자기 클래스가 대상과 상관없이 고정으로 더해진다 (실측 클래스 약 5,000개, 비힙 약 45~60MB)

스레드 스텍은 에이전트 배경 스레드(약 12개)와 Extension 스레드(1개)만큼 늘어난다 (실측 +11~13개, 1~2MB로 작다)
기타 네이티브: JVM 바깥 메모리

주 지표는 docker stats MEM / cgroup memory.current 원인 분석은 NMT로 진행하는 걸로

에이전트는 OTEL_JAVAAGENT_ENABLED=false의 경우에는 어쨋든 부팅 시 에이전트를 읽기 때문에 아주 미세한 메모리가 점유될 수 있다는 것이 있어 조금 귀찮지만 엄밀한 수치 측정을 위해서는 -javaagent 옵션 완전 제거를 사용하는 것이 좋을 거 같다

계측 개수가 OTel 자바 에이전트 내부에 그물망이 있고 이 그물망은 서비스가 사용하는 기술스택 등에 매칭이 되기에 서비스에서 사용하는 기술스택이 많을 수록 비힙을 많이 먹는다는 말이다 안 걸리는 그물은 비힙을 거의 안 먹고 부팅 때 대 보는 비용만 든다. 그래서 안 쓰는 것을 끄면 부팅이 빨라지고, 쓰지만 필요 없는 것을 끄면 메모리·CPU가 준다

OTel 야간 벤치: 메모리가 약 100MB 늘어난다 (CPU는 머신이 포화라 비교 불가). 우리 100MB 목표가 빠듯하다는 근거
Elastic EDOT: CPU는 늘지만 작다 (+0.43%p)
PR #20003: 부팅시간 줄이는 거

비교는 cgroup으로 하는 것이 좋고(떗을 때 안떗을 때 비교가 쉬우니까)
jvm.memory.used를 사용하면 에이전트 때면 지표가 사라지니까 비교에는 사용 안하고, 붙인 쪽 안을 쪼개 볼 때 보조로만 쓴다

jvm.cpu.recent_utilization는 에이전트를 떼면 지표가 사라지니까 사용하지 않는 방향으로 가면 좋을 거 같다 (CPU 제한이 없으면 서버 기준, 제한을 걸면 컨테이너 기준으로 바뀐다)

healthy 대기는 부팅 비용을 측정에서 빼려고 사용하는 거고 그 뒤 워밍업 40초는 JIT를 데우는 구간이라 따로 버린다

CPU 3%는 상대 증가율, CPU 1개 대비 %p, 요청당 ms를 다 재서 적고 판정은 상대 증가율로 하자. 쇼핑몰이 가벼운 앱이라 크게 나온다는 이유도 같이 적자

메모리는 부하 중 cgroup 메모리 차이로 판정하고 부하 없을 때 차이와 NMT도 같이 적자

컨테이너마다 CPU 1개 메모리 1GB 제한을 걸고, 힙은 고정 안 한다

부하는 초당 10, 20, 40건, 붙임 뗌 5회씩 번갈아 돌려서 중앙값을 보자

설정은 안 바꾸고 메트릭 끄기, 안 쓰는 계측 끄기 실험 두 개만 더 해서 어디서 비용이 드는지만 보자. 설정 변경은 다음 이슈로

스크립트는 레포 perf/overhead/에 두고 결과는 results.md에 남기자

요구사항 문장은 결과랑 같이 고친 문장을 backend에 제안하고 고칠지는 팀이 정하자
```

이어서 (승조, 원문) :

```
2차질문은 패스하고 위 내용을 기반으로 프롬프트를 작성하고 구현까지 하자
```
