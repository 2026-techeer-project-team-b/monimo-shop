# 레포 루트에서 빌드한다:  docker build -f inventory/Dockerfile -t monimo/shop-inventory:dev .

# 1단계: Gradle 로 실행용 jar 만들기 (테스트는 CI 몫이라 bootJar 만)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :inventory:bootJar --no-daemon

# 2단계: OTel Java Agent 내려받기. 버전은 otel/AGENT_VERSION 한 곳에서만 정한다
FROM eclipse-temurin:17-jdk AS agent
WORKDIR /otel
COPY otel/AGENT_VERSION .
RUN VERSION="$(tr -d '[:space:]' < AGENT_VERSION)" \
 && curl -fsSL -o opentelemetry-javaagent.jar \
      "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${VERSION}/opentelemetry-javaagent.jar"

# 3단계: JRE 만 있는 실행 이미지
FROM eclipse-temurin:17-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/inventory/build/libs/*.jar app.jar
COPY --from=agent /otel/opentelemetry-javaagent.jar otel/opentelemetry-javaagent.jar
COPY otel/agent.properties otel/agent.properties
USER app

# 서비스 이름은 여기서 기본값만 준다. compose · K8s 에서 필요하면 덮어쓴다 (agent.properties 에는 적지 않는 약속)
ENV OTEL_SERVICE_NAME=shop-inventory
# 컨테이너 안에서 localhost 는 자기 자신이라, MySQL · Redis 를 compose 서비스 이름으로 찾는다. 실행할 때 -e 로 덮어쓴다
ENV MYSQL_HOST=mysql \
    MYSQL_PORT=3306 \
    REDIS_HOST=redis \
    REDIS_PORT=6379

EXPOSE 8093
# 에이전트를 앱보다 먼저 JVM 에 올린다. -javaagent 는 반드시 -jar 앞 (뒤에 쓰면 앱 인자로 넘어가 조용히 무시된다)
# 앱 코드는 에이전트의 존재를 모른다 (ADR #33 비침습)
ENTRYPOINT ["java", \
  "-javaagent:/app/otel/opentelemetry-javaagent.jar", \
  "-Dotel.javaagent.configuration-file=/app/otel/agent.properties", \
  "-jar", "/app/app.jar"]
