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
        channelName: String,
        timestamp: String
    ): String {
        logger.info { "Creating PR for summary from channel $channelName" }

        val repo = github.getRepository("${config.repoOwner}/${config.repoName}")
        val defaultBranch = repo.defaultBranch

        // Create a unique branch name
        val branchName = "${config.branchPrefix}${channelName}-${timestamp.replace(".", "-")}"
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

        // Generate filename from timestamp
        val filename = generateFilename(channelName, timestamp)
        val filePath = "summaries/$filename"

        // Check if file already exists
        try {
            val existingFile = repo.getFileContent(filePath, branchName)
            logger.warn { "File $filePath already exists, will update it" }

            // Update existing file
            existingFile.update(summary, "Update summary for $channelName thread", branchName)
        } catch (e: GHFileNotFoundException) {
            // File doesn't exist, create it
            repo.createContent()
                .content(summary)
                .path(filePath)
                .branch(branchName)
                .message("Add summary for $channelName thread")
                .commit()

            logger.debug { "Created file $filePath in branch $branchName" }
        }

        // Create pull request
        val title = "Add summary: ${extractTitle(summary)}"
        val body = """
            ## Summary from Slack

            **Channel:** #$channelName
            **Thread timestamp:** $timestamp

            This PR adds a summary of a Slack thread that was marked with a :pushpin: reaction.

            ### File
            - `$filePath`
        """.trimIndent()

        val pr = repo.createPullRequest(
            title,
            branchName,
            defaultBranch,
            body
        )

        logger.info { "Pull request created: ${pr.htmlUrl}" }

        return pr.htmlUrl.toString()
    }

    private fun generateFilename(channelName: String, timestamp: String): String {
        // Convert Slack timestamp to readable date
        val epochSeconds = timestamp.substringBefore(".").toLongOrNull() ?: 0L
        val instant = Instant.ofEpochSecond(epochSeconds)
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
            .withZone(ZoneId.systemDefault())
        val dateStr = dateFormatter.format(instant)

        return "${channelName}_${dateStr}.md"
    }

    private fun extractTitle(summary: String): String {
        // Extract first heading from markdown
        val lines = summary.lines()
        for (line in lines) {
            if (line.startsWith("#")) {
                return line.removePrefix("#").trim().take(60)
            }
        }
        return "Slack Thread Summary"
    }
}
