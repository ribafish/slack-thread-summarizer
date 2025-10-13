# Slack Thread Summarizer - Python Implementation

Python implementation of the Slack Thread Summarizer.

## Setup

```bash
# Install dependencies
pip install -r requirements.txt

# Set environment variables
export SLACK_BOT_TOKEN="xoxb-..."
export GEMINI_API_KEY="..."
export ANTHROPIC_API_KEY="..."
export GITHUB_TOKEN="..."
export KB_REPO_OWNER="yourusername"
export KB_REPO_NAME="knowledge-base"
export AI_PROVIDER="gemini"  # or "claude"
```

## Usage

```bash
python -m summarizer.main <channel_id> <message_ts>
```

## Structure

```
summarizer/
├── __init__.py
├── main.py              # Entry point
├── config.py            # Configuration management
├── models.py            # Data models
├── services/
│   ├── __init__.py
│   ├── slack_service.py      # Slack API
│   ├── gemini_service.py     # Gemini AI
│   ├── claude_service.py     # Claude AI
│   └── github_service.py     # GitHub API
└── requirements.txt     # Python dependencies
```
