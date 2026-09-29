package com.monimo.shop.order

import io.kotest.core.spec.style.BehaviorSpec
import org.mockito.Mockito
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime

/**
 * 컨트롤러가 서비스 결과를 올바른 HTTP 상태로 바꾸는지만 본다 (201 · 502 · 200 · 404).
 * MySQL · payment 없이 돌도록 OrderService 는 Mockito 가짜로 바꾼다. 실제 DB · HTTP 흐름은 PR #10 의 curl 로 확인했다.
 */
@WebMvcTest(OrderController::class)
@Import(OrderControllerTest.FakeService::class)
class OrderControllerTest(mvc: MockMvc, service: OrderService) : BehaviorSpec({
    val req = CreateOrderRequest(productId = "P-100", quantity = 2, amount = 15000)
    val body = """{"productId":"P-100","quantity":2,"amount":15000}"""

    Given("주문 생성") {
        When("결제가 성공하면") {
            Mockito.`when`(service.create(req, null))
                .thenReturn(CreateOrderResponse(1, OrderStatus.PAID, paymentId = "pay-12345678"))

            Then("201 이고 status 가 PAID 다") {
                mvc.post("/api/orders") {
                    contentType = MediaType.APPLICATION_JSON
                    content = body
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.status") { value("PAID") }
                    jsonPath("$.paymentId") { value("pay-12345678") }
                }
            }
        }
        When("결제가 실패하면 (X-Shop-Fault: payment-error)") {
            Mockito.`when`(service.create(req, "payment-error"))
                .thenReturn(CreateOrderResponse(2, OrderStatus.FAILED, reason = "payment error"))

            Then("502 이고 status 가 FAILED 다 — 5xx 비율 규칙이 잡을 수 있게") {
                mvc.post("/api/orders") {
                    contentType = MediaType.APPLICATION_JSON
                    content = body
                    header(PaymentClient.FAULT_HEADER, "payment-error")
                }.andExpect {
                    status { isBadGateway() }
                    jsonPath("$.status") { value("FAILED") }
                }
            }
        }
    }

    Given("주문 조회") {
        When("있는 주문이면") {
            Mockito.`when`(service.get(1))
                .thenReturn(Order(1, "P-100", 2, 15000, OrderStatus.PAID, "pay-12345678", LocalDateTime.of(2026, 9, 29, 10, 0)))

            Then("200 이고 주문 내용이 나온다") {
                mvc.get("/api/orders/1").andExpect {
                    status { isOk() }
                    jsonPath("$.orderId") { value(1) }
                    jsonPath("$.status") { value("PAID") }
                }
            }
        }
        When("없는 주문이면") {
            Mockito.`when`(service.get(999)).thenReturn(null)

            Then("404 이고 reason 이 order not found 다") {
                mvc.get("/api/orders/999").andExpect {
                    status { isNotFound() }
                    jsonPath("$.reason") { value("order not found") }
                }
            }
        }
    }
}) {
    /** 진짜 OrderService 대신 넣을 가짜. 컨트롤러와 테스트가 같은 가짜를 생성자로 받는다. */
    @TestConfiguration
    class FakeService {
        @Bean
        fun orderService(): OrderService = Mockito.mock(OrderService::class.java)
    }
}
