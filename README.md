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
- AI API key (Google Gemini or Anthropic Claude)
- GitHub personal access token
- Knowledge base repository (can be this repo or separate)

## Setup

### 1. Fork or Clone This Repository

This repository contains the processing code that will run in GitHub Actions.

### 2. Configure GitHub Secrets

Go to your repository → Settings → Secrets and variables → Actions

Add these **Repository Secrets**:
- `SLACK_BOT_TOKEN` - your Slack bot token (xoxb-...)
- `GEMINI_API_KEY` - your Google Gemini API key (if using Gemini)
- `ANTHROPIC_API_KEY` - your Anthropic API key (if using Claude)
- `GITHUB_TOKEN` - automatically provided by GitHub Actions (no action needed)

Add these **Repository Variables**:
- `AI_PROVIDER` - which AI to use: `gemini` (default) or `claude`
- `KB_REPO_OWNER` - owner of knowledge base repo (e.g., "yourusername")
- `KB_REPO_NAME` - name of knowledge base repo (e.g., "knowledge-base")

### 3. Slack App Configuration

**Create Slack App:**
1. Go to https://api.slack.com/apps
2. Click "Create New App" → "From scratch"
3. Name it "Thread Summarizer"
4. Select your workspace

**Configure OAuth Scopes:**
Navigate to "OAuth & Permissions" and add these Bot Token Scopes:
- `channels:history`
- `channels:join` - allows bot to auto-join public channels
- `channels:read`
- `groups:history`
- `im:history`
- `mpim:history`

**Install App:**
- Click "Install to Workspace"
- Authorize the app
- Copy the "Bot User OAuth Token" (starts with `xoxb-`)

### 4. Create Knowledge Base Repository

Create a repository where summaries will be stored (or use an existing one):
1. Create a new GitHub repository (e.g., "knowledge-base")
2. Optionally, create a `summaries/` directory in it
3. Ensure the GitHub token (next step) has write access to this repo

### 5. Create GitHub Personal Access Token

1. Go to GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token with `repo` scope (full repository access)
3. Copy the token

### 6. Invite Slack Bot to Private Channels (Optional)

For **public channels**, the bot will auto-join when needed (with `channels:join` scope).

For **private channels**, manually invite the bot:
1. Go to the private channel in Slack
2. Type `/invite @Thread Summarizer` (or your bot name)

### 7. Set Up Slack Workflow

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

### 8. Get AI API Key

**For Gemini (default):**
1. Go to https://makersuite.google.com/app/apikey
2. Create a new API key
3. Add it as `GEMINI_API_KEY` secret in GitHub

**For Claude:**
1. Go to https://console.anthropic.com/
2. Create an API key
3. Add it as `ANTHROPIC_API_KEY` secret in GitHub

## Usage

1. In any Slack channel, react to a message with 📌 (`:pushpin:`)
2. Slack Workflow triggers GitHub Actions
3. Check the Actions tab in your GitHub repo to see progress
4. AI (Gemini or Claude) generates a summary of the thread
5. A PR is created in your knowledge base repo with the markdown summary

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
│       ├── GeminiService.kt     # AI summarization (Gemini)
│       ├── ClaudeService.kt     # AI summarization (Claude)
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
export KB_REPO_OWNER="..."
export KB_REPO_NAME="..."

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

## Switching AI Providers

The project supports both Gemini and Claude. By default, it uses Gemini.

To switch providers, see [SWITCHING_AI.md](SWITCHING_AI.md) for detailed instructions.

**Quick switch**: Set the `AI_PROVIDER` repository variable to `gemini` or `claude` in GitHub Settings.

## Cost

Cost depends on your chosen AI provider:
- GitHub Actions: 2,000 free minutes/month (each run ~1-2 minutes)
- Gemini: Free tier includes 60 requests/minute
- Claude: Pay-as-you-go (~$0.01-0.05 per summary)
- No server hosting costs

## License

MIT
