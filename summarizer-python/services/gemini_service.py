"""Google Gemini API integration service."""

import logging

import google.generativeai as genai

from ..config import GeminiConfig
from ..models import SlackThread

logger = logging.getLogger(__name__)


class GeminiService:
    """Service for generating summaries using Google Gemini."""

    def __init__(self, config: GeminiConfig):
        """Initialize Gemini service with configuration."""
        self.config = config
        genai.configure(api_key=config.api_key)
        self.model = genai.GenerativeModel(config.model)

    def summarize(self, thread: SlackThread) -> str:
        """Generate a summary of the thread using Gemini.

        Args:
            thread: The Slack thread to summarize

        Returns:
            Markdown-formatted summary
        """
        logger.debug(f"Generating summary for thread with {len(thread.messages)} messages")

        thread_content = self._build_thread_content(thread)
        prompt = self._build_prompt(thread_content)

        logger.debug("Sending request to Gemini API")

        try:
            response = self.model.generate_content(prompt)
            summary = response.text

            logger.debug(f"Generated summary: {len(summary)} characters")
            return summary

        except Exception as e:
            logger.error(f"Error calling Gemini API: {e}")
            raise

    def _build_thread_content(self, thread: SlackThread) -> str:
        """Build thread content string from messages."""
        return "\n\n".join(message.text for message in thread.messages)

    def _build_prompt(self, thread_content: str) -> str:
        """Build the prompt for Gemini."""
        return f"""You are a technical documentation assistant. Your task is to transform a Slack conversation
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

{thread_content}

Generate the knowledge base article:"""
