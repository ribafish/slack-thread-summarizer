"""Data models for Slack thread summarizer."""

from dataclasses import dataclass
from typing import List, Optional


@dataclass
class SlackMessage:
    """Represents a single Slack message."""
    user: str
    username: str
    text: str
    timestamp: str


@dataclass
class SlackThread:
    """Represents a Slack thread with metadata."""
    channel_id: str
    channel_name: str
    thread_ts: str
    messages: List[SlackMessage]
    workspace_id: Optional[str] = None
