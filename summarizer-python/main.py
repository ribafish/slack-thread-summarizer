"""Main entry point for the Slack Thread Summarizer."""

import logging
import os
import sys

from .config import AppConfig
from .services.slack_service import SlackService
from .services.gemini_service import GeminiService
from .services.claude_service import ClaudeService
from .services.bedrock_service import BedrockService
from .services.github_service import GitHubService

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def main():
    """Main function."""
    logger.info("Starting Slack Thread Summarizer...")

    if len(sys.argv) < 3:
        logger.error("Usage: <channel_id> <message_ts>")
        sys.exit(1)

    channel_id = sys.argv[1]
    message_ts = sys.argv[2]

    logger.info(f"Processing thread: channel={channel_id}, message={message_ts}")

    try:
        config = AppConfig.load()

        slack_service = SlackService(config.slack)
        github_service = GitHubService(config.github)
        response_url = os.getenv("SLACK_RESPONSE_URL")

        # Fetch thread
        logger.info("Fetching thread...")
        thread = slack_service.fetch_thread(channel_id, message_ts)

        if not thread.messages:
            logger.error("No messages found in thread")
            if response_url:
                workspace_name = config.slack.workspace_name
                message_id = message_ts.replace(".", "")
                message_link = f"https://{workspace_name}.slack.com/archives/{channel_id}/p{message_id}" if workspace_name else None
                slack_service.update_ephemeral_message(
                    response_url=response_url,
                    text=":x: Failed to fetch thread: No messages found",
                    message_link=message_link
                )
            sys.exit(1)

        logger.info(f"Fetched {len(thread.messages)} messages")
        message_count = len(thread.messages)

        # Get the last message timestamp for deeplink
        last_message_ts = thread.messages[-1].timestamp if thread.messages else message_ts

        # Summarize using configured provider
        provider = config.ai.provider.lower()
        if provider == "claude":
            logger.info("Generating summary with Claude...")
            claude_service = ClaudeService(config.claude)
            summary = claude_service.summarize(thread)
        elif provider == "gemini":
            logger.info("Generating summary with Gemini...")
            gemini_service = GeminiService(config.gemini)
            summary = gemini_service.summarize(thread)
        elif provider == "bedrock":
            logger.info("Generating summary with Amazon Bedrock...")
            bedrock_service = BedrockService(config.bedrock)
            summary = bedrock_service.summarize(thread)
        else:
            logger.error(f"Unknown AI provider: {provider}. Use 'gemini', 'claude', or 'bedrock'")
            sys.exit(1)

        logger.info(f"Summary generated: {len(summary)} characters")

        # Create PR
        logger.info("Creating pull request...")
        pr_url = github_service.create_pull_request(
            summary=summary,
            channel_id=channel_id,
            channel_name=thread.channel_name,
            timestamp=message_ts,
            workspace_id=thread.workspace_id,
            workspace_name=config.slack.workspace_name,
            last_message_ts=last_message_ts
        )

        logger.info(f"✓ Pull request created: {pr_url}")
        print(f"PR_URL={pr_url}")

        # Update ephemeral message if response_url is available
        if response_url:
            logger.info("Updating ephemeral message...")
            # Build message link
            workspace_name = config.slack.workspace_name
            message_id = message_ts.replace(".", "")
            message_link = f"https://{workspace_name}.slack.com/archives/{channel_id}/p{message_id}" if workspace_name else None

            message_text = f":white_check_mark: Summary generated from {message_count} message{'s' if message_count != 1 else ''}! Pull request created: {pr_url}"
            slack_service.update_ephemeral_message(
                response_url=response_url,
                text=message_text,
                message_link=message_link
            )
        else:
            logger.warning("No response_url available, skipping ephemeral message update")

        # Write to GitHub step summary if available
        if os.getenv("GITHUB_STEP_SUMMARY"):
            with open(os.getenv("GITHUB_STEP_SUMMARY"), "a") as f:
                f.write(f"### Slack Thread Summarizer\n\n")
                f.write(f"Successfully created pull request: {pr_url}\n")

    except Exception as e:
        logger.error(f"Failed to process thread: {e}", exc_info=True)

        # Update ephemeral message with error if response_url is available
        response_url = os.getenv("SLACK_RESPONSE_URL")
        if response_url:
            try:
                config = AppConfig.load()
                slack_service = SlackService(config.slack)
                workspace_name = config.slack.workspace_name
                message_id = message_ts.replace(".", "")
                message_link = f"https://{workspace_name}.slack.com/archives/{channel_id}/p{message_id}" if workspace_name else None

                error_message = str(e)
                slack_service.update_ephemeral_message(
                    response_url=response_url,
                    text=f":x: Failed to process thread: {error_message}",
                    message_link=message_link
                )
            except Exception as update_error:
                logger.error(f"Failed to update ephemeral message with error: {update_error}")

        sys.exit(1)


if __name__ == "__main__":
    main()
