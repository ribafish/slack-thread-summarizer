package com.ribafish.slacksummarizer

import com.ribafish.slacksummarizer.config.AppConfig
import com.ribafish.slacksummarizer.services.ClaudeService
import com.ribafish.slacksummarizer.services.GeminiService
import com.ribafish.slacksummarizer.services.GitHubService
import com.ribafish.slacksummarizer.services.SlackService
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) = runBlocking {
    logger.info { "Starting Slack Thread Summarizer..." }

    if (args.size < 2) {
        logger.error { "Usage: <channel_id> <message_ts>" }
        exitProcess(1)
    }

    val channelId = args[0]
    val messageTs = args[1]

    logger.info { "Processing thread: channel=$channelId, message=$messageTs" }

    try {
        val config = AppConfig.load()

        val slackService = SlackService(config.slack)
        val claudeService = ClaudeService(config.claude)
        val githubService = GitHubService(config.github)

        // Fetch thread
        logger.info { "Fetching thread..." }
        val thread = slackService.fetchThread(channelId, messageTs)

        if (thread.messages.isEmpty()) {
            logger.error { "No messages found in thread" }
            exitProcess(1)
        }

        logger.info { "Fetched ${thread.messages.size} messages" }

        // Summarize
        logger.info { "Generating summary with Claude..." }
        val summary = claudeService.summarize(thread)
        logger.info { "Summary generated: ${summary.length} characters" }

        // Create PR
        logger.info { "Creating pull request..." }
        val prUrl = githubService.createPullRequest(
            summary = summary,
            channelName = thread.channelName,
            timestamp = messageTs
        )

        logger.info { "✓ Pull request created: $prUrl" }
        println("PR_URL=$prUrl")

        claudeService.close()

    } catch (e: Exception) {
        logger.error(e) { "Failed to process thread: ${e.message}" }
        exitProcess(1)
    }
}
