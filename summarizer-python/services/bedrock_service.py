"""Amazon Bedrock API integration service."""

import json
import logging

import boto3
from botocore.config import Config

from ..config import BedrockConfig
from ..models import SlackThread

logger = logging.getLogger(__name__)


class BedrockService:
    """Service for generating summaries using Amazon Bedrock."""

    def __init__(self, config: BedrockConfig):
        """Initialize Bedrock service with configuration."""
        self.config = config

        # Configure boto3 client
        bedrock_config = Config(
            region_name=config.region,
            retries={'max_attempts': 3, 'mode': 'adaptive'}
        )

        self.client = boto3.client('bedrock-runtime', config=bedrock_config)

    def summarize(self, thread: SlackThread) -> str:
        """Generate a summary of the thread using Amazon Bedrock.

        Args:
            thread: The Slack thread to summarize

        Returns:
            Markdown-formatted summary
        """
        logger.debug(f"Generating summary for thread with {len(thread.messages)} messages using Bedrock")

        thread_content = self._build_thread_content(thread)
        prompt = self._build_prompt(thread_content)

        logger.debug(f"Sending request to Bedrock API with model: {self.config.model}")

        try:
            # Build request body based on model type
            if self.config.model.startswith("anthropic.claude"):
                request_body = self._build_claude_request(prompt)
            elif self.config.model.startswith("amazon.titan"):
                request_body = self._build_titan_request(prompt)
            elif self.config.model.startswith("meta.llama"):
                request_body = self._build_llama_request(prompt)
            else:
                raise ValueError(f"Unsupported Bedrock model: {self.config.model}")

            response = self.client.invoke_model(
                modelId=self.config.model,
                body=json.dumps(request_body)
            )

            response_body = json.loads(response['body'].read())
            summary = self._extract_response_text(response_body)

            logger.debug(f"Generated summary: {len(summary)} characters")
            return summary

        except Exception as e:
            logger.error(f"Error calling Bedrock API: {e}")
            raise

    def _build_thread_content(self, thread: SlackThread) -> str:
        """Build thread content string from messages."""
        return "\n\n".join(message.text for message in thread.messages)

    def _build_prompt(self, thread_content: str) -> str:
        """Build the prompt for Bedrock."""
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

    def _build_claude_request(self, prompt: str) -> dict:
        """Build request body for Claude models."""
        return {
            "anthropic_version": "bedrock-2023-05-31",
            "max_tokens": 4096,
            "messages": [
                {
                    "role": "user",
                    "content": prompt
                }
            ]
        }

    def _build_titan_request(self, prompt: str) -> dict:
        """Build request body for Titan models."""
        return {
            "inputText": prompt,
            "textGenerationConfig": {
                "maxTokenCount": 4096,
                "temperature": 0.7,
                "topP": 0.9
            }
        }

    def _build_llama_request(self, prompt: str) -> dict:
        """Build request body for Llama models."""
        return {
            "prompt": prompt,
            "max_gen_len": 4096,
            "temperature": 0.7,
            "top_p": 0.9
        }

    def _extract_response_text(self, response_body: dict) -> str:
        """Extract text from response body based on model type."""
        if "content" in response_body:
            # Claude format
            content = response_body["content"]
            if isinstance(content, list) and len(content) > 0:
                return content[0].get("text", "Failed to generate summary")
            return "Failed to generate summary"
        elif "results" in response_body:
            # Titan format
            results = response_body["results"]
            if isinstance(results, list) and len(results) > 0:
                return results[0].get("outputText", "Failed to generate summary")
            return "Failed to generate summary"
        elif "generation" in response_body:
            # Llama format
            return response_body.get("generation", "Failed to generate summary")
        else:
            logger.warning(f"Unknown response format: {response_body.keys()}")
            return "Failed to generate summary"
