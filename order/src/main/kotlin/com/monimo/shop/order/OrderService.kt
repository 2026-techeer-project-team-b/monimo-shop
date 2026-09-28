package com.monimo.shop.order

import org.springframework.stereotype.Service

/** 주문 흐름: PENDING 으로 저장 → 결제 호출 → PAID 또는 FAILED 로 갱신. */
@Service
class OrderService(
    private val repository: OrderRepository,
    private val paymentClient: PaymentClient,
) {
    fun create(req: CreateOrderRequest, fault: String?): CreateOrderResponse {
        val id = repository.insertPending(req)
        val payment = paymentClient.pay(id, req.amount, fault)
        return if (payment != null) {
            repository.updateResult(id, OrderStatus.PAID, payment.paymentId)
            CreateOrderResponse(id, OrderStatus.PAID, paymentId = payment.paymentId)
        } else {
            repository.updateResult(id, OrderStatus.FAILED, null)
            CreateOrderResponse(id, OrderStatus.FAILED, reason = "payment error")
        }
    }

    fun get(id: Long): Order? = repository.findById(id)
}
