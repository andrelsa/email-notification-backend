import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("plugin.jpa") version "1.9.25"
}

group = "com.emailnotification"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("io.mockk:mockk:1.13.13")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Integration tests use Testcontainers (jdbc:tc: URL in application-test.yml).
    // Docker must be available on the host. On macOS/Windows ensure Docker Desktop is running.
    //
    // Root cause: testcontainers-1.20.5 shades docker-java and its DefaultDockerClientConfig
    // reads the API version from the JVM system property "api.version" (not the env var
    // DOCKER_API_VERSION). Docker Engine 27+ (API minimum 1.44) rejects requests with API
    // version < 1.44 with HTTP 400. The jvmArg below forces the shaded docker-java to use
    // /v1.44/... paths so all Testcontainers strategies succeed.
    //
    // DOCKER_HOST is kept as an env var because docker-java *does* read it from the environment.
    jvmArgs("-Dapi.version=${System.getProperty("api.version") ?: "1.44"}")
    environment("DOCKER_HOST", System.getenv("DOCKER_HOST") ?: "unix:///var/run/docker.sock")
}

