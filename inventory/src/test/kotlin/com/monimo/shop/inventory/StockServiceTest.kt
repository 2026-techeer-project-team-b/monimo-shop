package com.monimo.shop.inventory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.cache.concurrent.ConcurrentMapCacheManager

/**
 * 차감 · 복원의 판단 논리만 본다 (MySQL · Redis 없이, 저장소는 Mockito 가짜 · 캐시는 메모리).
 *  - 조건부 UPDATE 가 0 행이면 409 (SoldOut). 차감 기록 INSERT 는 트랜잭션이 롤백한다
 *  - 같은 주문이 다시 오면 차감하지 않는다
 *  - 같은 주문으로 두 번 복원하면 두 번째는 재고를 더하지 않는다
 */
class StockServiceTest : BehaviorSpec({
    fun serviceWith(repo: StockRepository): Pair<StockService, ConcurrentMapCacheManager> {
        val cache = ConcurrentMapCacheManager(StockService.CACHE)
        return StockService(repo, cache) to cache
    }

    Given("차감") {
        When("재고가 충분하면") {
            val repo = Mockito.mock(StockRepository::class.java)
            Mockito.`when`(repo.insertDeduction(1, "P-100", 2)).thenReturn(true)
            Mockito.`when`(repo.deductIfEnough("P-100", 2)).thenReturn(true)
            Mockito.`when`(repo.findQty("P-100")).thenReturn(999_998)
            val (service, cache) = serviceWith(repo)
            cache.getCache(StockService.CACHE)!!.put("P-100", StockResponse("P-100", 1_000_000))

            val res = service.deduct(DeductRequest(1, "P-100", 2), null)

            Then("남은 수량을 돌려주고 캐시를 지운다") {
                res.remaining shouldBe 999_998
                cache.getCache(StockService.CACHE)!!.get("P-100") shouldBe null
            }
        }
        When("재고가 모자라면 (조건부 UPDATE 가 0 행)") {
            val repo = Mockito.mock(StockRepository::class.java)
            Mockito.`when`(repo.insertDeduction(2, "P-SOLDOUT", 1)).thenReturn(true)
            Mockito.`when`(repo.deductIfEnough("P-SOLDOUT", 1)).thenReturn(false)
            Mockito.`when`(repo.findQty("P-SOLDOUT")).thenReturn(0)
            val (service, _) = serviceWith(repo)

            Then("SoldOut 을 던진다") {
                shouldThrow<StockService.SoldOut> { service.deduct(DeductRequest(2, "P-SOLDOUT", 1), null) }
            }
        }
        When("수량이 0 이하면") {
            val repo = Mockito.mock(StockRepository::class.java)
            val (service, _) = serviceWith(repo)
            Then("IllegalArgumentException 이고 DB 를 건드리지 않는다") {
                shouldThrow<IllegalArgumentException> { service.deduct(DeductRequest(5, "P-100", 0), null) }
                verify(repo, never()).insertDeduction(5, "P-100", 0)
            }
        }
        When("같은 주문이 다시 오면") {
            val repo = Mockito.mock(StockRepository::class.java)
            Mockito.`when`(repo.insertDeduction(1, "P-100", 2)).thenReturn(false)
            Mockito.`when`(repo.findQty("P-100")).thenReturn(999_998)
            val (service, _) = serviceWith(repo)

            val res = service.deduct(DeductRequest(1, "P-100", 2), null)

            Then("차감하지 않고 지금 수량만 돌려준다") {
                res.remaining shouldBe 999_998
                verify(repo, never()).deductIfEnough("P-100", 2)
            }
        }
        When("X-Shop-Fault: inventory-error 면") {
            val repo = Mockito.mock(StockRepository::class.java)
            val (service, _) = serviceWith(repo)
            Then("InjectedFailure 를 던지고 DB 를 건드리지 않는다") {
                shouldThrow<StockService.InjectedFailure> { service.deduct(DeductRequest(3, "P-100", 1), "inventory-error") }
                verify(repo, never()).insertDeduction(3, "P-100", 1)
            }
        }
    }

    Given("복원") {
        When("처음 복원하면") {
            val repo = Mockito.mock(StockRepository::class.java)
            Mockito.`when`(repo.findDeduction(1)).thenReturn(StockRepository.Deduction(1, "P-100", 2))
            Mockito.`when`(repo.markRestored(1)).thenReturn(true)
            Mockito.`when`(repo.findQty("P-100")).thenReturn(1_000_000)
            val (service, _) = serviceWith(repo)

            val res = service.restore(RestoreRequest(1))

            Then("재고를 더한다") {
                verify(repo).restore("P-100", 2)
                res.remaining shouldBe 1_000_000
            }
        }
        When("같은 주문을 두 번 복원하면") {
            val repo = Mockito.mock(StockRepository::class.java)
            Mockito.`when`(repo.findDeduction(1)).thenReturn(StockRepository.Deduction(1, "P-100", 2))
            Mockito.`when`(repo.markRestored(1)).thenReturn(false)
            Mockito.`when`(repo.findQty("P-100")).thenReturn(1_000_000)
            val (service, _) = serviceWith(repo)

            service.restore(RestoreRequest(1))

            Then("두 번째는 재고를 더하지 않는다 (멱등)") {
                verify(repo, never()).restore("P-100", 2)
            }
        }
        When("차감 기록이 없으면") {
            val repo = Mockito.mock(StockRepository::class.java)
            Mockito.`when`(repo.findDeduction(999)).thenReturn(null)
            val (service, _) = serviceWith(repo)
            Then("DeductionNotFound 를 던진다") {
                shouldThrow<StockService.DeductionNotFound> { service.restore(RestoreRequest(999)) }
            }
        }
    }
})
