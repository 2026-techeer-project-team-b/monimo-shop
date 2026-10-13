package com.monimo.shop.agentextension

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import java.lang.management.ManagementFactory
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** 이 테스트 JVM 의 진짜 덤프로 모양과 연타 보호(10초)를 본다 */
class ThreadDumperTest : BehaviorSpec({
    class MovableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }

    Given("덤프") {
        val clock = MovableClock(Instant.parse("2026-10-08T00:00:00Z"))
        val dumper = ThreadDumper(ManagementFactory.getThreadMXBean(), clock)

        When("처음 뜨면") {
            val dump = dumper.dump()
            Then("jstack 모양으로 스레드 이름 · 상태 · at 줄이 나온다") {
                dump.threadCount() shouldBeGreaterThan 0
                dump.text() shouldContain "\"${Thread.currentThread().name}\" #"
                dump.text() shouldContain "\tat "
                dump.text() shouldContain "RUNNABLE"
            }
            Then("스레드마다 스택이 128줄을 넘지 않는다") {
                dump.text().split("\n\n").forEach { block ->
                    block.lines().count { it.startsWith("\tat ") } shouldBeLessThanOrEqual ThreadDumper.MAX_DEPTH
                }
            }
        }
        When("10초 안에 또 오면") {
            val first = dumper.dump()
            clock.now = clock.now.plusSeconds(9)
            Then("직전 결과를 그대로 준다 (JVM 을 다시 멈추지 않는다)") {
                (dumper.dump() === first) shouldBe true
            }
        }
        When("10초가 지나면") {
            val first = dumper.dump()
            clock.now = clock.now.plusSeconds(10)
            Then("새로 뜬다") {
                (dumper.dump() === first) shouldBe false
                dumper.dump().takenAt() shouldNotBe first.takenAt()
            }
        }
    }
})
