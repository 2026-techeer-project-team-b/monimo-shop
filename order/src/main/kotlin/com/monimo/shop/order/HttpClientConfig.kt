package com.monimo.shop.order

import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import java.time.Duration

/**
 * RestTemplate 은 스프링의 동기 HTTP 클라이언트다 (Notion 「사용할 라이브러리 정리」 §3 채택).
 * OTel Java Agent 가 RestTemplate 을 자동 계측해 traceparent 헤더를 넣어 준다. 우리는 아무것도 안 한다.
 * RestTemplateBuilder 로 만들어야 타임아웃이 걸린다. new RestTemplate() 은 타임아웃이 무한이라 payment 가 죽으면 스레드가 영원히 묶인다.
 */
@Configuration
class HttpClientConfig {
    @Bean
    fun restTemplate(builder: RestTemplateBuilder): RestTemplate =
        builder
            .connectTimeout(Duration.ofSeconds(2))
            .readTimeout(Duration.ofSeconds(5))   // payment-slow(2초) · inventory-slow(1.5초) 보다 길게. 재고 · 결제 호출이 같이 쓴다
            .build()
}
