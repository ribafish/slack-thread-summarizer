"""AWS Lambda function to handle Slack message shortcuts and trigger GitHub Actions."""

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

    # Get headers (Lambda Function URLs provide lowercase headers)
    headers = event.get("headers", {})
    timestamp = headers.get("x-slack-request-timestamp", "")
    signature = headers.get("x-slack-signature", "")

    if not timestamp or not signature:
        print(f"Missing timestamp or signature. Headers: {headers}")
        return False

    # Prevent replay attacks
    try:
        if abs(time.time() - int(timestamp)) > 60 * 5:
            print(f"Timestamp too old: {timestamp}")
            return False
    except ValueError:
        print(f"Invalid timestamp format: {timestamp}")
        return False

    # Verify signature - need the raw body before base64 decoding
    body = event.get("body", "")

    # If body is base64 encoded, decode it for signature verification
    if event.get("isBase64Encoded", False):
        import base64
        body = base64.b64decode(body).decode("utf-8")
    sig_basestring = f"v0:{timestamp}:{body}"

    my_signature = "v0=" + hmac.new(
        slack_signing_secret.encode(),
        sig_basestring.encode(),
        hashlib.sha256
    ).hexdigest()

    is_valid = hmac.compare_digest(my_signature, signature)
    if not is_valid:
        print(f"Signature mismatch. Expected: {my_signature}, Got: {signature}")

    return is_valid


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
    """Handle Slack message shortcut requests.

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

    # Parse body - shortcuts come as URL-encoded form data
    body_str = event.get("body", "")

    # Decode base64 if needed
    if event.get("isBase64Encoded", False):
        import base64
        body_str = base64.b64decode(body_str).decode("utf-8")

    # Parse URL-encoded payload
    try:
        # Extract payload from form data
        import urllib.parse
        parsed = urllib.parse.parse_qs(body_str)

        # Payload is in the 'payload' field
        if "payload" in parsed:
            payload = json.loads(parsed["payload"][0])
        else:
            # Try parsing as JSON directly (for testing)
            payload = json.loads(body_str)
    except (json.JSONDecodeError, KeyError, IndexError) as e:
        print(f"Error parsing request body: {e}")
        return {
            "statusCode": 400,
            "body": json.dumps({"error": "Invalid request format"})
        }

    print(f"Parsed payload: {json.dumps(payload)}")

    # Handle message shortcut
    if payload.get("type") == "message_action":
        callback_id = payload.get("callback_id")

        # Check if this is our "Summarize Thread" shortcut
        if callback_id == "summarize_thread":
            message = payload.get("message", {})
            channel_id = payload.get("channel", {}).get("id")
            message_ts = message.get("ts")

            if channel_id and message_ts:
                print(f"Triggering workflow for channel={channel_id}, ts={message_ts}")

                # Trigger GitHub Actions workflow
                result = trigger_github_workflow(channel_id, message_ts)

                if result["success"]:
                    print(f"Successfully triggered GitHub workflow")
                else:
                    print(f"Failed to trigger workflow: {result.get('error')}")

                # Respond to Slack to acknowledge receipt
                return {
                    "statusCode": 200,
                    "body": ""  # Shortcuts require empty body response
                }

    # Default response for unhandled requests
    return {
        "statusCode": 200,
        "body": ""
    }
