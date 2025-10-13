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
    implementation("com.slack.api:slack-api-client:1.36.2")

    // Gemini API
    implementation("com.google.ai.client.generativeai:generativeai:0.1.2")

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
