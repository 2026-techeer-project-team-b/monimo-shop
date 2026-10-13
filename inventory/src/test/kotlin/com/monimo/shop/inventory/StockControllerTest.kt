package com.monimo.shop.inventory

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

/**
 * 컨트롤러가 서비스 결과를 올바른 HTTP 상태로 바꾸는지만 본다 (200 · 404 · 409 · 500).
 * MySQL · Redis 없이 돌도록 StockService 는 Mockito 가짜로 바꾼다. 실제 DB · 캐시 흐름은 compose 로 확인한다.
 */
@WebMvcTest(StockController::class)
@Import(StockControllerTest.FakeService::class)
class StockControllerTest(mvc: MockMvc, service: StockService) : BehaviorSpec({
    val deductBody = """{"orderId":1,"productId":"P-100","quantity":2}"""

    Given("재고 조회") {
        When("있는 상품이면") {
            Mockito.`when`(service.find("P-100")).thenReturn(StockResponse("P-100", 999_998))
            Then("200 이고 수량이 나온다") {
                mvc.get("/api/stock/P-100").andExpect {
                    status { isOk() }
                    jsonPath("$.qty") { value(999_998) }
                }
            }
        }
        When("없는 상품이면") {
            Mockito.`when`(service.find("P-404")).thenThrow(StockService.ProductNotFound("P-404"))
            Then("404 다") {
                mvc.get("/api/stock/P-404").andExpect { status { isNotFound() } }
            }
        }
    }

    Given("재고 차감") {
        When("재고가 있으면") {
            Mockito.`when`(service.deduct(DeductRequest(1, "P-100", 2), null)).thenReturn(StockChangeResponse(1, "P-100", 999_998))
            Then("200 이고 남은 수량이 나온다") {
                mvc.post("/api/stock/deduct") {
                    contentType = MediaType.APPLICATION_JSON
                    content = deductBody
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.remaining") { value(999_998) }
                }
            }
        }
        When("재고가 모자라면") {
            Mockito.`when`(service.deduct(DeductRequest(2, "P-SOLDOUT", 1), null)).thenThrow(StockService.SoldOut("P-SOLDOUT"))
            Then("409 이고 reason 이 sold out 이다 — 4xx 비율 규칙이 잡을 수 있게") {
                mvc.post("/api/stock/deduct") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"orderId":2,"productId":"P-SOLDOUT","quantity":1}"""
                }.andExpect {
                    status { isConflict() }
                    jsonPath("$.reason") { value("sold out") }
                }
            }
        }
        When("수량이 0 이면") {
            Mockito.`when`(service.deduct(DeductRequest(9, "P-100", 0), null)).thenThrow(IllegalArgumentException("quantity must be positive: 0"))
            Then("400 이다") {
                mvc.post("/api/stock/deduct") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"orderId":9,"productId":"P-100","quantity":0}"""
                }.andExpect { status { isBadRequest() } }
            }
        }
        When("X-Shop-Fault: inventory-error 면") {
            Mockito.`when`(service.deduct(DeductRequest(1, "P-100", 2), "inventory-error")).thenThrow(StockService.InjectedFailure())
            Then("500 이다") {
                mvc.post("/api/stock/deduct") {
                    contentType = MediaType.APPLICATION_JSON
                    content = deductBody
                    header(StockService.FAULT_HEADER, "inventory-error")
                }.andExpect { status { isInternalServerError() } }
            }
        }
    }

    Given("재고 복원") {
        When("차감 기록이 있는 주문이면") {
            Mockito.`when`(service.restore(RestoreRequest(1))).thenReturn(StockChangeResponse(1, "P-100", 1_000_000))
            Then("200 이고 되돌린 수량이 나온다") {
                mvc.post("/api/stock/restore") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"orderId":1}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.remaining") { value(1_000_000) }
                }
            }
        }
        When("차감 기록이 없는 주문이면") {
            Mockito.`when`(service.restore(RestoreRequest(999))).thenThrow(StockService.DeductionNotFound(999))
            Then("404 다") {
                mvc.post("/api/stock/restore") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"orderId":999}"""
                }.andExpect { status { isNotFound() } }
            }
        }
    }
}) {
    @TestConfiguration
    class FakeService {
        @Bean
        fun stockService(): StockService = Mockito.mock(StockService::class.java)
    }
}
