package com.monimo.shop.payment

import io.kotest.core.spec.style.BehaviorSpec
import org.hamcrest.Matchers.startsWith
import org.mockito.Mockito
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * 진짜 PaymentService 에 가짜 결제사(PgClient)를 넣어 본다. pg-stub 컨테이너 없이 돈다.
 * 실제 pg-stub 까지의 흐름은 compose · CI smoke 로 확인한다.
 */
@WebMvcTest(PaymentController::class)
@Import(PaymentService::class, PaymentControllerTest.FakePg::class)
class PaymentControllerTest(mvc: MockMvc, pgClient: PgClient) : BehaviorSpec({
    val body = """{"orderId":1,"amount":15000}"""

    Given("결제 요청") {
        When("X-Shop-Fault 헤더가 없으면") {
            Mockito.`when`(pgClient.approve(1, 15000, null))
                .thenReturn(PgClient.ApprovalResponse("pg-12345678", "APPROVED"))

            Then("200 이고 status 가 APPROVED, paymentId 는 pay- 로 시작한다") {
                mvc.post("/api/payments") {
                    contentType = MediaType.APPLICATION_JSON
                    content = body
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.status") { value("APPROVED") }
                    jsonPath("$.paymentId") { value(startsWith("pay-")) }
                }
            }
        }
        When("X-Shop-Fault: payment-error 를 붙이면") {
            Then("결제사까지 가지 않고 500 이고 reason 이 injected failure 다") {
                mvc.post("/api/payments") {
                    contentType = MediaType.APPLICATION_JSON
                    content = body
                    header(PaymentService.FAULT_HEADER, PaymentService.FAULT_ERROR)
                }.andExpect {
                    status { isInternalServerError() }
                    jsonPath("$.reason") { value("injected failure") }
                }
            }
        }
        When("결제사가 실패하면 (X-Shop-Fault: pg-error)") {
            Mockito.`when`(pgClient.approve(1, 15000, "pg-error"))
                .thenThrow(PgClient.PgFailure(null))

            Then("502 이고 reason 이 pg failure 다 — 우리 잘못(500)과 구분") {
                mvc.post("/api/payments") {
                    contentType = MediaType.APPLICATION_JSON
                    content = body
                    header(PaymentService.FAULT_HEADER, "pg-error")
                }.andExpect {
                    status { isBadGateway() }
                    jsonPath("$.reason") { value("pg failure") }
                }
            }
        }
    }
}) {
    /** 진짜 PgClient 대신 넣을 가짜. 서비스와 테스트가 같은 가짜를 생성자로 받는다. */
    @TestConfiguration
    class FakePg {
        @Bean
        fun pgClient(): PgClient = Mockito.mock(PgClient::class.java)
    }
}
