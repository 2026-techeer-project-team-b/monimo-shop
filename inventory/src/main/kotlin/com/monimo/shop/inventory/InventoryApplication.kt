package com.monimo.shop.inventory

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 재고 (1b 에 내용 추가) 서비스. 감시 대상 쇼핑몰이라 제품 기능은 만들지 않고 호출 골격만 둔다.
 * 계측은 코드가 아니라 OTel Java Agent(-javaagent)가 한다 (ADR #33).
 */
@SpringBootApplication
class InventoryApplication

fun main(args: Array<String>) {
    runApplication<InventoryApplication>(*args)
}
