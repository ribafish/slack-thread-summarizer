"""Main entry point for the Slack Thread Summarizer."""

import logging
import os
import sys

from .config import AppConfig
from .services.slack_service import SlackService
from .services.gemini_service import GeminiService
from .services.claude_service import ClaudeService
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

        # Fetch thread
        logger.info("Fetching thread...")
        thread = slack_service.fetch_thread(channel_id, message_ts)

        if not thread.messages:
            logger.error("No messages found in thread")
            sys.exit(1)

        logger.info(f"Fetched {len(thread.messages)} messages")

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
        else:
            logger.error(f"Unknown AI provider: {provider}. Use 'gemini' or 'claude'")
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
            workspace_name=config.slack.workspace_name
        )

        logger.info(f"✓ Pull request created: {pr_url}")
        print(f"PR_URL={pr_url}")

        # Write to GitHub step summary if available
        if os.getenv("GITHUB_STEP_SUMMARY"):
            with open(os.getenv("GITHUB_STEP_SUMMARY"), "a") as f:
                f.write(f"### Slack Thread Summarizer\n\n")
                f.write(f"Successfully created pull request: {pr_url}\n")

    except Exception as e:
        logger.error(f"Failed to process thread: {e}", exc_info=True)
        sys.exit(1)


if __name__ == "__main__":
    main()
