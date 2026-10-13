# 쇼핑몰 네 서비스(gateway · order · payment · inventory)의 이미지를 이 파일 하나로 만든다 (#42).
# 레포 루트에서 서비스 하나만 빌드할 때 :  docker build --target order -t monimo/shop-order:dev .
# compose 는 서비스마다 target 만 골라 쓴다 (docker-compose.dev.yml)
# --target 을 빼면 맨 마지막 단계(inventory)가 만들어진다. 늘 --target 을 준다
#
# 단계 연결 :  build ─┬─▶ base ──▶ gateway · order · payment · inventory
#             agent ─┘
# Gradle 은 build 단계에서 한 번만 돈다. 네 이미지가 그 결과(jar)를 나눠 가져가므로,
# 서비스마다 Dockerfile 을 두던 때처럼 Gradle 네 개가 동시에 CPU 를 다 쓰는 일이 없다.

# 1단계: Gradle 로 실행용 jar 만들기 (테스트는 CI 몫이라 bootJar 만). 스레드 덤프 Extension 도 같이 (#34)
# 캐시 마운트 : Gradle 이 받은 배포판 · 라이브러리(/root/.gradle)를 이미지 층 밖에 보관해 둔다.
# 소스가 바뀌어 이 RUN 이 다시 돌아도 이미 받은 것은 다시 받지 않는다. 이 컴퓨터의 BuildKit 에만 남는
# 로컬 · compose 용 이득이고, GitHub Actions 처럼 매번 새 러너면 매번 처음부터 받는다.
# sharing=locked : 같은 마운트를 쓰는 빌드가 동시에 돌면 차례로 쓰게 한다
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :gateway:bootJar :order:bootJar :payment:bootJar :inventory:bootJar :agent-extension:jar --no-daemon

# 2단계: OTel Java Agent 내려받기. 버전은 otel/AGENT_VERSION 한 곳에서만 정한다.
# 이 파일만 입력이라 소스가 바뀌어도 다시 받지 않는다
FROM eclipse-temurin:17-jdk AS agent
WORKDIR /otel
COPY otel/AGENT_VERSION .
RUN VERSION="$(tr -d '[:space:]' < AGENT_VERSION)" \
 && curl -fsSL -o opentelemetry-javaagent.jar \
      "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${VERSION}/opentelemetry-javaagent.jar"

# 3단계: 네 서비스 공통 실행 이미지 (JRE + 에이전트 + Extension + 에이전트 설정)
FROM eclipse-temurin:17-jre AS base
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=agent /otel/opentelemetry-javaagent.jar otel/opentelemetry-javaagent.jar
COPY otel/agent.properties otel/agent.properties
COPY --from=build /workspace/agent-extension/build/libs/agent-extension.jar otel/agent-extension.jar
USER app
# 에이전트를 앱보다 먼저 JVM 에 올린다. -javaagent 는 반드시 -jar 앞 (뒤에 쓰면 앱 인자로 넘어가 조용히 무시된다)
# 앱 코드는 에이전트의 존재를 모른다 (ADR #33 비침습)
# -Dotel.javaagent.extensions : 스레드 덤프 Extension. 에이전트 옆에 얹는 jar 라 앱 코드는 그대로다 (#34)
ENTRYPOINT ["java", \
  "-javaagent:/app/otel/opentelemetry-javaagent.jar", \
  "-Dotel.javaagent.extensions=/app/otel/agent-extension.jar", \
  "-Dotel.javaagent.configuration-file=/app/otel/agent.properties", \
  "-jar", "/app/app.jar"]

# 4단계: 서비스별 이미지. 서비스 이름은 여기서 기본값만 준다. compose · K8s 에서 필요하면 덮어쓴다
# (agent.properties 에는 적지 않는 약속). 다른 서비스 주소는 컨테이너 안 localhost 가 자기 자신이라
# compose 서비스 이름으로 찾고, 실행할 때 -e 로 덮어쓴다

# 입구. 주문 · 재고 서비스로 넘긴다
FROM base AS gateway
COPY --from=build /workspace/gateway/build/libs/*.jar app.jar
ENV OTEL_SERVICE_NAME=shop-gateway \
    ORDER_BASE_URL=http://order:8091 \
    INVENTORY_BASE_URL=http://inventory:8093
EXPOSE 8090

# 주문. MySQL · 재고 · 결제를 부른다
FROM base AS order
COPY --from=build /workspace/order/build/libs/*.jar app.jar
ENV OTEL_SERVICE_NAME=shop-order \
    MYSQL_HOST=mysql \
    MYSQL_PORT=3306 \
    INVENTORY_BASE_URL=http://inventory:8093 \
    PAYMENT_BASE_URL=http://payment:8092
EXPOSE 8091

# 결제. 외부 결제사(pg-stub)를 부른다
FROM base AS payment
COPY --from=build /workspace/payment/build/libs/*.jar app.jar
ENV OTEL_SERVICE_NAME=shop-payment \
    PG_BASE_URL=http://pg-stub:8080
EXPOSE 8092

# 재고. MySQL · Redis
FROM base AS inventory
COPY --from=build /workspace/inventory/build/libs/*.jar app.jar
ENV OTEL_SERVICE_NAME=shop-inventory \
    MYSQL_HOST=mysql \
    MYSQL_PORT=3306 \
    REDIS_HOST=redis \
    REDIS_PORT=6379
EXPOSE 8093
