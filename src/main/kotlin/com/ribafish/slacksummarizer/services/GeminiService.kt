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
}
