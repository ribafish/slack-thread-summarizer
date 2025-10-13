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
            message.text
        }
    }

    private fun buildPrompt(threadContent: String): String {
        return """
            You are a technical documentation assistant. Your task is to transform a Slack conversation
            into a clear, well-structured knowledge base article in markdown format.

            Requirements:
            1. Create a clear, descriptive title based on the main topic discussed
            2. Focus entirely on the technical content, concepts, and information shared
            3. Organize information into logical sections with proper headers
            4. Extract and preserve:
               - Technical explanations and concepts
               - Code snippets and examples
               - Solutions to problems
               - Best practices and recommendations
               - Important links and references
            5. Remove conversational elements (greetings, acknowledgments, "thanks", etc.)
            6. Synthesize multiple related points into cohesive explanations
            7. Use proper markdown formatting (headers, lists, code blocks, etc.)
            8. Keep the tone professional and encyclopedic

            Do NOT include:
            - Who said what or when
            - Conversational back-and-forth
            - Off-topic discussion
            - Personal opinions unless they represent technical best practices

            Format the output as a complete markdown document with:
            - A descriptive title (# heading)
            - An overview section explaining what this article covers
            - Logical subsections organizing the technical content
            - Code blocks for any code examples
            - Links to external resources if mentioned

            Here is the conversation to transform into a knowledge base article:

            $threadContent

            Generate the knowledge base article:
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
