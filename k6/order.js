// 주문 부하 + 에러 · 지연 주입 시나리오.
// 초당 RATE 건의 주문을 DURATION 동안 넣고, 비율만큼 결제 서비스 자체 문제(ERROR_RATE 502 · SLOW_RATE 2초)와
// 외부 결제사 문제(PG_ERROR_RATE 502 · PG_SLOW_RATE 1.5초)를 섞는다. 콜트리에서 "우리 문제 vs 결제사 문제" 를 구분해 보는 재료다.
// 비율은 난수가 아니라 요청 순번으로 정한다. ERROR_RATE=0.3 이면 100건 중 정확히 30건이 502 라서, 알림 규칙 시험 때 대조할 수 있다.
//
// 실행 (레포 루트, compose 로 쇼핑몰을 켠 뒤):
//   docker run --rm --network monimo-dev -v "$PWD/k6:/scripts" -e BASE_URL=http://gateway:8090 \
//     grafana/k6:2.3.0 run /scripts/order.js
// 값 바꾸기: -e RATE=5 -e DURATION=20s -e ERROR_RATE=0.3 -e SLOW_RATE=0.1 -e PG_ERROR_RATE=0.1 -e PG_SLOW_RATE=0.1

import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090'; // 게이트웨이(입구). 주문 서비스를 직접 치려면 8091
const RATE = Number(__ENV.RATE || 10); // 초당 주문 수
const DURATION = __ENV.DURATION || '1m';
const ERROR_RATE = Number(__ENV.ERROR_RATE || 0); // 0 ~ 1. payment-error 로 502 를 낼 비율
const SLOW_RATE = Number(__ENV.SLOW_RATE || 0); // 0 ~ 1. payment-slow 로 2초 지연시킬 비율
const PG_ERROR_RATE = Number(__ENV.PG_ERROR_RATE || 0); // 0 ~ 1. pg-error 로 외부 결제사 503(→ 주문 502)을 낼 비율
const PG_SLOW_RATE = Number(__ENV.PG_SLOW_RATE || 0); // 0 ~ 1. pg-slow 로 외부 결제사를 1.5초 지연시킬 비율

// X-Shop-Fault 헤더 이름과 값은 쇼핑몰 코드(PaymentService · PaymentClient) · pg-stub 응답 정의와의 약속이다. 바꾸면 같이 바꾼다
const FAULT_HEADER = 'X-Shop-Fault';
const SLOW_MILLIS = 2000;
const PG_SLOW_MILLIS = 1500;

const RATES = [ERROR_RATE, SLOW_RATE, PG_ERROR_RATE, PG_SLOW_RATE];
if (RATES.some((r) => r < 0) || RATES.reduce((a, b) => a + b, 0) > 1) {
  throw new Error(`ERROR_RATE · SLOW_RATE · PG_ERROR_RATE · PG_SLOW_RATE 는 각각 0 이상, 합이 1 이하여야 한다 (${RATES})`);
}

export const options = {
  scenarios: {
    orders: {
      // 응답이 느려져도 초당 요청 수를 유지한다 (지연 주입 중에도 부하가 줄지 않게)
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      // 지연 요청은 2초 넘게 VU 를 잡고 있으므로 넉넉히
      preAllocatedVUs: Math.max(5, RATE * 3),
      maxVUs: Math.max(20, RATE * 10),
    },
  },
  thresholds: {
    // 모든 요청이 "기대한" 응답이어야 한다. 에러 주입한 요청이 502 인 것은 기대한 결과다
    checks: ['rate==1'],
  },
};

// 요청 순번(0, 1, 2, …)을 100 으로 나눈 나머지로 종류를 정한다. 구간 순서: payment-error → payment-slow → pg-error → pg-slow → none
const FAULTS = [
  ['payment-error', ERROR_RATE],
  ['payment-slow', SLOW_RATE],
  ['pg-error', PG_ERROR_RATE],
  ['pg-slow', PG_SLOW_RATE],
];
function faultOf(iteration) {
  const bucket = iteration % 100;
  let upper = 0;
  for (const [fault, rate] of FAULTS) {
    upper += rate;
    if (bucket < Math.round(upper * 100)) return fault;
  }
  return 'none';
}

export default function () {
  const fault = faultOf(exec.scenario.iterationInTest);
  const headers = { 'Content-Type': 'application/json' };
  if (fault !== 'none') headers[FAULT_HEADER] = fault;

  const res = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify({ productId: 'P-100', quantity: 1, amount: 1000 }),
    // fault 태그로 결과 요약에서 종류별로 나눠 볼 수 있다
    { headers, tags: { fault } },
  );

  if (fault === 'payment-error' || fault === 'pg-error') {
    // 결제 서비스 자체 실패(500)든 결제사 실패(503 → 502)든 주문은 502 로 답한다
    check(res, {
      '에러 주입 → 502': (r) => r.status === 502,
      '에러 주입 → FAILED': (r) => r.json('status') === 'FAILED',
    }, { fault });
  } else if (fault === 'payment-slow' || fault === 'pg-slow') {
    const min = fault === 'payment-slow' ? SLOW_MILLIS : PG_SLOW_MILLIS;
    check(res, {
      '지연 주입 → 201': (r) => r.status === 201,
      [`지연 주입 → ${min}ms 이상`]: (r) => r.timings.duration >= min,
    }, { fault });
  } else {
    check(res, {
      '정상 → 201': (r) => r.status === 201,
      '정상 → PAID': (r) => r.json('status') === 'PAID',
    }, { fault });
  }
}
