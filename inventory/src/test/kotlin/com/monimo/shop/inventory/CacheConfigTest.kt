package com.monimo.shop.inventory

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * Redis 에 넣은 값을 다시 읽을 수 있는지 본다. 리뷰에서 "Kotlin data class 라 기본 Jackson 으로는 되읽지 못해
 * 모든 조회가 미스가 된다" 가 잡혀서 넣은 테스트다. Redis 없이 직렬화기만 돌린다.
 */
class CacheConfigTest : BehaviorSpec({
    val serializer = CacheConfig().stockSerializer()

    Given("StockResponse 를 캐시 값으로") {
        When("직렬화했다 다시 읽으면") {
            val bytes = serializer.serialize(StockResponse("P-100", 999_998))!!
            Then("같은 값이고 JSON 은 사람이 읽을 수 있다") {
                String(bytes) shouldBe """{"productId":"P-100","qty":999998}"""
                serializer.deserialize(bytes) shouldBe StockResponse("P-100", 999_998)
            }
        }
    }
})
