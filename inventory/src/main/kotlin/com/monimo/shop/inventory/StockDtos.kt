package com.monimo.shop.inventory

/** GET /api/stock/{productId} 응답. Redis 에 캐시되는 값이라 JSON 으로 직렬화된다 (CacheConfig 참고). */
data class StockResponse(
    val productId: String,
    val qty: Int,
)

/** POST /api/stock/deduct 요청. 주문 서비스가 결제 전에 보낸다. orderId 는 같은 주문이 두 번 차감 · 복원되지 않게 막는 열쇠다. */
data class DeductRequest(
    val orderId: Long,
    val productId: String,
    val quantity: Int,
)

/** POST /api/stock/restore 요청. 결제가 실패한 주문이 보낸다. */
data class RestoreRequest(
    val orderId: Long,
)

/** 차감 · 복원 응답. remaining 은 처리 뒤 남은 수량 */
data class StockChangeResponse(
    val orderId: Long,
    val productId: String,
    val remaining: Int,
)
