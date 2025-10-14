"""Slack API integration service."""

import logging
from typing import List

from slack_sdk import WebClient
from slack_sdk.errors import SlackApiError

from ..config import SlackConfig
from ..models import SlackMessage, SlackThread

logger = logging.getLogger(__name__)


class SlackService:
    """Service for interacting with Slack API."""

    def __init__(self, config: SlackConfig):
        """Initialize Slack service with configuration."""
        self.config = config
        self.client = WebClient(token=config.bot_token)

    def post_message(self, channel_id: str, text: str, thread_ts: str = None) -> None:
        """Post a message to a Slack channel or thread.

        Args:
            channel_id: The channel ID
            text: The message text
            thread_ts: Optional thread timestamp to reply in thread
        """
        try:
            kwargs = {
                "channel": channel_id,
                "text": text
            }
            if thread_ts:
                kwargs["thread_ts"] = thread_ts

            response = self.client.chat_postMessage(**kwargs)
            if response["ok"]:
                logger.debug(f"Posted message to channel {channel_id}")
            else:
                logger.error(f"Failed to post message: {response.get('error')}")
        except SlackApiError as e:
            logger.error(f"Failed to post message: {e.response['error']}")

    def fetch_thread(self, channel_id: str, thread_ts: str) -> SlackThread:
        """Fetch a thread from Slack.

        Args:
            channel_id: The channel ID
            thread_ts: The thread timestamp

        Returns:
            SlackThread containing all messages
        """
        logger.debug(f"Fetching thread {thread_ts} from channel {channel_id}")

        # Try to join the channel if it's public
        try:
            join_response = self.client.conversations_join(channel=channel_id)
            if join_response["ok"]:
                logger.debug(f"Joined channel {channel_id}")
        except SlackApiError as e:
            if e.response["error"] != "already_in_channel":
                logger.debug(f"Could not join channel {channel_id}: {e.response['error']}")

        # Get workspace/team info
        workspace_id = None
        try:
            team_info = self.client.team_info()
            workspace_id = team_info["team"]["id"]
        except Exception as e:
            logger.debug(f"Could not get team info: {e}")

        # Get channel info
        channel_name = channel_id
        try:
            channel_info = self.client.conversations_info(channel=channel_id)
            if channel_info["ok"]:
                channel_name = channel_info["channel"]["name"]
        except SlackApiError as e:
            logger.warning(f"Failed to get channel info: {e.response['error']}")

        # Get thread messages
        try:
            replies_response = self.client.conversations_replies(
                channel=channel_id,
                ts=thread_ts
            )

            if not replies_response["ok"]:
                logger.error(f"Failed to fetch thread: {replies_response.get('error', 'unknown error')}")
                return SlackThread(
                    channel_id=channel_id,
                    channel_name=channel_name,
                    thread_ts=thread_ts,
                    messages=[],
                    workspace_id=workspace_id
                )

            messages = []
            for message in replies_response["messages"]:
                messages.append(SlackMessage(
                    user=message.get("user", "unknown"),
                    username="",  # Not needed for knowledge base
                    text=message.get("text", ""),
                    timestamp=message["ts"]
                ))

            logger.debug(f"Fetched {len(messages)} messages from thread")

            return SlackThread(
                channel_id=channel_id,
                channel_name=channel_name,
                thread_ts=thread_ts,
                messages=messages,
                workspace_id=workspace_id
            )

        except SlackApiError as e:
            logger.error(f"Failed to fetch thread: {e.response['error']}")
            return SlackThread(
                channel_id=channel_id,
                channel_name=channel_name,
                thread_ts=thread_ts,
                messages=[],
                workspace_id=workspace_id
            )
