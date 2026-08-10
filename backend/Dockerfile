FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S itsm && adduser -S -G itsm itsm
WORKDIR /app
COPY --from=build /workspace/build/libs/itsm-platform-*.jar app.jar
USER itsm
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
