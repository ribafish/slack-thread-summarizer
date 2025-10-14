# Slack Thread Summarizer

Serverless solution that summarizes Slack threads when marked with a 📌 (pushpin) reaction, creating a pull request with the summary to a knowledge base repository via GitHub Actions.

## Features

- No always-on server required (serverless via GitHub Actions)
- Triggered by Slack Workflow Builder on pushpin reactions
- Fetches entire thread context
- Summarizes conversation using Gemini, Claude, or Amazon Bedrock AI
- Creates formatted markdown documents
- Automatically opens PRs to your knowledge base repo
- Smart article merging (extends existing articles on same topic)
- Keyword extraction and management

## Architecture

1. User adds 📌 reaction to Slack message
2. Slack Workflow Builder detects reaction
3. Workflow triggers GitHub Actions via API
4. GitHub Actions runs a Python script
5. Processor creates PR with summary

## Prerequisites

- Slack workspace with Workflow Builder access
- AI API key (Google Gemini, Anthropic Claude, or AWS credentials for Bedrock)
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
- `AWS_ACCESS_KEY_ID` - your AWS access key (if using Bedrock)
- `AWS_SECRET_ACCESS_KEY` - your AWS secret key (if using Bedrock)
- `GITHUB_TOKEN` - automatically provided by GitHub Actions (no action needed)

Add these **Repository Variables**:
- `AI_PROVIDER` - which AI to use: `gemini` (default), `claude`, or `bedrock`
- `KB_REPO_OWNER` - owner of knowledge base repo (e.g., "yourusername")
- `KB_REPO_NAME` - name of knowledge base repo (e.g., "knowledge-base")
- `SLACK_WORKSPACE_NAME` - your Slack workspace name (e.g., "your-workspace")

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

1. Go to GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens
2. Generate new token with the following settings:
   - **Repository access**: Select "Only select repositories" and choose your knowledge base repo
   - **Repository permissions**:
     - Contents: Read and write
     - Pull requests: Read and write
     - Metadata: Read-only (automatically included)
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
- URL: `https://api.github.com/repos/{owner}/{repo}/actions/workflows/summarize-thread-python.yml/dispatches`
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

**For Amazon Bedrock:**
1. Create AWS IAM user with `bedrock:InvokeModel` permission
2. Request access to desired Bedrock models in AWS Console
3. Add `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` as secrets in GitHub
4. Optionally set `AWS_REGION` variable (defaults to `us-east-1`)

## Usage

1. In any Slack channel, react to a message with 📌 (`:pushpin:`)
2. Slack Workflow triggers GitHub Actions
3. Check the Actions tab in your GitHub repo to see progress
4. AI (Gemini, Claude, or Bedrock) generates a summary of the thread
5. A PR is created in your knowledge base repo with the markdown summary

## Project Structure

```
summarizer-python/
├── __init__.py
├── main.py              # Entry point
├── config.py            # Configuration management
├── models.py            # Data models
├── services/
│   ├── __init__.py
│   ├── slack_service.py      # Slack API
│   ├── gemini_service.py     # Gemini AI
│   ├── claude_service.py     # Claude AI
│   ├── bedrock_service.py    # Amazon Bedrock AI
│   └── github_service.py     # GitHub API
└── requirements.txt     # Python dependencies
```

## Local Testing

```bash
# Set environment variables
export SLACK_BOT_TOKEN="xoxb-..."
export SLACK_WORKSPACE_NAME="your-workspace-name"
export GEMINI_API_KEY="..."
export ANTHROPIC_API_KEY="..."
export GITHUB_TOKEN="..."
export KB_REPO_OWNER="..."
export KB_REPO_NAME="..."
export AI_PROVIDER="gemini"  # or "claude" or "bedrock"

# For Bedrock (optional):
export AWS_ACCESS_KEY_ID="..."
export AWS_SECRET_ACCESS_KEY="..."
export AWS_REGION="us-east-1"

# Install dependencies
pip install -r summarizer-python/requirements.txt

# Run with channel ID and message timestamp
python -m summarizer-python.main C01234ABCD 1234567890.123456
```

## Troubleshooting

### Workflow not triggering
- Verify Workflow is published in Slack
- Check webhook URL is correct
- Ensure GitHub token has Contents and Pull requests permissions
- Review Workflow execution history in Slack

### GitHub Actions failing
- Check Actions tab for error logs
- Verify all secrets are set correctly
- Ensure bot has access to Slack channels

### Gemini API errors
- Verify API key is.
- Check quota limits
- Review prompt size (very long threads may exceed limits)

### GitHub PR creation fails
- Verify token has Contents and Pull requests permissions
- Ensure the knowledge base repository exists
- Check token has access to the specific repository

## Switching AI Providers

The project supports Gemini, Claude, and Amazon Bedrock. By default, it uses Gemini.

To switch providers, see [SWITCHING_AI.md](SWITCHING_AI.md) for detailed instructions.

**Quick switch**: Set the `AI_PROVIDER` repository variable to `gemini`, `claude`, or `bedrock` in GitHub Settings.

## Cost

Cost depends on your chosen AI provider:
- GitHub Actions: 2,000 free minutes/month (each run ~1-2 minutes)
- Gemini: Free tier includes 60 requests/minute
- Claude: Pay-as-you-go (~$0.01-0.05 per summary)
- Bedrock: Pay-as-you-go (varies by model, similar to Claude)
- No server hosting costs

## License

MIT