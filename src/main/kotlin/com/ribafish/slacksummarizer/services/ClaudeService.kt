package com.ribafish.slacksummarizer.services

import com.ribafish.slacksummarizer.config.ClaudeConfig
import com.ribafish.slacksummarizer.models.SlackThread
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ClaudeService(private val config: ClaudeConfig) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    suspend fun summarize(thread: SlackThread): String = withContext(Dispatchers.IO) {
        logger.debug { "Generating summary for thread with ${thread.messages.size} messages using Claude" }

        val threadContent = buildThreadContent(thread)
        val prompt = buildPrompt(threadContent)

        logger.debug { "Sending request to Claude API" }

        try {
            val response = client.post("https://api.anthropic.com/v1/messages") {
                header("x-api-key", config.apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)

                setBody(ClaudeRequest(
                    model = config.model,
                    maxTokens = 4096,
                    messages = listOf(
                        Message(role = "user", content = prompt)
                    )
                ))
            }

            val claudeResponse = response.body<ClaudeResponse>()
            val summary = claudeResponse.content.firstOrNull()?.text ?: "Failed to generate summary"

            logger.debug { "Generated summary: ${summary.length} characters" }

            summary
        } catch (e: Exception) {
            logger.error(e) { "Error calling Claude API: ${e.message}" }
            throw e
        }
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

    fun close() {
        client.close()
    }
}

@Serializable
data class ClaudeRequest(
    val model: String,
    val maxTokens: Int,
    val messages: List<Message>
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    val model: String,
    val stopReason: String? = null,
    val usage: Usage
)

@Serializable
data class ContentBlock(
    val type: String,
    val text: String
)

@Serializable
data class Usage(
    val inputTokens: Int,
    val outputTokens: Int
)
