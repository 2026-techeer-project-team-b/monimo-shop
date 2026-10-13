package com.monimo.shop.agentextension;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;

/**
 * 에이전트가 SDK 를 만들 때 설정 · Resource 를 받아 ExtensionConfig 에 적어 둔다.
 *
 * <p>AgentListener.afterAgent 로 받는 AutoConfiguredOpenTelemetrySdk 의 getResource() · getConfig() 는
 * public 이 아니라 쓸 수 없다 (javap 확인, #34 research ⑤). 그래서 Resource 를 고치는 자리(addResourceCustomizer)에서
 * 손대지 않고 읽기만 한다. order 를 가장 크게 둬 다른 커스터마이저가 바꾼 뒤의 최종 값을 읽는다.
 */
public final class ConfigCapture implements AutoConfigurationCustomizerProvider {

    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> SERVICE_INSTANCE_ID = AttributeKey.stringKey("service.instance.id");

    @Override
    public void customize(AutoConfigurationCustomizer customizer) {
        customizer.addResourceCustomizer((resource, config) -> {
            ExtensionConfig.set(new ExtensionConfig(
                    config.getString("otel.monimo.collector.url", ExtensionConfig.DEFAULT_COLLECTOR_URL),
                    config.getString("otel.monimo.agent.token", ""),
                    resource.getAttribute(SERVICE_NAME),
                    resource.getAttribute(SERVICE_INSTANCE_ID)));
            return resource;
        });
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }
}
