package com.monimo.shop.order

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orders")
class OrderController(private val service: OrderService) {

    /** 파수꾼 카나리와 k6 가 부르는 문. 결제 실패면 502 로 답해 5xx 비율 규칙이 잡을 수 있게 한다. */
    @PostMapping
    fun create(
        @RequestBody req: CreateOrderRequest,
        @RequestHeader(PaymentClient.FAULT_HEADER, required = false) fault: String?,
    ): ResponseEntity<CreateOrderResponse> {
        val res = service.create(req, fault)
        val status = if (res.status == OrderStatus.PAID) HttpStatus.CREATED else HttpStatus.BAD_GATEWAY
        return ResponseEntity.status(status).body(res)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<Any> =
        service.get(id)
            ?.let { ResponseEntity.ok(OrderResponse.from(it)) }
            ?: ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("reason" to "order not found"))
}
