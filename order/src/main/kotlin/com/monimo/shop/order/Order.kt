package com.monimo.shop.order

import java.time.LocalDateTime

/** orders 표 한 줄. JPA Entity 가 아니라 그냥 data class 다 (JdbcTemplate 로 직접 읽고 쓴다). */
data class Order(
    val id: Long,
    val productId: String,
    val quantity: Int,
    val amount: Int,
    val status: OrderStatus,
    val paymentId: String?,
    val createdAt: LocalDateTime,
)

/** SOLD_OUT 은 재고가 모자라 결제를 부르지 않고 끝난 주문 (409) */
enum class OrderStatus { PENDING, PAID, FAILED, SOLD_OUT }
