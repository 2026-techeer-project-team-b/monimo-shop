package com.monimo.shop.gateway

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestTemplate
import java.io.IOException

/**
 * 스프링 없이 가짜 주문 서비스(MockRestServiceServer)를 세워, 게이트웨이가 요청을 그대로 넘기고
 * 응답을 바꾸지 않는지 본다. 실제 넘기기는 compose · CI smoke 로 확인한다.
 */
class OrderProxyTest : BehaviorSpec({
    val order = "http://order:8091"
    val json = """{"productId":"P-100","quantity":1,"amount":1000}"""

    fun proxyWithFakeOrder(): Pair<OrderProxy, MockRestServiceServer> {
        val restTemplate = RestTemplate().apply { errorHandler = PassThroughErrorHandler() } // 실제 빈과 같은 처리기
        return OrderProxy(restTemplate, order) to MockRestServiceServer.bindTo(restTemplate).build()
    }

    fun postOrder(fault: String? = null) = MockHttpServletRequest("POST", "/api/orders").apply {
        contentType = MediaType.APPLICATION_JSON_VALUE
        setContent(json.toByteArray())
        fault?.let { addHeader("X-Shop-Fault", it) }
    }

    Given("POST /api/orders") {
        When("주문이 201 을 주면") {
            Then("201 과 본문이 그대로 오고, 본문 · X-Shop-Fault 가 주문까지 넘어간다") {
                val (proxy, server) = proxyWithFakeOrder()
                server.expect(requestTo("$order/api/orders"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("X-Shop-Fault", "payment-slow"))
                    .andExpect(content().json(json))
                    .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("""{"orderId":1,"status":"PAID"}"""))

                val res = proxy.forward(postOrder("payment-slow"), json.toByteArray())

                res.statusCode shouldBe HttpStatus.CREATED
                String(res.body!!) shouldBe """{"orderId":1,"status":"PAID"}"""
                server.verify()
            }
        }
        When("주문이 502 를 주면") {
            Then("예외로 바뀌지 않고 502 와 본문이 그대로 온다") {
                val (proxy, server) = proxyWithFakeOrder()
                server.expect(requestTo("$order/api/orders"))
                    .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_JSON).body("""{"status":"FAILED"}"""))

                val res = proxy.forward(postOrder("pg-error"), json.toByteArray())

                res.statusCode shouldBe HttpStatus.BAD_GATEWAY
                String(res.body!!) shouldBe """{"status":"FAILED"}"""
            }
        }
        When("주문에 연결이 안 되면") {
            Then("502 order unavailable") {
                val (proxy, server) = proxyWithFakeOrder()
                server.expect(requestTo("$order/api/orders")).andRespond(withException(IOException("connection refused")))

                val res = proxy.forward(postOrder(), json.toByteArray())

                res.statusCode shouldBe HttpStatus.BAD_GATEWAY
                String(res.body!!) shouldBe """{"reason":"order unavailable"}"""
            }
        }
    }

    Given("GET /api/orders/1?debug=1") {
        When("주문이 404 를 주면") {
            Then("경로 · 쿼리가 그대로 넘어가고 404 가 그대로 온다") {
                val (proxy, server) = proxyWithFakeOrder()
                server.expect(requestTo("$order/api/orders/1?debug=1"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("""{"reason":"order not found"}"""))

                val res = proxy.forward(MockHttpServletRequest("GET", "/api/orders/1").apply { queryString = "debug=1" }, null)

                res.statusCode shouldBe HttpStatus.NOT_FOUND
                server.verify()
            }
        }
    }
})
