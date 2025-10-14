"""AWS Lambda function to handle Slack reaction_added events and trigger GitHub Actions."""

import hashlib
import hmac
import json
import os
import time
from typing import Any, Dict
from urllib.request import Request, urlopen
from urllib.error import URLError


def verify_slack_signature(event: Dict[str, Any]) -> bool:
    """Verify that the request came from Slack using the signing secret."""
    slack_signing_secret = os.environ["SLACK_SIGNING_SECRET"]

    # Get headers
    headers = event.get("headers", {})
    timestamp = headers.get("x-slack-request-timestamp", "")
    signature = headers.get("x-slack-signature", "")

    # Prevent replay attacks
    if abs(time.time() - int(timestamp)) > 60 * 5:
        return False

    # Verify signature
    body = event.get("body", "")
    sig_basestring = f"v0:{timestamp}:{body}"

    my_signature = "v0=" + hmac.new(
        slack_signing_secret.encode(),
        sig_basestring.encode(),
        hashlib.sha256
    ).hexdigest()

    return hmac.compare_digest(my_signature, signature)


def trigger_github_workflow(channel_id: str, message_ts: str) -> Dict[str, Any]:
    """Trigger GitHub Actions workflow via API."""
    github_token = os.environ["GITHUB_TOKEN"]
    repo_owner = os.environ["GITHUB_REPO_OWNER"]
    repo_name = os.environ["GITHUB_REPO_NAME"]

    url = f"https://api.github.com/repos/{repo_owner}/{repo_name}/actions/workflows/summarize-thread-python.yml/dispatches"

    payload = {
        "ref": "main",
        "inputs": {
            "channel_id": channel_id,
            "message_ts": message_ts
        }
    }

    headers = {
        "Authorization": f"Bearer {github_token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json"
    }

    req = Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST"
    )

    try:
        with urlopen(req) as response:
            return {
                "status_code": response.status,
                "success": True
            }
    except URLError as e:
        print(f"Error triggering GitHub workflow: {e}")
        return {
            "status_code": 500,
            "success": False,
            "error": str(e)
        }


def lambda_handler(event: Dict[str, Any], context: Any) -> Dict[str, Any]:
    """Handle Slack event subscription requests.

    Args:
        event: Lambda event containing Slack request
        context: Lambda context

    Returns:
        Response dict with statusCode and body
    """
    print(f"Received event: {json.dumps(event)}")

    # Verify Slack signature
    if not verify_slack_signature(event):
        print("Invalid Slack signature")
        return {
            "statusCode": 401,
            "body": json.dumps({"error": "Invalid signature"})
        }

    # Parse body
    body = json.loads(event.get("body", "{}"))

    # Handle URL verification challenge
    if body.get("type") == "url_verification":
        print("Responding to URL verification challenge")
        return {
            "statusCode": 200,
            "body": json.dumps({"challenge": body.get("challenge")})
        }

    # Handle event callback
    if body.get("type") == "event_callback":
        event_data = body.get("event", {})

        # Check if this is a reaction_added event with pushpin emoji
        if event_data.get("type") == "reaction_added" and event_data.get("reaction") == "pushpin":
            channel_id = event_data.get("item", {}).get("channel")
            message_ts = event_data.get("item", {}).get("ts")

            if channel_id and message_ts:
                print(f"Triggering workflow for channel={channel_id}, ts={message_ts}")

                # Trigger GitHub Actions workflow
                result = trigger_github_workflow(channel_id, message_ts)

                if result["success"]:
                    print(f"Successfully triggered GitHub workflow")
                else:
                    print(f"Failed to trigger workflow: {result.get('error')}")

                # Always respond with 200 to Slack to acknowledge receipt
                return {
                    "statusCode": 200,
                    "body": json.dumps({"ok": True})
                }

    # Default response for unhandled events
    return {
        "statusCode": 200,
        "body": json.dumps({"ok": True})
    }
