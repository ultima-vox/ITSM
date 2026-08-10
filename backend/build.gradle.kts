plugins {
    java
    id("org.springframework.boot") version "3.5.14"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ru.ultimavox"
version = "0.1.0-SNAPSHOT"

extra["tomcat.version"] = "10.1.55"
extra["spring-framework.version"] = "6.2.19"
extra["spring-data-bom.version"] = "2025.0.12"
extra["micrometer.version"] = "1.15.12"
extra["jackson-bom.version"] = "2.21.4"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:1.4.0")
        mavenBom("org.testcontainers:testcontainers-bom:2.0.5")
        mavenBom("io.netty:netty-bom:4.1.136.Final")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("software.amazon.awssdk:s3:2.31.50")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")

    runtimeOnly("org.postgresql:postgresql:42.7.12")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    // Spring Modulith 1.4.0 pins ArchUnit 1.4.0, whose ASM cannot read Java 25 bytecode.
    testImplementation("com.tngtech.archunit:archunit:1.5.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("docker.api.version", System.getenv("DOCKER_API_VERSION") ?: "1.44")
    System.getenv("DOCKER_HOST")?.let { systemProperty("docker.host", it) }
}
