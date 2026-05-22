// Backend build script — Kotlin 2.2 + Spring Boot 3.5 (JVM 17+)
// [^src: raw/tech_stack.md §Backend]
// [^src: design_&_architecture/decisions/ADR-002-backend-stack.md]
// [^src: design_&_architecture/decisions/ADR-007-api-contract.md]
// [^src: design_&_architecture/decisions/ADR-008-observability-logging.md]

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    kotlin("plugin.jpa") version "2.2.0"
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.valueinvesting"
version = "0.1.0-SNAPSHOT"

java {
    // JDK 21 toolchain matches:
    //   - .github/workflows/ci.yml (setup-java@v4 java-version: 21)
    //   - src/docker/Dockerfile (gradle:8-jdk21-alpine build, temurin:21-jre runtime)
    //   - ADR-009 §2 Build artifact (Spring Boot 3.5 LTS runtime)
    // tech_stack.md baseline is "JVM 17+" — JDK 21 satisfies the lower bound;
    // no source uses Java 21-specific syntax so a downgrade to 17 stays
    // mechanically possible if needed.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

extra["resilience4jVersion"] = "2.2.0"
extra["jjwtVersion"] = "0.12.6"
extra["springdocVersion"] = "2.8.16" // Spring Boot 3.5.x requires >= 2.8.9 [springdoc#3005]
extra["testcontainersVersion"] = "1.20.4"
extra["flywayVersion"] = "10.20.1"

dependencies {
    // Spring Boot starters [^src: design_&_architecture/decisions/ADR-002-backend-stack.md §Moduli Spring]
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux") // WebClient FMP
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Kotlin essentials
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Caching (Caffeine, used in ADR-002)
    implementation("com.github.ben-manes.caffeine:caffeine")

    // Database driver + Flyway 10.x + flyway-database-postgresql
    // [^src: raw/tech_stack.md §Database]
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core:${property("flywayVersion")}")
    implementation("org.flywaydb:flyway-database-postgresql:${property("flywayVersion")}")

    // Resilience4j 2.2 with Spring Boot 3 binding
    // [^src: raw/tech_stack.md §Backend - Resilience]
    implementation("io.github.resilience4j:resilience4j-spring-boot3:${property("resilience4jVersion")}")
    implementation("io.github.resilience4j:resilience4j-reactor:${property("resilience4jVersion")}")
    implementation("io.github.resilience4j:resilience4j-micrometer:${property("resilience4jVersion")}")

    // JJWT 0.12+ (RFC 7519/7515) [^src: design_&_architecture/decisions/ADR-006-authentication.md]
    implementation("io.jsonwebtoken:jjwt-api:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:${property("jjwtVersion")}")

    // springdoc-openapi 2.x (OpenAPI 3.1) [^src: design_&_architecture/decisions/ADR-007-api-contract.md]
    // API docs only (no swagger-ui) — avoids PathPatternParser clash on Boot 3.5 [springdoc#965]
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:${property("springdocVersion")}")

    // Observability (Micrometer + Prometheus) [^src: design_&_architecture/decisions/ADR-008-observability-logging.md]
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    // Tests — JUnit5 + Testcontainers [^src: raw/tech_stack.md §QA / Testing]
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito", module = "mockito-core")
    }
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.testcontainers:junit-jupiter:${property("testcontainersVersion")}")
    testImplementation("org.testcontainers:postgresql:${property("testcontainersVersion")}")
    testImplementation("org.wiremock:wiremock-standalone:3.10.0")
    testImplementation("org.assertj:assertj-core")
    testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val repoRoot: File = projectDir.parentFile.parentFile

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty(
        "contract.openapi.canonical",
        repoRoot.resolve("design_&_architecture/api/openapi.yaml").absolutePath,
    )
    // Print the full assertion message + stack trace so CI logs show *why* a
    // test failed (status mismatch, body diff, etc.), not just the line number.
    // STANDARD_OUT is included so Spring's server-side log.error() lines (e.g.
    // GlobalExceptionHandler.handleGeneric) reach the CI log when a controller
    // throws — otherwise we only see the MockMvc 500 status without the cause.
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT,
            org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR,
        )
        showCauses = true
        showExceptions = true
        showStackTraces = true
    }
}

tasks.register<Test>("contractCheck") {
    group = "verification"
    description = "OpenAPI contract drift check (springdoc vs openapi.yaml)"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("contract")
    }
}

// Allow JPA entities to be open without manual `open` modifier
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
