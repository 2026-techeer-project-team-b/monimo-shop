package com.monimo.shop.order

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

/**
 * 주문 흐름의 순서만 본다 (MySQL · 재고 · 결제 서비스 없이, 전부 Mockito 가짜).
 *  - 재고가 모자라면 결제를 부르지 않고 SOLD_OUT
 *  - 결제가 실패하면 재고를 되돌린다
 *  - 결제가 성공하면 되돌리지 않는다
 */
class OrderServiceTest : BehaviorSpec({
    val req = CreateOrderRequest(productId = "P-100", quantity = 2, amount = 15000)

    fun fakes(): Triple<OrderRepository, InventoryClient, PaymentClient> {
        val repo = Mockito.mock(OrderRepository::class.java)
        Mockito.`when`(repo.insertPending(req)).thenReturn(7L)
        return Triple(repo, Mockito.mock(InventoryClient::class.java), Mockito.mock(PaymentClient::class.java))
    }

    Given("주문 생성") {
        When("재고가 있고 결제가 성공하면") {
            val (repo, inventory, payment) = fakes()
            Mockito.`when`(inventory.deduct(7, "P-100", 2, null)).thenReturn(InventoryClient.DeductResult.OK)
            Mockito.`when`(payment.pay(7, 15000, null)).thenReturn(PaymentClient.PaymentResponse("pay-1", "APPROVED"))

            val res = OrderService(repo, inventory, payment).create(req, null)

            Then("PAID 이고 재고를 되돌리지 않는다") {
                res.status shouldBe OrderStatus.PAID
                verify(repo).updateResult(7, OrderStatus.PAID, "pay-1")
                verify(inventory, never()).restore(7)
            }
        }
        When("재고가 모자라면") {
            val (repo, inventory, payment) = fakes()
            Mockito.`when`(inventory.deduct(7, "P-100", 2, null)).thenReturn(InventoryClient.DeductResult.SOLD_OUT)

            val res = OrderService(repo, inventory, payment).create(req, null)

            Then("SOLD_OUT 이고 결제를 부르지 않는다") {
                res.status shouldBe OrderStatus.SOLD_OUT
                res.reason shouldBe "sold out"
                verify(payment, never()).pay(7, 15000, null)
                verify(repo).updateResult(7, OrderStatus.SOLD_OUT, null)
            }
        }
        When("재고는 줄였는데 결제가 실패하면") {
            val (repo, inventory, payment) = fakes()
            Mockito.`when`(inventory.deduct(7, "P-100", 2, "payment-error")).thenReturn(InventoryClient.DeductResult.OK)
            Mockito.`when`(payment.pay(7, 15000, "payment-error")).thenReturn(null)

            val res = OrderService(repo, inventory, payment).create(req, "payment-error")

            Then("FAILED 이고 재고를 되돌린다") {
                res.status shouldBe OrderStatus.FAILED
                res.reason shouldBe "payment error"
                verify(inventory).restore(7)
            }
        }
        When("재고 서비스가 응답하지 않으면") {
            val (repo, inventory, payment) = fakes()
            Mockito.`when`(inventory.deduct(7, "P-100", 2, null)).thenReturn(InventoryClient.DeductResult.ERROR)

            val res = OrderService(repo, inventory, payment).create(req, null)

            Then("FAILED(inventory error) 이고 결제를 부르지 않는다. 타임아웃이면 차감이 됐을 수 있어 복원은 한 번 불러 본다") {
                res.status shouldBe OrderStatus.FAILED
                res.reason shouldBe "inventory error"
                verify(payment, never()).pay(7, 15000, null)
                verify(inventory).restore(7)
            }
        }
    }
})
