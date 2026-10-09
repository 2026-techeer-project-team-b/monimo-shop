#!/usr/bin/env bash
# 한 회차 : 쇼핑몰을 띄우고 → 워밍업 → 고정 부하로 재고 → 내린다. 결과를 CSV 한 줄씩(서비스마다) 덧붙인다.
#
#   perf/overhead/run.sh <변형> <초당 건수> [측정 초] [CSV 경로]
#   변형 : on (그대로) · off (에이전트 뗌) · metrics-off · instr-min · nmt-on · nmt-off
#   초당 건수 0 이면 부하 없이 가만히 둔 채 잰다
#
# CPU 는 컨테이너 cgroup 의 cpu.stat usage_usec(지금까지 쓴 CPU 시간 누적)를 측정 구간 앞뒤로 읽어 뺀다.
# 메모리는 측정이 끝난 순간의 memory.current. 둘 다 JVM 바깥에서 읽으므로 에이전트가 있든 없든 똑같이 잰다 (#36)
set -euo pipefail

VARIANT=${1:?변형 (on · off · metrics-off · instr-min · nmt-on · nmt-off)}
RATE=${2:?초당 건수}
SECS=${3:-60}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
HERE="$ROOT/perf/overhead"
CSV=${4:-$HERE/out/results.csv}
WARMUP_SECS=40   # 막 뜬 JVM 은 JIT 전이라 무겁다. 이 구간은 버린다
SERVICES="gateway order payment inventory"
K6_IMAGE=grafana/k6:2.3.0

FILES=(-f "$ROOT/docker-compose.dev.yml" -f "$HERE/limits.yml")
case "$VARIANT" in
  on)          ;;
  off)         FILES+=(-f "$HERE/no-agent.yml") ;;
  metrics-off) FILES+=(-f "$HERE/metrics-off.yml") ;;
  instr-min)   FILES+=(-f "$HERE/instr-min.yml") ;;
  nmt-on)      FILES+=(-f "$HERE/nmt.yml") ;;
  nmt-off)     FILES+=(-f "$HERE/no-agent.yml" -f "$HERE/nmt.yml") ;;
  *) echo "모르는 변형: $VARIANT" >&2; exit 2 ;;
esac
compose() { docker compose "${FILES[@]}" "$@"; }

# 이미 떠 있는 쇼핑몰(MySQL · Redis 만 띄운 개발 중 포함)을 내려 버리지 않게, 하나라도 떠 있으면 멈춘다
if docker ps --format '{{.Names}}' | grep -q '^monimo-shop-'; then
  echo "쇼핑몰 컨테이너가 이미 떠 있다. docker compose -f docker-compose.dev.yml down 뒤 다시 실행" >&2; exit 1
fi

OUT=$(dirname "$CSV")
mkdir -p "$OUT"
[ -s "$CSV" ] || echo "time,variant,rate,secs,service,cpu_usec,mem_bytes,reqs,p95_ms,failed" > "$CSV"

# up 이 반쯤 실패해도 띄운 것은 내린다
trap 'compose down >/dev/null 2>&1 || true' EXIT
# healthy = 부팅이 끝났다. 부팅 CPU 는 측정에 넣지 않는다
compose up -d --wait >"$OUT/compose-up.log" 2>&1 || { tail -20 "$OUT/compose-up.log" >&2; exit 1; }

k6() { # $1 = 길이, 나머지 = k6 인자. check 가 틀려도(종료 코드 99) 측정은 이어 가고, 기록에 failed 로 남긴다
  local dur=$1; shift
  docker run --rm --network monimo-dev -v "$ROOT/k6:/scripts:ro" -v "$OUT:/out" -e BASE_URL=http://gateway:8090 \
    -e RATE="$RATE" -e DURATION="$dur" "$K6_IMAGE" run --quiet "$@" /scripts/order.js >"$OUT/k6.log" 2>&1 || true
}
cgroup() { # 서비스 · CPU 누적(µs) · 메모리(바이트). cgroup v2 전제 (도커 데스크톱 · 요즘 리눅스)
  for s in $SERVICES; do
    line=$(docker exec "monimo-shop-$s-1" sh -c \
      'echo "'"$s"' $(awk "/^usage_usec /{print \$2}" /sys/fs/cgroup/cpu.stat) $(cat /sys/fs/cgroup/memory.current)"')
    [ "$(wc -w <<<"$line")" -eq 3 ] || { echo "cgroup v2 값을 못 읽었다: $line" >&2; exit 1; }
    echo "$line"
  done
}

if [ "$RATE" = 0 ]; then sleep "$WARMUP_SECS"; else k6 "${WARMUP_SECS}s" || true; fi
before=$(cgroup)
SUMMARY="$OUT/k6-summary.json"
rm -f "$SUMMARY"
if [ "$RATE" = 0 ]; then sleep "$SECS"; else k6 "${SECS}s" --summary-export=/out/k6-summary.json; fi
after=$(cgroup)

read -r reqs p95 failed < <(python3 - "$SUMMARY" <<'PY'
import json, sys, os
p = sys.argv[1]
if not os.path.exists(p):
    print(0, 0, 0); sys.exit()
m = json.load(open(p))["metrics"]
print(int(m["http_reqs"]["count"]), round(m["http_req_duration"]["p(95)"], 1), m["checks"]["fails"])
PY
)
rm -f "$SUMMARY"
# k6 가 아예 안 돌았거나(이미지 · 네트워크 문제) 요청이 모자라면 이 회차를 버린다. 0 이 정상처럼 쌓이지 않게
if [ "$RATE" != 0 ] && [ "$reqs" -lt $((RATE * SECS * 95 / 100)) ]; then
  echo "k6 요청 수가 모자란다($reqs). $OUT/k6.log 확인" >&2; exit 1
fi

now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
for s in $SERVICES; do
  read -r _ u1 _ <<<"$(grep "^$s " <<<"$before")"
  read -r _ u2 mem <<<"$(grep "^$s " <<<"$after")"
  echo "$now,$VARIANT,$RATE,$SECS,$s,$((u2 - u1)),$mem,$reqs,$p95,$failed" | tee -a "$CSV"
done

# NMT 는 JVM 이 끝날 때 찍으므로 멈춘 뒤 로그에서 꺼낸다
if [[ "$VARIANT" == nmt-* ]]; then
  compose stop -t 30 $SERVICES >/dev/null 2>&1
  for s in $SERVICES; do
    docker logs "monimo-shop-$s-1" 2>&1 | sed -n '/^Native Memory Tracking:/,$p' > "$(dirname "$CSV")/nmt-$VARIANT-$s.txt"
  done
fi
