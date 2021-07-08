plugins {
    application
    kotlin("jvm") version "1.3.10"
    id("com.google.cloud.tools.jib") version "3.1.2"
}

group = "example"
version = "1"

val ktor_version by extra("1.0.0")
val logback_version by extra("1.2.3")

val main_class by extra("io.ktor.server.netty.EngineMain")

application {
    mainClassName = main_class

    applicationDefaultJvmArgs = listOf(
            "-server",
            "-Djava.awt.headless=true",
            "-Xms128m",
            "-Xmx256m",
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=100"
    )
}

java.sourceCompatibility = JavaVersion.VERSION_1_8

dependencies {
    implementation(kotlin("stdlib"))

    implementation("ch.qos.logback:logback-classic:$logback_version")

    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-metrics:$ktor_version")
    implementation("io.ktor:ktor-html-builder:$ktor_version")
}

jib {
    container {
        ports = listOf("8080")
        mainClass = main_class

        // good defauls intended for Java 8 (>= 8u191) containers
        jvmFlags = listOf(
                "-server",
                "-Djava.awt.headless=true",
                "-XX:InitialRAMFraction=2",
                "-XX:MinRAMFraction=2",
                "-XX:MaxRAMFraction=2",
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=100",
                "-XX:+UseStringDeduplication"
        )
    }
}

repositories {
    mavenCentral()
}
