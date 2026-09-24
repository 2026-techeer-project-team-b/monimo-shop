plugins {
    alias(libs.plugins.kotlin.jvm)
}

// OTel Java Agent 에 -Dotel.javaagent.extensions 로 얹는 jar. 스프링을 쓰지 않는다.
// 스레드 덤프 명령 수신 내용과 OTel Extension API 의존성은 후속 이슈에서 넣는다 (Q28 존치 여부 OPEN).
dependencies {
    testImplementation(libs.bundles.kotest)
    testRuntimeOnly(libs.junit.platform.launcher)
}
