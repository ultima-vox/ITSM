FROM eclipse-temurin:25-jdk-alpine@sha256:5ecfde8e5ecde5954ea3721155b345ef56c1d579b940c761318ad4c05959a151 AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew ./
RUN chmod +x gradlew && ./gradlew --version
COPY build.gradle.kts settings.gradle.kts ./
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0
RUN apk upgrade --no-cache && \
    apk add --no-cache wget && \
    addgroup -S itsm && adduser -S -G itsm itsm
WORKDIR /app
COPY --from=build /workspace/build/libs/itsm-platform-*.jar app.jar
# Numeric so Kubernetes can verify runAsNonRoot without resolving /etc/passwd.
USER 100:101
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
