package com.ribafish.slacksummarizer.services

import com.ribafish.slacksummarizer.config.GitHubConfig
import mu.KotlinLogging
import org.kohsuke.github.GHFileNotFoundException
import org.kohsuke.github.GitHubBuilder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

class GitHubService(private val config: GitHubConfig) {
    private val github = GitHubBuilder()
        .withOAuthToken(config.token)
        .build()

    suspend fun createPullRequest(
        summary: String,
        channelId: String,
        channelName: String,
        timestamp: String,
        workspaceId: String?
    ): String {
        logger.info { "Creating PR for summary from channel $channelName" }

        val repo = github.getRepository("${config.repoOwner}/${config.repoName}")
        val defaultBranch = repo.defaultBranch

        // Extract title from summary for filename and branch
        val title = extractTitle(summary)
        val sanitizedTitle = sanitizeForFilename(title)

        // Create a unique branch name
        val branchName = "${config.branchPrefix}${sanitizedTitle}-${timestamp.replace(".", "-")}"
        logger.debug { "Branch name: $branchName" }

        // Get the base branch reference
        val baseRef = repo.getRef("heads/$defaultBranch")
        val baseSha = baseRef.`object`.sha

        // Create new branch
        try {
            repo.createRef("refs/heads/$branchName", baseSha)
            logger.debug { "Created branch $branchName from $baseSha" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to create branch: ${e.message}" }
            throw e
        }

        // Generate filename from title
        val filename = "${sanitizedTitle}.md"
        val filePath = "summaries/$filename"

        // Build Slack link
        val slackLink = buildSlackLink(workspaceId, channelId, timestamp)

        // Add metadata footer to summary
        val summaryWithMetadata = """
            $summary

            ---

            **Source:** [Slack Thread]($slackLink)
        """.trimIndent()

        // Check if file already exists
        try {
            val existingFile = repo.getFileContent(filePath, branchName)
            logger.warn { "File $filePath already exists, will update it" }

            // Update existing file
            existingFile.update(summaryWithMetadata, "Update summary: $title", branchName)
        } catch (e: GHFileNotFoundException) {
            // File doesn't exist, create it
            repo.createContent()
                .content(summaryWithMetadata)
                .path(filePath)
                .branch(branchName)
                .message("Add summary: $title")
                .commit()

            logger.debug { "Created file $filePath in branch $branchName" }
        }

        // Create pull request
        val prTitle = "Add KB article: $title"
        val body = """
            ## Knowledge Base Article from Slack

            **Source:** [Slack Thread]($slackLink)
            **Channel:** #$channelName

            This PR adds a knowledge base article generated from a Slack thread that was marked with a :pushpin: reaction.

            ### File
            - `$filePath`
        """.trimIndent()

        val pr = repo.createPullRequest(
            prTitle,
            branchName,
            defaultBranch,
            body
        )

        logger.info { "Pull request created: ${pr.htmlUrl}" }

        return pr.htmlUrl.toString()
    }

    private fun buildSlackLink(workspaceId: String?, channelId: String, timestamp: String): String {
        val messageId = timestamp.replace(".", "")
        return if (workspaceId != null) {
            "https://slack.com/app_redirect?team=$workspaceId&channel=$channelId&message_ts=$timestamp"
        } else {
            "https://app.slack.com/client/$channelId/thread/$channelId/$messageId"
        }
    }

    private fun sanitizeForFilename(title: String): String {
        // Remove markdown heading markers, convert to lowercase, replace spaces/special chars with hyphens
        return title
            .removePrefix("#").trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .replace(Regex("^-+|-+$"), "") // Remove leading/trailing hyphens
            .take(50) // Limit length
    }

    private fun extractTitle(summary: String): String {
        // Extract first heading from markdown
        val lines = summary.lines()
        for (line in lines) {
            if (line.startsWith("#")) {
                return line.removePrefix("#").trim()
            }
        }
        return "Slack Thread Summary"
    }
}
