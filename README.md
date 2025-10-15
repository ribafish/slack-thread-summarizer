# Slack Thread Summarizer

Serverless solution that summarizes Slack threads using a message shortcut, creating a pull request with the summary to a knowledge base repository via GitHub Actions.

## Features

- No always-on server required (serverless via GitHub Actions and Lambda)
- Triggered by Slack message shortcut on any message
- Fetches entire thread context
- Summarizes conversation using Gemini, Claude, or Amazon Bedrock AI
- Creates formatted markdown documents
- Automatically opens PRs to your knowledge base repo
- Smart article merging (extends existing articles on same topic)
- Keyword extraction and management

## Architecture

1. User clicks "Summarize Thread" message shortcut on any Slack message
2. Slack sends shortcut payload to AWS Lambda Function URL
3. Lambda function validates request and triggers GitHub Actions workflow
4. GitHub Actions runs Python script to fetch thread and generate summary
5. Processor creates PR with summary in knowledge base repository

## Prerequisites

- Slack workspace with admin access to create Slack apps
- AWS account for Lambda function deployment
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
- `KB_GITHUB_TOKEN` - your GitHub Personal Access Token (PAT) with access to the knowledge base repo

Add these **Repository Variables**:
- `AI_PROVIDER` - which AI to use: `gemini` (default), `claude`, or `bedrock`
- `KB_REPO_OWNER` - owner of knowledge base repo (e.g., "yourusername")
- `KB_REPO_NAME` - name of knowledge base repo (e.g., "knowledge-base")
- `SLACK_WORKSPACE_NAME` - your Slack workspace name (e.g., "your-workspace")

### 3. Deploy AWS Lambda Function

