#!/usr/bin/env bash
# #36 결정대로 전부 돈다 (맥북 기준 약 2시간 40분). 결과는 out/results.csv, 요약은 summarize.py
#   ① 초당 10 · 20 · 40건 × 붙임 · 뗌 5회씩, 회차마다 순서를 번갈아 (기계 흔들림이 한쪽에 몰리지 않게.
#      홀수 번 반복이면 붙임이 먼저인 쌍이 하나 더 많다)
#   ② 부하 0 (가만히 60초) × 붙임 · 뗌 3회씩 번갈아
#   ③ 원인 찾기 : 초당 20건에서 on · metrics-off · instr-min 3회씩 번갈아
#   ④ 메모리 쪼개기 : 초당 20건에서 nmt-on · nmt-off 1회씩
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
REPEAT=${REPEAT:-5}
pairs() { # $1 = 초당 건수, $2 = 반복 횟수
  for i in $(seq 1 "$2"); do
    if (( i % 2 )); then pair="on off"; else pair="off on"; fi
    for v in $pair; do "$HERE/run.sh" "$v" "$1"; done
  done
}
for rate in 10 20 40; do pairs "$rate" "$REPEAT"; done
pairs 0 3
for i in 1 2 3; do
  for v in on metrics-off instr-min; do "$HERE/run.sh" "$v" 20; done
done
"$HERE/run.sh" nmt-on 20
"$HERE/run.sh" nmt-off 20
