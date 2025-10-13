# Switching Between AI Providers

This project supports both Google Gemini and Anthropic Claude for generating summaries. You can easily switch between them.

## Current Provider

By default, the project uses **Gemini**.

## How to Switch

### Option 1: Environment Variable (Recommended for GitHub Actions)

Set the `AI_PROVIDER` variable in your GitHub repository:

1. Go to your repository → Settings → Secrets and variables → Actions → Variables tab
2. Click "New repository variable"
3. Name: `AI_PROVIDER`
4. Value: `gemini` or `claude`
5. Click "Add variable"

Also ensure you have the corresponding API key secret:
- For Gemini: `GEMINI_API_KEY`
- For Claude: `ANTHROPIC_API_KEY`

### Option 2: Command Line Environment Variable

```bash
export AI_PROVIDER="claude"  # or "gemini"
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

## Changing the Model Version

You can also customize which model version to use by setting the following environment variables:
- `GEMINI_MODEL`
- `CLAUDE_MODEL`

For example:
```bash
export GEMINI_MODEL="gemini-1.5-pro"
export CLAUDE_MODEL="claude-3-opus-20240229"
```

## Troubleshooting

### "Unknown AI provider" error
- Check that `AI_PROVIDER` is set to exactly `gemini` or `claude` (case-insensitive)
- Verify the environment variable is being loaded

### Missing API key
- Ensure the corresponding secret is set in GitHub Actions
- For local testing, verify environment variables are exported

### Different quality results
- Claude typically produces more detailed summaries
- Gemini is faster and more cost-effective
- Try both to see which fits your needs better