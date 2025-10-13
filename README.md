# Slack Thread Summarizer

Serverless solution that summarizes Slack threads when marked with a 📌 (pushpin) reaction, creating a pull request with the summary to a knowledge base repository via GitHub Actions.

## Features

- No always-on server required (serverless via GitHub Actions)
- Triggered by Slack Workflow Builder on pushpin reactions
- Fetches entire thread context
- Summarizes conversation using Gemini AI
- Creates formatted markdown documents
- Automatically opens PRs to your knowledge base repo

## Architecture

1. User adds 📌 reaction to Slack message
2. Slack Workflow Builder detects reaction
3. Workflow triggers GitHub Actions via API
4. GitHub Actions runs Kotlin processor
5. Processor creates PR with summary

## Prerequisites

- Slack workspace with Workflow Builder access
- Google Gemini API key
- GitHub personal access token
- Knowledge base repository (can be this repo or separate)

## Setup

### 1. Fork or Clone This Repository

This repository contains the processing code that will run in GitHub Actions.

### 2. Configure GitHub Secrets

Go to your repository → Settings → Secrets and variables → Actions

Add these **Repository Secrets**:
- `SLACK_BOT_TOKEN` - your Slack bot token (xoxb-...)
- `GEMINI_API_KEY` - your Google Gemini API key
- `GITHUB_TOKEN` - automatically provided by GitHub Actions (no action needed)

Add these **Repository Variables**:
- `GITHUB_REPO_OWNER` - owner of knowledge base repo (e.g., "yourusername")
- `GITHUB_REPO_NAME` - name of knowledge base repo (e.g., "knowledge-base")

### 3. Slack App Configuration

**Create Slack App:**
1. Go to https://api.slack.com/apps
2. Click "Create New App" → "From scratch"
3. Name it "Thread Summarizer"
4. Select your workspace

**Configure OAuth Scopes:**
Navigate to "OAuth & Permissions" and add these Bot Token Scopes:
- `channels:history`
- `groups:history`
- `im:history`
- `mpim:history`
- `channels:read`
- `users:read`

**Install App:**
- Click "Install to Workspace"
- Authorize the app
- Copy the "Bot User OAuth Token" (starts with `xoxb-`)

### 4. Create GitHub Personal Access Token

1. Go to GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token with `repo` scope
3. Copy the token

### 5. Set Up Slack Workflow

**Create Workflow:**
1. In Slack, go to Tools → Workflow Builder
2. Create new workflow with trigger: "Reaction added"
3. Configure trigger to watch for `:pushpin:` emoji
4. Add step: "Send a webhook"

**Configure Webhook:**
- Method: `POST`
- URL: `https://api.github.com/repos/{owner}/{repo}/actions/workflows/summarize-thread.yml/dispatches`
  - Replace `{owner}` with your GitHub username
  - Replace `{repo}` with `slack-thread-summarizer`
- Headers:
  ```
  Authorization: Bearer YOUR_GITHUB_TOKEN
  Accept: application/vnd.github+json
  X-GitHub-Api-Version: 2022-11-28
  ```
- Body (JSON):
  ```json
  {
    "ref": "main",
    "inputs": {
      "channel_id": "{{channel_id}}",
      "message_ts": "{{message_ts}}",
      "user_id": "{{user_id}}"
    }
  }
  ```

**Variables from Slack:**
Slack Workflow Builder provides these automatically:
- `{{channel_id}}` - the channel ID
- `{{message_ts}}` - the message timestamp
- `{{user_id}}` - user who added the reaction

6. Publish the workflow

## Usage

1. In any Slack channel, react to a message with 📌 (`:pushpin:`)
2. Slack Workflow triggers GitHub Actions
3. Check the Actions tab in your GitHub repo to see progress
4. When complete, a PR will be created in your knowledge base repo

## Project Structure

```
slack-thread-summarizer/
├── .github/workflows/
│   └── summarize-thread.yml     # GitHub Actions workflow
├── src/main/kotlin/com/ribafish/slacksummarizer/
│   ├── Main.kt                  # CLI entry point
│   ├── config/
│   │   └── AppConfig.kt         # Configuration management
│   ├── models/
│   │   └── SlackThread.kt       # Data models
│   └── services/
│       ├── SlackService.kt      # Slack API interactions
│       ├── GeminiService.kt     # AI summarization
│       └── GitHubService.kt     # GitHub PR creation
└── src/main/resources/
    ├── application.conf.example # Configuration template
    └── logback.xml              # Logging configuration
```

## Local Testing

You can test the processor locally:

```bash
# Set environment variables
export SLACK_BOT_TOKEN="xoxb-..."
export GEMINI_API_KEY="..."
export GITHUB_TOKEN="..."
export GITHUB_REPO_OWNER="..."
export GITHUB_REPO_NAME="..."

# Build
./gradlew build

# Run with channel ID and message timestamp
./gradlew run --args="C01234ABCD 1234567890.123456"
```

## Troubleshooting

### Workflow not triggering
- Verify Workflow is published in Slack
- Check webhook URL is correct
- Ensure GitHub token has `repo` scope
- Review Workflow execution history in Slack

### GitHub Actions failing
- Check Actions tab for error logs
- Verify all secrets are set correctly
- Ensure bot has access to Slack channels

### Gemini API errors
- Verify API key is valid
- Check quota limits
- Review prompt size (very long threads may exceed limits)

### GitHub PR creation fails
- Verify token has `repo` scope
- Ensure the knowledge base repository exists
- Check bot has write access to the repository

## Cost

This solution is effectively free for most use cases:
- GitHub Actions: 2,000 free minutes/month
- Each run takes ~1-2 minutes
- Gemini: Free tier includes 60 requests/minute
- No server costs

## License

MIT
