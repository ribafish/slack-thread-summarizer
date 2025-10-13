package com.ribafish.slacksummarizer.services

import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import com.ribafish.slacksummarizer.config.GeminiConfig
import com.ribafish.slacksummarizer.models.SlackThread
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class GeminiService(private val config: GeminiConfig) {
    private val client = Client.builder()
        .apiKey(config.apiKey)
        .build()

    suspend fun summarize(thread: SlackThread): String {
        logger.debug { "Generating summary for thread with ${thread.messages.size} messages" }

        val threadContent = buildThreadContent(thread)
        val prompt = buildPrompt(threadContent)

        logger.debug { "Prompt: $prompt" }

        val generateConfig = GenerateContentConfig.builder().build()

        val response = client.models.generateContent(config.model, prompt, generateConfig)
        val summary = response.text() ?: "Failed to generate summary"

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
            2. Identify 3-7 keywords/tags that represent the main topics, technologies, or concepts discussed
            3. Focus entirely on the technical content, concepts, and information shared
            4. Organize information into logical sections with proper headers
            5. Extract and preserve:
               - Technical explanations and concepts
               - Code snippets and examples
               - Solutions to problems
               - Best practices and recommendations
               - Important links and references
            6. Remove conversational elements (greetings, acknowledgments, "thanks", etc.)
            7. Synthesize multiple related points into cohesive explanations
            8. Use proper markdown formatting (headers, lists, code blocks, etc.)
            9. Keep the tone professional and encyclopedic

            Do NOT include:
            - Who said what or when
            - Conversational back-and-forth
            - Off-topic discussion
            - Personal opinions unless they represent technical best practices

            Format the output as a complete markdown document with:
            - A descriptive title (# heading)
            - A keywords line IMMEDIATELY after the title in the format: **Keywords:** keyword1, keyword2, keyword3
            - An overview section explaining what this article covers
            - Logical subsections organizing the technical content
            - Code blocks for any code examples
            - Links to external resources if mentioned

            Example format:
            # How to Configure Redis for High Availability

            **Keywords:** redis, high-availability, clustering, replication, failover

            ## Overview
            [content here]

            Here is the conversation to transform into a knowledge base article:

            $threadContent

            Generate the knowledge base article:
        """.trimIndent()
    }
}
