# 프롬프트 로그 — 승조

AI 에게 무엇을 어떻게 물었고 무엇이 나왔는지 남긴다. 규칙과 틀은 `monimo-backend` 의 [`docs/seungjo/README.md`](https://github.com/2026-techeer-project-team-b/monimo-backend/blob/HEAD/docs/seungjo/README.md) 가 정본이고, 프롬프트 로그 틀은 [`TEMPLATE/prompts.md`](https://github.com/2026-techeer-project-team-b/monimo-backend/blob/HEAD/docs/seungjo/TEMPLATE/prompts.md) 다. 여기에는 **이 레포에서 한 작업**의 로그만 둔다 : 코드와 **같은 PR** 에, 파일 이름 `YYYY-MM-DD-<작업>-<이슈번호>.md`. 조사 · 결정까지 한 이슈는 backend 와 같은 꼴로 [`../seungjo/<이슈번호>-<주제>/`](../seungjo/) 폴더에 두고(README · research · prompts) 여기서는 목록에 링크만 건다.

## 목록

| 날짜 | 작업 | 한 번에 | 고쳐 물음 |
|---|---|---|---|
| 2026-10-08 | 스레드 덤프 Extension 첫 검증 (`#34`, backend `#122`) : [`../seungjo/34-thread-dump-extension/prompts.md`](../seungjo/34-thread-dump-extension/prompts.md) | 예 | 0 |
| 2026-10-07 | 재고 서비스 + Redis 캐시 (`#32`) : 조사 · 결정 · 구현 원문은 [`../seungjo/32-inventory-redis/prompts.md`](../seungjo/32-inventory-redis/prompts.md) | 아니오 (구현 중 1회 : Redis 가 죽었을 때 차감이 500) | 1 |
| 2026-10-06 | 재시작해도 그대로인 에이전트 이름표 (`#30`) : 조사 · 결정 · 구현 원문은 [`../seungjo/30-service-instance-id/prompts.md`](../seungjo/30-service-instance-id/prompts.md) | 예 | 0 |
