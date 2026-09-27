package com.monimo.shop.payment

import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 결제 승인 흉내. 실제 결제사 호출은 없다 (더미 외부 결제 API 는 다음 이슈 2b).
 * X-Shop-Fault 헤더 값에 따라 일부러 실패하거나 느려진다. k6 시나리오가 이 스위치를 쓴다.
 *  - payment-error → InjectedFailure (컨트롤러가 500 으로 바꾼다)
 *  - payment-slow  → 2초 잠들었다 정상 응답 (p95 지연 규칙 시험용)
 */
@Service
class PaymentService {

    class InjectedFailure : RuntimeException("injected failure")

    fun approve(req: PaymentRequest, fault: String?): PaymentResponse {
        when (fault) {
            FAULT_ERROR -> throw InjectedFailure()
            FAULT_SLOW -> Thread.sleep(SLOW_MILLIS)
        }
        return PaymentResponse(
            paymentId = "pay-" + UUID.randomUUID().toString().take(8),
            status = "APPROVED",
        )
    }

    companion object {
        const val FAULT_HEADER = "X-Shop-Fault"
        const val FAULT_ERROR = "payment-error"
        const val FAULT_SLOW = "payment-slow"
        const val SLOW_MILLIS = 2_000L
    }
}
