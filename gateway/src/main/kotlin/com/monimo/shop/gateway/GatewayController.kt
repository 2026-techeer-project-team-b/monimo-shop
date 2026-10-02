package com.monimo.shop.gateway

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 쇼핑몰의 입구. 주문 API 전부를 방식(GET · POST 등) 상관없이 주문 서비스로 넘긴다. */
@RestController
class GatewayController(private val orderProxy: OrderProxy) {

    @RequestMapping("/api/orders", "/api/orders/**")
    fun orders(
        request: HttpServletRequest,
        @RequestBody(required = false) body: ByteArray?,
    ): ResponseEntity<ByteArray> = orderProxy.forward(request, body)
}
