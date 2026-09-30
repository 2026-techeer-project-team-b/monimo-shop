package com.monimo.shop.payment

import io.kotest.core.spec.style.BehaviorSpec
import org.hamcrest.Matchers.startsWith
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(PaymentController::class)
@Import(PaymentService::class) // @WebMvcTest 는 컨트롤러만 띄우므로 서비스는 직접 넣는다 (진짜 서비스로 주입 동작까지 확인)
class PaymentControllerTest(mvc: MockMvc) : BehaviorSpec({
    val body = """{"orderId":1,"amount":15000}"""

    Given("결제 요청") {
        When("X-Shop-Fault 헤더가 없으면") {
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
            Then("500 이고 reason 이 injected failure 다") {
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
    }
})
