package com.monimo.shop.gateway

import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.time.Duration

/**
 * 주문 서비스로 넘길 때 쓰는 RestTemplate. OTel Java Agent 가 자동 계측해 traceparent 를 넣는다 (코드에는 계측 없음).
 * 읽기 타임아웃 8초: 주문은 결제를 최대 5초 기다리므로 그보다 길게 둬야 지연 주입이 게이트웨이에서 끊기지 않는다.
 */
@Configuration
class HttpClientConfig {
    @Bean
    fun restTemplate(builder: RestTemplateBuilder): RestTemplate =
        builder
            .connectTimeout(Duration.ofSeconds(2))
            .readTimeout(Duration.ofSeconds(8))
            .errorHandler(PassThroughErrorHandler())
            .build()
}

/**
 * RestTemplate 은 기본으로 4xx · 5xx 를 예외로 던진다. 게이트웨이는 주문의 502 · 404 를 그대로 돌려줘야 하므로
 * 어떤 상태 코드도 에러로 보지 않고 응답 객체를 받는다.
 */
class PassThroughErrorHandler : DefaultResponseErrorHandler() {
    override fun hasError(response: ClientHttpResponse): Boolean = false
}
