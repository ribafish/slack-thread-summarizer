plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    application
}

group = "com.ribafish"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Slack SDK
    implementation("com.slack.api:slack-api-client:1.45.3")
    implementation("com.slack.api:slack-api-model-kotlin-extension:1.45.3")
    implementation("com.slack.api:slack-api-client-kotlin-extension:1.45.3")

    // Claude API (Anthropic)
    implementation("com.anthropic:anthropic-java:2.8.1")

    // HTTP client for Claude
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")

    // Gemini API (kept for reference, not used)
    implementation("com.google.genai:google-genai:1.22.0")

    // GitHub API
    implementation("org.kohsuke:github-api:1.319")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // Configuration
    implementation("com.typesafe:config:1.4.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Testing
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.ribafish.slacksummarizer.MainKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.ribafish.slacksummarizer.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
