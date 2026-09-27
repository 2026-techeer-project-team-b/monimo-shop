package com.monimo.shop.payment

/** POST /api/payments 요청. order 서비스만 부른다. */
data class PaymentRequest(val orderId: Long, val amount: Int)

/** POST /api/payments 응답 */
data class PaymentResponse(val paymentId: String, val status: String)
