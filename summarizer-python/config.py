"""Configuration management for the summarizer."""

import os
from dataclasses import dataclass
from typing import Optional


@dataclass
class AIConfig:
    """AI provider configuration."""
    provider: str


@dataclass
class SlackConfig:
    """Slack API configuration."""
    bot_token: str


@dataclass
class ClaudeConfig:
    """Claude API configuration."""
    api_key: str
    model: str = "claude-3-5-sonnet-20241022"


@dataclass
class GeminiConfig:
    """Gemini API configuration."""
    api_key: str
    model: str = "gemini-2.0-flash-exp"


@dataclass
class GitHubConfig:
    """GitHub API configuration."""
    token: str
    repo_owner: str
    repo_name: str
    branch_prefix: str = "kb/add-"


@dataclass
class AppConfig:
    """Application configuration."""
    ai: AIConfig
    slack: SlackConfig
    claude: ClaudeConfig
    gemini: GeminiConfig
    github: GitHubConfig

    @classmethod
    def load(cls) -> "AppConfig":
        """Load configuration from environment variables."""
        ai_provider = os.getenv("AI_PROVIDER", "gemini")

        return cls(
            ai=AIConfig(provider=ai_provider),
            slack=SlackConfig(
                bot_token=os.getenv("SLACK_BOT_TOKEN", "")
            ),
            claude=ClaudeConfig(
                api_key=os.getenv("ANTHROPIC_API_KEY", ""),
                model=os.getenv("CLAUDE_MODEL", "claude-3-5-sonnet-20241022")
            ),
            gemini=GeminiConfig(
                api_key=os.getenv("GEMINI_API_KEY", ""),
                model=os.getenv("GEMINI_MODEL", "gemini-2.0-flash-exp")
            ),
            github=GitHubConfig(
                token=os.getenv("GITHUB_TOKEN", ""),
                repo_owner=os.getenv("KB_REPO_OWNER", ""),
                repo_name=os.getenv("KB_REPO_NAME", ""),
                branch_prefix=os.getenv("GITHUB_BRANCH_PREFIX", "kb/add-")
            )
        )
