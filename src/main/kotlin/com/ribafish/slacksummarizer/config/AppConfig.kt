package com.ribafish.slacksummarizer.config

import com.typesafe.config.ConfigFactory
import java.io.File

data class AppConfig(
    val ai: AIConfig,
    val slack: SlackConfig,
    val claude: ClaudeConfig,
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
                ai = AIConfig(
                    provider = config.getString("ai.provider")
                ),
                slack = SlackConfig(
                    botToken = config.getString("slack.botToken")
                ),
                claude = ClaudeConfig(
                    apiKey = config.getString("claude.apiKey"),
                    model = config.getString("claude.model")
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

data class AIConfig(
    val provider: String
)

data class SlackConfig(
    val botToken: String
)

data class ClaudeConfig(
    val apiKey: String,
    val model: String
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
