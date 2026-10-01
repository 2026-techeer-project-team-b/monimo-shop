package com.monimo.shop.payment

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import java.net.http.HttpClient
import java.time.Duration

/**
 * 결제사(pg-stub) 호출용 RestTemplate. OTel Java Agent 가 자동 계측해 클라이언트 스팬을 남긴다 (코드에는 계측 없음).
 * 읽기 타임아웃 3초: pg-slow(1.5초)는 기다리고, order 의 읽기 타임아웃(5초)보다는 먼저 포기해 order 가 502 로 답할 수 있게.
 *
 * HTTP/1.1 고정: 기본 JDK 클라이언트는 평문 http 에서도 HTTP/2 업그레이드(h2c)를 먼저 시도하는데,
 * pg-stub(WireMock · Jetty)은 업그레이드를 받은 뒤 스트림을 끊어(RST_STREAM) 모든 승인이 실패한다. 톰캣(order → payment)은 이 문제가 없다.
 */
@Configuration
class HttpClientConfig {
    @Bean
    fun restTemplate(builder: RestTemplateBuilder): RestTemplate =
        builder
            .requestFactoryBuilder(
                ClientHttpRequestFactoryBuilder.jdk().withHttpClientCustomizer { it.version(HttpClient.Version.HTTP_1_1) },
            )
            .connectTimeout(Duration.ofSeconds(1))
            .readTimeout(Duration.ofSeconds(3))
            .build()
}
