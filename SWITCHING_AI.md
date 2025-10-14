# Switching Between AI Providers

This project supports multiple AI providers for generating summaries: Google Gemini, Anthropic Claude, and Amazon Bedrock. You can easily switch between them.

## Current Provider

By default, the project uses **Gemini**.

## How to Switch

### Option 1: Environment Variable (Recommended for GitHub Actions)

Set the `AI_PROVIDER` variable in your GitHub repository:

1. Go to your repository → Settings → Secrets and variables → Actions → Variables tab
2. Click "New repository variable"
3. Name: `AI_PROVIDER`
4. Value: `gemini`, `claude`, or `bedrock`
5. Click "Add variable"

Also ensure you have the corresponding credentials:
- For Gemini: `GEMINI_API_KEY` secret
- For Claude: `ANTHROPIC_API_KEY` secret
- For Bedrock: Configure AWS credentials (see Bedrock section below)

### Option 2: Command Line Environment Variable

```bash
export AI_PROVIDER="claude"  # or "gemini" or "bedrock"
python -m summarizer.main <channel_id> <message_ts>
```

## Provider Comparison

### Gemini (Default)
- **Cost**: Free tier (60 requests/minute)
- **Model**: gemini-1.5-flash
- **Best for**: Cost-effective, high-volume usage
- **API Key**: Get from [Google AI Studio](https://makersuite.google.com/app/apikey)

### Claude
- **Cost**: Pay-as-you-go (~$0.01-0.05 per summary)
- **Model**: claude-3-5-sonnet-20241022
- **Best for**: Higher quality summaries, better context understanding
- **API Key**: Get from [Anthropic Console](https://console.anthropic.com/)

### Bedrock
- **Cost**: Pay-as-you-go (varies by model, similar to Claude)
- **Model**: anthropic.claude-3-5-sonnet-20241022-v2:0 (default)
- **Best for**: AWS-integrated environments, enterprise deployments
- **Setup**: Requires AWS credentials and Bedrock model access
- **Supported Models**:
  - Claude models: `anthropic.claude-*`
  - Titan models: `amazon.titan-*`
  - Llama models: `meta.llama-*`

## Changing the Model Version

You can also customize which model version to use by setting the following environment variables:
- `GEMINI_MODEL`
- `CLAUDE_MODEL`
- `BEDROCK_MODEL`
- `AWS_REGION` (for Bedrock, defaults to `us-east-1`)

For example:
```bash
export GEMINI_MODEL="gemini-1.5-pro"
export CLAUDE_MODEL="claude-3-opus-20240229"
export BEDROCK_MODEL="anthropic.claude-3-5-sonnet-20241022-v2:0"
export AWS_REGION="us-west-2"
```

## Configuring AWS Credentials for Bedrock

When using Bedrock, you need to configure AWS credentials. There are multiple ways to do this:

### Option 1: GitHub Actions (Recommended)

Use the AWS credentials GitHub Action:

```yaml
- name: Configure AWS Credentials
  uses: aws-actions/configure-aws-credentials@v4
  with:
    role-to-assume: arn:aws:iam::YOUR_ACCOUNT_ID:role/YOUR_ROLE_NAME
    aws-region: us-east-1
```

Or set secrets directly:
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_REGION` (optional, defaults to `us-east-1`)

### Option 2: Local Environment

```bash
export AWS_ACCESS_KEY_ID="your-access-key"
export AWS_SECRET_ACCESS_KEY="your-secret-key"
export AWS_REGION="us-east-1"
```

Or use AWS CLI profiles:
```bash
aws configure
```

### Option 3: IAM Role (for EC2/ECS/Lambda)

If running in AWS, use IAM roles attached to your compute resource.

## Troubleshooting

### "Unknown AI provider" error
- Check that `AI_PROVIDER` is set to exactly `gemini`, `claude`, or `bedrock` (case-insensitive)
- Verify the environment variable is being loaded

### Missing API key
- Ensure the corresponding secret is set in GitHub Actions
- For local testing, verify environment variables are exported

### Bedrock access denied
- Verify AWS credentials are properly configured
- Ensure you have requested and been granted access to the specific Bedrock model in the AWS Console
- Check that your IAM role/user has the `bedrock:InvokeModel` permission

### Different quality results
- Claude (direct API and Bedrock) typically produces more detailed summaries
- Gemini is faster and more cost-effective
- Try different providers to see which fits your needs better