package com.ribafish.slacksummarizer.services

import com.google.ai.client.generativeai.GenerativeModel
import com.ribafish.slacksummarizer.config.GeminiConfig
import com.ribafish.slacksummarizer.models.SlackThread
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class GeminiService(private val config: GeminiConfig) {
    private val model = GenerativeModel(
        modelName = config.model,
        apiKey = config.apiKey
    )

    suspend fun summarize(thread: SlackThread): String {
        logger.debug { "Generating summary for thread with ${thread.messages.size} messages" }

        val threadContent = buildThreadContent(thread)
        val prompt = buildPrompt(threadContent)

        logger.debug { "Prompt: $prompt" }

        val response = model.generateContent(prompt)
        val summary = response.text ?: "Failed to generate summary"

        logger.debug { "Generated summary: $summary" }

        return summary
    }

    private fun buildThreadContent(thread: SlackThread): String {
        return thread.messages.joinToString("\n\n") { message ->
            "**${message.username}** (${message.timestamp}):\n${message.text}"
        }
    }

    private fun buildPrompt(threadContent: String): String {
        return """
            You are a technical documentation assistant. Your task is to summarize a Slack conversation thread
            into a clear, well-structured markdown document suitable for a knowledge base.

            Requirements:
            1. Create a clear, descriptive title based on the main topic
            2. Write a concise summary paragraph
            3. Organize key points into logical sections
            4. Include important decisions, action items, and conclusions
            5. Preserve technical details and code snippets if present
            6. Use proper markdown formatting (headers, lists, code blocks, etc.)
            7. Keep the tone professional and clear

            Format the output as a complete markdown document with:
            - A title (# heading)
            - A summary section
            - Relevant subsections organizing the content
            - Any important links or references mentioned

            Here is the Slack thread to summarize:

            $threadContent

            Generate the markdown summary:
        """.trimIndent()
    }
}
