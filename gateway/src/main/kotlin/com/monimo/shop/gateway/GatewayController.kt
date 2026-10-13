package com.monimo.shop.gateway

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 쇼핑몰의 입구. 주문 API 는 주문 서비스로 방식(GET · POST 등) 상관없이, 재고는 조회(GET)만 재고 서비스로 넘긴다. */
@RestController
class GatewayController(
    private val proxy: UpstreamProxy,
    @Value("\${order.base-url}") private val orderBaseUrl: String,
    @Value("\${inventory.base-url}") private val inventoryBaseUrl: String,
) {
    @RequestMapping("/api/orders", "/api/orders/**")
    fun orders(
        request: HttpServletRequest,
        @RequestBody(required = false) body: ByteArray?,
    ): ResponseEntity<ByteArray> = proxy.forward(orderBaseUrl, request, body)

    /** 손님이 주문 전에 재고를 본다 (k6 가 섞어 보낸다). 차감 · 복원(POST)은 주문 서비스만 부르므로 GET 만 연다 (POST 는 405) */
    @GetMapping("/api/stock/**")
    fun stock(request: HttpServletRequest): ResponseEntity<ByteArray> = proxy.forward(inventoryBaseUrl, request, null)
}
