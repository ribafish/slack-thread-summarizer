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

        // Search for existing file with similar topic
        val existingFilePath = searchExistingArticle(repo, sanitizedTitle, defaultBranch)

        val isUpdate = existingFilePath != null
        val filePath = existingFilePath ?: "summaries/${sanitizedTitle}.md"

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

        // Build Slack link
        val slackLink = buildSlackLink(workspaceId, channelId, timestamp)

        // Prepare content based on whether we're updating or creating
        val finalContent = if (isUpdate) {
            logger.info { "Found existing article at $filePath, will extend it" }
            val existingContent = try {
                repo.getFileContent(filePath, defaultBranch).read().readBytes().decodeToString()
            } catch (e: Exception) {
                logger.warn(e) { "Could not read existing file, will create new" }
                ""
            }

            if (existingContent.isNotEmpty()) {
                mergeArticles(existingContent, summary, slackLink)
            } else {
                """
                    $summary

                    ---

                    **Sources:**
                    - [Slack Thread]($slackLink)
                """.trimIndent()
            }
        } else {
            """
                $summary

                ---

                **Source:** [Slack Thread]($slackLink)
            """.trimIndent()
        }

        // Create or update file
        try {
            val existingFile = repo.getFileContent(filePath, branchName)
            logger.debug { "File exists in new branch, updating" }
            existingFile.update(finalContent, if (isUpdate) "Update KB article: $title" else "Add KB article: $title", branchName)
        } catch (e: GHFileNotFoundException) {
            logger.debug { "Creating new file in branch" }
            repo.createContent()
                .content(finalContent)
                .path(filePath)
                .branch(branchName)
                .message(if (isUpdate) "Update KB article: $title" else "Add KB article: $title")
                .commit()
        }

        // Create pull request
        val prTitle = if (isUpdate) "Update KB article: $title" else "Add KB article: $title"
        val body = """
            ## ${if (isUpdate) "Updated" else "New"} Knowledge Base Article from Slack

            **Source:** [Slack Thread]($slackLink)
            **Channel:** #$channelName
            ${if (isUpdate) "**Action:** Extended existing article with new information" else "**Action:** Created new article"}

            This PR ${if (isUpdate) "updates an existing" else "adds a new"} knowledge base article generated from a Slack thread that was marked with a :pushpin: reaction.

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

    private fun searchExistingArticle(repo: org.kohsuke.github.GHRepository, sanitizedTitle: String, branch: String): String? {
        return try {
            // Search for files in summaries directory
            val contents = repo.getDirectoryContent("summaries", branch)

            // Look for exact match or similar filename
            val exactMatch = contents.find { it.name == "$sanitizedTitle.md" }
            if (exactMatch != null) {
                logger.debug { "Found exact match: ${exactMatch.path}" }
                return exactMatch.path
            }

            // Look for similar titles (fuzzy match)
            val similarMatch = contents.find { content ->
                val existingTitle = content.name.removeSuffix(".md")
                // Check if titles are similar (contain common words or one is substring of another)
                val titleWords = sanitizedTitle.split("-").filter { it.length > 3 }
                val existingWords = existingTitle.split("-").filter { it.length > 3 }

                // If they share 50%+ of significant words, consider it a match
                val commonWords = titleWords.intersect(existingWords.toSet())
                commonWords.size >= (titleWords.size.coerceAtMost(existingWords.size) / 2)
            }

            if (similarMatch != null) {
                logger.debug { "Found similar match: ${similarMatch.path}" }
                return similarMatch.path
            }

            null
        } catch (e: Exception) {
            logger.debug(e) { "Could not search for existing articles: ${e.message}" }
            null
        }
    }

    private fun mergeArticles(existingContent: String, newSummary: String, newSlackLink: String): String {
        // Extract existing sources section
        val sourcesRegex = Regex("""---\s*\*\*Source[s]?:\*\*(.+)$""", RegexOption.DOT_MATCHES_ALL)
        val existingSources = sourcesRegex.find(existingContent)?.groupValues?.get(1)?.trim() ?: ""

        // Remove sources section from existing content
        val contentWithoutSources = existingContent.replace(sourcesRegex, "").trim()

        // Extract keywords from new summary and merge with existing
        val newKeywordsRegex = Regex("""\*\*Keywords:\*\*\s*(.+)""")
        val newKeywords = newKeywordsRegex.find(newSummary)?.groupValues?.get(1)?.trim()?.split(",")?.map { it.trim() } ?: emptyList()

        val existingKeywordsRegex = Regex("""\*\*Keywords:\*\*\s*(.+)""")
        val existingKeywordsMatch = existingKeywordsRegex.find(contentWithoutSources)
        val existingKeywords = existingKeywordsMatch?.groupValues?.get(1)?.trim()?.split(",")?.map { it.trim() } ?: emptyList()

        // Merge keywords (deduplicate)
        val mergedKeywords = (existingKeywords + newKeywords).distinct().take(10)

        // Update keywords line in existing content
        val contentWithUpdatedKeywords = if (existingKeywordsMatch != null && mergedKeywords.isNotEmpty()) {
            contentWithoutSources.replace(existingKeywordsMatch.value, "**Keywords:** ${mergedKeywords.joinToString(", ")}")
        } else if (mergedKeywords.isNotEmpty()) {
            // Add keywords after title if not present
            val lines = contentWithoutSources.lines().toMutableList()
            val titleIndex = lines.indexOfFirst { it.startsWith("#") }
            if (titleIndex >= 0 && titleIndex + 1 < lines.size) {
                lines.add(titleIndex + 1, "")
                lines.add(titleIndex + 2, "**Keywords:** ${mergedKeywords.joinToString(", ")}")
                lines.joinToString("\n")
            } else {
                contentWithoutSources
            }
        } else {
            contentWithoutSources
        }

        // Extract main content from new summary (without title and keywords)
        val newSummaryLines = newSummary.lines()
        val newContentStart = newSummaryLines.indexOfFirst {
            !it.startsWith("#") && !it.contains("**Keywords:**") && it.isNotBlank()
        }
        val newContent = if (newContentStart >= 0) {
            newSummaryLines.drop(newContentStart).joinToString("\n")
        } else {
            newSummary
        }

        // Build new sources list
        val sourcesList = mutableListOf<String>()

        // Parse existing sources
        if (existingSources.isNotEmpty()) {
            if (existingSources.startsWith("- [Slack Thread]")) {
                sourcesList.add(existingSources.removePrefix("- "))
            } else if (existingSources.contains("\n- [Slack Thread]")) {
                existingSources.lines()
                    .filter { it.trim().startsWith("- [Slack Thread]") }
                    .forEach { sourcesList.add(it.trim().removePrefix("- ")) }
            } else {
                sourcesList.add("[Slack Thread]($existingSources)")
            }
        }

        // Add new source
        sourcesList.add("[Slack Thread]($newSlackLink)")

        val sourcesSection = if (sourcesList.size == 1) {
            "**Source:** ${sourcesList[0]}"
        } else {
            "**Sources:**\n" + sourcesList.joinToString("\n") { "- $it" }
        }

        return """
            $contentWithUpdatedKeywords

            ## Additional Context

            $newContent

            ---

            $sourcesSection
        """.trimIndent()
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
