#!/usr/bin/env bash
# PR 라벨 워크플로가 쓰는 라벨을 이 레포에 한 번에 만든다. 이미 있으면 색상 · 설명만 갱신한다.
# 실행: ./.github/setup-labels.sh   (gh auth login 필요)
set -euo pipefail

# 이름:색상(hex, # 없이):설명
LABELS=(
  "type/feature:0E8A16:새 기능 추가"
  "type/bug:D73A4A:버그 / 오류"
  "type/refactor:FBCA04:리팩토링 (동작 변경 없음)"
  "type/docs:0075CA:문서"
  "type/test:BFD4F2:테스트"
  "type/chore:CFD3D7:빌드/설정/잡일"
  "type/ci:CFD3D7:CI / 배포"
  "type/style:E4E669:코드 스타일 (포맷)"
  "type/perf:5319E7:성능 개선"
  "size/XS:3CBF00:변경 <10줄"
  "size/S:5FE800:변경 <50줄"
  "size/M:FBCA04:변경 <200줄"
  "size/L:FF9F00:변경 <500줄"
  "size/XL:E11D21:변경 ≥500줄"
  "area/gateway:C5DEF5:게이트웨이"
  "area/order:C5DEF5:주문"
  "area/payment:C5DEF5:결제"
  "area/inventory:C5DEF5:재고"
  "area/agent-extension:D93F0B:스레드 덤프 Extension"
  "area/otel:5319E7:OTel Agent 설정"
  "area/k6:BFDADC:부하 · 에러 주입 시나리오"
  "area/build:FEF2C0:Gradle · Docker"
  "area/ci:CFD3D7:CI · GitHub 설정"
  "area/docs:0075CA:문서"
)

for entry in "${LABELS[@]}"; do
  IFS=":" read -r name color desc <<< "$entry"
  if gh label create "$name" --color "$color" --description "$desc" --force >/dev/null 2>&1; then
    echo "✓ $name"
  else
    echo "✗ $name (실패)"
  fi
done
