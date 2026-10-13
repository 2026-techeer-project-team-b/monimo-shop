plugins {
    // 본체는 Java 로 쓴다. Kotlin 이면 kotlin-stdlib(약 1.7 MB)를 jar 에 넣고 이름을 바꿔 묶어야 해서
    // "Extension 의존성 0개" 합의가 깨진다 (#34 research ⑧). Kotlin 플러그인은 Kotest 테스트를 위해서만 쓴다
    java
    alias(libs.plugins.kotlin.jvm)
}

// OTel Java Agent 에 -Dotel.javaagent.extensions 로 얹는 jar. 스프링을 쓰지 않는다.
// OTel API 는 컴파일할 때만 필요하다. 실행 때는 에이전트가 같은 클래스를 가지고 있다 (compileOnly)
dependencies {
    compileOnly(libs.bundles.otel.extension)

    testImplementation(libs.bundles.otel.extension)
    testImplementation(libs.bundles.kotest)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// 이미지에서 찾기 쉽게 이름을 고정한다 (Dockerfile 이 /app/otel/agent-extension.jar 로 복사)
tasks.jar {
    archiveFileName.set("agent-extension.jar")
}
