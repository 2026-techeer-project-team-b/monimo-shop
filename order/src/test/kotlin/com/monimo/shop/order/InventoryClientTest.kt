package com.monimo.shop.order

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestTemplate
import java.io.IOException

/** 가짜 재고 서비스(MockRestServiceServer)로 응답 코드가 OK · SOLD_OUT · ERROR 로 바뀌는지, X-Shop-Fault 가 넘어가는지 본다. */
class InventoryClientTest : BehaviorSpec({
    val base = "http://inventory:8093"
    fun clientWithFake(): Pair<InventoryClient, MockRestServiceServer> {
        val restTemplate = RestTemplate()
        return InventoryClient(restTemplate, base) to MockRestServiceServer.bindTo(restTemplate).build()
    }

    Given("재고 차감") {
        When("200 이면") {
            Then("OK 이고 본문 · X-Shop-Fault 가 넘어간다") {
                val (client, server) = clientWithFake()
                server.expect(requestTo("$base/api/stock/deduct")).andExpect(method(HttpMethod.POST))
                    .andExpect(header("X-Shop-Fault", "inventory-slow"))
                    .andExpect(content().json("""{"orderId":7,"productId":"P-100","quantity":2}"""))
                    .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("""{"orderId":7,"productId":"P-100","remaining":5}"""))
                client.deduct(7, "P-100", 2, "inventory-slow") shouldBe InventoryClient.DeductResult.OK
                server.verify()
            }
        }
        When("409 면") {
            Then("SOLD_OUT 이다") {
                val (client, server) = clientWithFake()
                server.expect(requestTo("$base/api/stock/deduct"))
                    .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("""{"reason":"sold out"}"""))
                client.deduct(7, "P-SOLDOUT", 1, null) shouldBe InventoryClient.DeductResult.SOLD_OUT
            }
        }
        When("500 이면") {
            Then("ERROR 다") {
                val (client, server) = clientWithFake()
                server.expect(requestTo("$base/api/stock/deduct")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))
                client.deduct(7, "P-100", 1, "inventory-error") shouldBe InventoryClient.DeductResult.ERROR
            }
        }
        When("연결이 안 되면") {
            Then("ERROR 다") {
                val (client, server) = clientWithFake()
                server.expect(requestTo("$base/api/stock/deduct")).andRespond(withException(IOException("connection refused")))
                client.deduct(7, "P-100", 1, null) shouldBe InventoryClient.DeductResult.ERROR
            }
        }
    }

    Given("재고 복원") {
        When("200 이면") {
            Then("true") {
                val (client, server) = clientWithFake()
                server.expect(requestTo("$base/api/stock/restore")).andExpect(content().json("""{"orderId":7}"""))
                    .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("""{"orderId":7,"productId":"P-100","remaining":7}"""))
                client.restore(7) shouldBe true
            }
        }
        When("404 면 (차감 기록 없음)") {
            Then("false 이고 예외가 밖으로 나가지 않는다") {
                val (client, server) = clientWithFake()
                server.expect(requestTo("$base/api/stock/restore")).andRespond(withStatus(HttpStatus.NOT_FOUND))
                client.restore(7) shouldBe false
            }
        }
    }
})
