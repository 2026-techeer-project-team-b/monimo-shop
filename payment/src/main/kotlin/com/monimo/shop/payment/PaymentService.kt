package com.monimo.shop.payment

import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 결제 승인. 자체 처리 뒤 더미 외부 결제사(pg-stub)에 승인을 받는다.
 * X-Shop-Fault 헤더 값에 따라 일부러 실패하거나 느려진다. k6 시나리오가 이 스위치를 쓴다.
 *  - payment-error → InjectedFailure (결제 서비스 자체 문제, 컨트롤러가 500 으로 바꾼다)
 *  - payment-slow  → 2초 잠들었다 정상 응답 (결제 서비스 자체 지연)
 *  - pg-error · pg-slow → 결제사로 그대로 넘겨 결제사가 503 · 1.5초 지연 (결제사 문제, PgClient 참고)
 */
@Service
class PaymentService(private val pgClient: PgClient) {

    class InjectedFailure : RuntimeException("injected failure")

    fun approve(req: PaymentRequest, fault: String?): PaymentResponse {
        when (fault) {
            FAULT_ERROR -> throw InjectedFailure()
            FAULT_SLOW -> Thread.sleep(SLOW_MILLIS)
        }
        pgClient.approve(req.orderId, req.amount, fault)
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