See the [Lambda Deployment Guide](#lambda-deployment) section below for detailed instructions on creating and deploying the Lambda function that will receive Slack events.

### 4. Slack App Configuration

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

**Configure Message Shortcut:**
1. Navigate to "Interactivity & Shortcuts" in your Slack app settings
2. Enable Interactivity
3. Set Request URL to your Lambda Function URL (you'll get this after deploying the Lambda)
4. Click "Create New Shortcut"
5. Choose "On messages"
6. Configure the shortcut:
   - Name: `Summarize Thread`
   - Short Description: `Generate a summary of this thread`
   - Callback ID: `summarize_thread`
7. Click "Create"
8. Save Changes

**Install App:**
- Click "Install to Workspace"
- Authorize the app
- Copy the "Bot User OAuth Token" (starts with `xoxb-`)
- Copy the "Signing Secret" from Basic Information → App Credentials

### 5. Create Knowledge Base Repository

Create a repository where summaries will be stored (or use an existing one):
1. Create a new GitHub repository (e.g., "knowledge-base")
2. Optionally, create a `summaries/` directory in it
3. Ensure the GitHub token (next step) has write access to this repo

### 6. Create GitHub Personal Access Token

1. Go to GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens
2. Generate new token with the following settings:
   - **Repository access**: Select "Only select repositories" and choose your knowledge base repo
   - **Repository permissions**:
     - Contents: Read and write
     - Pull requests: Read and write
     - Metadata: Read-only (automatically included)
3. Copy the token and add it as `KB_GITHUB_TOKEN` secret in your repository

**Note**: The default `GITHUB_TOKEN` provided by GitHub Actions cannot be used because it doesn't have permission to create pull requests in other repositories. You must use a Personal Access Token (PAT).

### 7. Invite Slack Bot to Private Channels (Optional)

For **public channels**, the bot will auto-join when needed (with `channels:join` scope).

For **private channels**, manually invite the bot:
1. Go to the private channel in Slack
2. Type `/invite @Thread Summarizer` (or your bot name)

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

## Lambda Deployment

### Option 1: AWS Console (Recommended for Beginners)

1. **Create Secrets in AWS Secrets Manager:**
   - Go to AWS Secrets Manager Console
   - Click "Store a new secret"
   - Choose "Other type of secret"
   - Create two secrets:

     **Secret 1: Slack Signing Secret**
     - Secret name: `lambda/slack-thread-summarizer-webhook/slack_signing_secret`
     - Secret value: Plaintext, paste your Slack signing secret from Slack App → Basic Information → App Credentials

     **Secret 2: GitHub Token**
     - Secret name: `lambda/slack-thread-summarizer-webhook/github_token`
     - Secret value: Plaintext, paste your GitHub Personal Access Token with `actions:write` permission

2. **Create Lambda Function:**
   - Go to AWS Lambda Console
   - Click "Create function"
   - Choose "Author from scratch"
   - Function name: `slack-thread-summarizer-webhook`
   - Runtime: Python 3.12
   - Architecture: x86_64
   - Click "Create function"

3. **Upload Function Code:**
   - Copy the code from `lambda/slack_event_handler.py`
   - In the Lambda console, paste it into the code editor
   - Click "Deploy"

4. **Add boto3 Layer (if needed):**
   - boto3 is pre-installed in Lambda Python runtime, but if you need a specific version:
   - Go to Layers → Add a layer → AWS layers → AWSSDKPandas-Python312

5. **Configure Environment Variables:**
   Add the following environment variables in Configuration → Environment variables:
   - `SLACK_SHORTCUT_CALLBACK_ID` - the callback ID from your shortcut (default: `summarize_thread`)
   - `GITHUB_REPO_OWNER` - your GitHub username
   - `GITHUB_REPO_NAME` - this repository name (e.g., `slack-thread-summarizer`)
   - `GITHUB_REPO_WORKFLOW` - workflow filename (default: `summarize-thread-python.yml`)

6. **Configure IAM Permissions:**
   - Go to Configuration → Permissions
   - Click on the execution role name
   - Click "Add permissions" → "Attach policies"
   - Create a new inline policy with this JSON:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": [
           "secretsmanager:GetSecretValue"
         ],
         "Resource": [
           "arn:aws:secretsmanager:*:*:secret:lambda/slack-thread-summarizer-webhook/*"
         ]
       }
     ]
   }
   ```

7. **Create Function URL:**
   - Go to Configuration → Function URL
   - Click "Create function URL"
   - Auth type: NONE (Slack handles authentication via signature verification)
   - Click "Save"
   - Copy the Function URL - you'll need this for Slack Event Subscriptions

8. **Configure Timeout:**
   - Go to Configuration → General configuration
   - Edit timeout to 10 seconds (default 3s may be too short)

### Option 2: AWS CLI Deployment

1. **Create secrets in AWS Secrets Manager:**
   ```bash
   # Create Slack signing secret
   aws secretsmanager create-secret \
     --name lambda/slack-thread-summarizer-webhook/slack_signing_secret \
     --secret-string "your_slack_signing_secret_here"

   # Create GitHub Token
   aws secretsmanager create-secret \
     --name lambda/slack-thread-summarizer-webhook/github_token \
     --secret-string "your_github_token_here"
   ```

2. **Package the function:**
   ```bash
   cd lambda
   zip function.zip slack_event_handler.py
   ```

3. **Create IAM role for Lambda:**
   ```bash
   aws iam create-role \
     --role-name lambda-slack-webhook-role \
     --assume-role-policy-document '{
       "Version": "2012-10-17",
       "Statement": [{
         "Effect": "Allow",
         "Principal": {"Service": "lambda.amazonaws.com"},
         "Action": "sts:AssumeRole"
       }]
     }'

   aws iam attach-role-policy \
     --role-name lambda-slack-webhook-role \
     --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

   # Add Secrets Manager permissions
   aws iam put-role-policy \
     --role-name lambda-slack-webhook-role \
     --policy-name SecretsManagerAccess \
     --policy-document '{
       "Version": "2012-10-17",
       "Statement": [{
         "Effect": "Allow",
         "Action": ["secretsmanager:GetSecretValue"],
         "Resource": ["arn:aws:secretsmanager:*:*:secret:lambda/slack-thread-summarizer-webhook/*"]
       }]
     }'
   ```

4. **Create the Lambda function:**
   ```bash
   aws lambda create-function \
     --function-name slack-thread-summarizer-webhook \
     --runtime python3.12 \
     --role arn:aws:iam::YOUR_ACCOUNT_ID:role/lambda-slack-webhook-role \
     --handler slack_event_handler.lambda_handler \
     --zip-file fileb://function.zip \
     --timeout 10
   ```

5. **Set environment variables:**
   ```bash
   aws lambda update-function-configuration \
     --function-name slack-thread-summarizer-webhook \
     --environment Variables="{
       SLACK_SHORTCUT_CALLBACK_ID=summarize_thread,
       GITHUB_REPO_OWNER=your_github_username,
       GITHUB_REPO_NAME=slack-thread-summarizer,
       GITHUB_REPO_WORKFLOW=summarize-thread-python.yml
     }"
   ```

6. **Create Function URL:**
   ```bash
   aws lambda create-function-url-config \
     --function-name slack-thread-summarizer-webhook \
     --auth-type NONE
   ```

### Testing the Lambda Function

You can test the function using the AWS Lambda console:

1. Go to your function → Test tab
2. Create a new test event with this JSON (simulating a Slack shortcut):
   ```json
   {
     "headers": {
       "x-slack-request-timestamp": "1234567890",
       "x-slack-signature": "v0=test"
     },
     "body": "payload=%7B%22type%22%3A%22message_action%22%2C%22callback_id%22%3A%22summarize_thread%22%2C%22channel%22%3A%7B%22id%22%3A%22C01234567%22%7D%2C%22message%22%3A%7B%22ts%22%3A%221234567890.123456%22%7D%7D"
   }
   ```
3. Note: This test will fail signature verification, but you can verify the function deploys correctly

## Usage

1. In any Slack channel, hover over a message and click the "More actions" menu (⋮)
2. Select "Summarize Thread" from the shortcuts menu
3. You'll receive an ephemeral confirmation message with a link to the original message
4. Lambda triggers GitHub Actions in the background
5. Check the Actions tab in your GitHub repo to see progress
6. AI (Gemini, Claude, or Bedrock) generates a summary of the thread
7. A PR is created in your knowledge base repo with the markdown summary
8. The ephemeral message updates with the PR link when complete

## Project Structure

```
.
├── lambda/
│   └── slack_event_handler.py    # AWS Lambda function for Slack events
├── summarizer-python/
│   ├── __init__.py
│   ├── main.py                    # Entry point
│   ├── config.py                  # Configuration management
│   ├── models.py                  # Data models
│   ├── services/
│   │   ├── __init__.py
│   │   ├── slack_service.py      # Slack API
│   │   ├── gemini_service.py     # Gemini AI
│   │   ├── claude_service.py     # Claude AI
│   │   ├── bedrock_service.py    # Amazon Bedrock AI
│   │   └── github_service.py     # GitHub API
│   └── requirements.txt          # Python dependencies
└── .github/workflows/
    └── summarize-thread-python.yml
