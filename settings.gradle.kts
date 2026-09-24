rootProject.name = "monimo-shop"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // 팀원 컴퓨터에 JDK 17이 없으면 Gradle이 자동으로 내려받는다 (monimo-backend 와 같은 방식).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

// 쇼핑몰 4개(감시 대상) + agent-extension(우리가 만드는 유일한 에이전트 코드).
// Phase 1a 는 order · payment 만 내용이 있고, gateway · inventory 는 1b 까지 빈 앱이다 (ADR #17).
include(
    "gateway",
    "order",
    "payment",
    "inventory",
    "agent-extension",
)
