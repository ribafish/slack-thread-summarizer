package com.ribafish.slacksummarizer.config

import com.typesafe.config.ConfigFactory
import java.io.File

data class AppConfig(
    val slack: SlackConfig,
    val gemini: GeminiConfig,
    val github: GitHubConfig
) {
    companion object {
        fun load(): AppConfig {
            val config = ConfigFactory.systemEnvironment()
                .withFallback(
                    if (File("application.conf").exists()) {
                        ConfigFactory.parseFile(File("application.conf"))
                    } else {
                        ConfigFactory.empty()
                    }
                )
                .withFallback(ConfigFactory.load())

            return AppConfig(
                slack = SlackConfig(
                    botToken = config.getString("slack.botToken")
                ),
                gemini = GeminiConfig(
                    apiKey = config.getString("gemini.apiKey"),
                    model = config.getString("gemini.model")
                ),
                github = GitHubConfig(
                    token = config.getString("github.token"),
                    repoOwner = config.getString("github.repoOwner"),
                    repoName = config.getString("github.repoName"),
                    branchPrefix = config.getString("github.branchPrefix")
                )
            )
        }
    }
}

data class SlackConfig(
    val botToken: String
)

data class GeminiConfig(
    val apiKey: String,
    val model: String
)

data class GitHubConfig(
    val token: String,
    val repoOwner: String,
    val repoName: String,
    val branchPrefix: String
)