```

## Local Testing

```bash
# Set environment variables
export SLACK_BOT_TOKEN="xoxb-..."
export SLACK_WORKSPACE_NAME="your-workspace-name"
export GEMINI_API_KEY="..."
export ANTHROPIC_API_KEY="..."
export KB_GITHUB_TOKEN="..."
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

### Lambda function not receiving events
- Check CloudWatch Logs for the Lambda function
- Verify Slack Event Subscriptions Request URL matches Lambda Function URL
- Ensure Lambda Function URL auth type is set to NONE
- Check Lambda environment variables are set correctly
- Verify Lambda has IAM permissions to access Secrets Manager

### Secrets Manager errors
- Ensure secrets are created with exact names:
  - `lambda/slack-thread-summarizer-webhook/slack_signing_secret`
  - `lambda/slack-thread-summarizer-webhook/github_token`
- Verify Lambda execution role has `secretsmanager:GetSecretValue` permission
- Check CloudWatch Logs for specific error messages

### Slack signature verification failing
- Verify the Slack signing secret in Secrets Manager is correct
- Check it matches the value in Slack App → Basic Information → App Credentials → Signing Secret
- Ensure there are no extra spaces or newlines in the secret value

### GitHub Actions not triggering from Lambda
- Check Lambda CloudWatch logs for API errors
- Verify GitHub token in Secrets Manager (`lambda/slack-thread-summarizer-webhook/github_token`) has `actions:write` scope
- Ensure `GITHUB_REPO_OWNER`, `GITHUB_REPO_NAME`, and `GITHUB_REPO_WORKFLOW` environment variables are correct
- Check GitHub Actions workflow file exists (default: `.github/workflows/summarize-thread-python.yml`)

### GitHub Actions failing
- Check Actions tab for error logs
- Verify all secrets are set correctly
- Ensure bot has access to Slack channels

### Gemini API errors
- Verify API key is.
- Check quota limits
- Review prompt size (very long threads may exceed limits)

### GitHub PR creation fails
- Verify `KB_GITHUB_TOKEN` has Contents and Pull requests permissions
- Ensure the knowledge base repository exists
- Check token has access to the specific repository
- Confirm you're using `KB_GITHUB_TOKEN` (PAT) and not the default `GITHUB_TOKEN`

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