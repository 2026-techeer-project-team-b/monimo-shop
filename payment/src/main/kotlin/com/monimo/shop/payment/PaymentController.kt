package com.monimo.shop.payment

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payments")
class PaymentController(private val service: PaymentService) {

    @PostMapping
    fun pay(
        @RequestBody req: PaymentRequest,
        @RequestHeader(PaymentService.FAULT_HEADER, required = false) fault: String?,
    ): PaymentResponse = service.approve(req, fault)

    /** 주입된 실패는 500 으로. 스프링 기본 처리도 500 이지만 응답 모양 `{reason}` 을 고정하려고 명시한다. */
    @ExceptionHandler(PaymentService.InjectedFailure::class)
    fun onInjected(e: PaymentService.InjectedFailure): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("reason" to e.message.orEmpty()))

    /** 결제사(pg-stub)가 실패하거나 응답이 없으면 502. "뒤의 시스템 잘못" 이라는 뜻이라 500 과 구분한다. */
    @ExceptionHandler(PgClient.PgFailure::class)
    fun onPgFailure(e: PgClient.PgFailure): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(mapOf("reason" to e.message.orEmpty()))
}
