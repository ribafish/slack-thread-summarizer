"""GitHub API integration service."""

import logging
import re
from typing import Optional

from github import Github, GithubException

from ..config import GitHubConfig

logger = logging.getLogger(__name__)


class GitHubService:
    """Service for creating pull requests on GitHub."""

    def __init__(self, config: GitHubConfig):
        """Initialize GitHub service with configuration."""
        self.config = config
        self.github = Github(config.token)

    def create_pull_request(
        self,
        summary: str,
        channel_id: str,
        channel_name: str,
        timestamp: str,
        workspace_id: Optional[str]
    ) -> str:
        """Create a pull request with the summary.

        Args:
            summary: The generated summary markdown
            channel_id: Slack channel ID
            channel_name: Slack channel name
            timestamp: Message timestamp
            workspace_id: Slack workspace ID

        Returns:
            URL of the created pull request
        """
        logger.info(f"Creating PR for summary from channel {channel_name}")

        repo = self.github.get_repo(f"{self.config.repo_owner}/{self.config.repo_name}")
        default_branch = repo.default_branch

        # Extract title from summary for filename and branch
        title = self._extract_title(summary)
        sanitized_title = self._sanitize_for_filename(title)

        # Search for existing file with similar topic
        existing_file_path = self._search_existing_article(repo, sanitized_title, default_branch)

        is_update = existing_file_path is not None
        file_path = existing_file_path if existing_file_path else f"knowledge-base/{sanitized_title}.md"

        # Create a unique branch name
        branch_name = f"{self.config.branch_prefix}{sanitized_title}-{timestamp.replace('.', '-')}"
        logger.debug(f"Branch name: {branch_name}")

        # Get the base branch reference
        base_ref = repo.get_git_ref(f"heads/{default_branch}")
        base_sha = base_ref.object.sha

        # Create new branch
        try:
            repo.create_git_ref(f"refs/heads/{branch_name}", base_sha)
            logger.debug(f"Created branch {branch_name} from {base_sha}")
        except Exception as e:
            logger.error(f"Failed to create branch: {e}")
            raise

        # Build Slack link
        slack_link = self._build_slack_link(workspace_id, channel_id, timestamp)

        # Prepare content based on whether we're updating or creating
        if is_update:
            logger.info(f"Found existing article at {file_path}, will extend it")
            try:
                existing_content = repo.get_contents(file_path, ref=default_branch).decoded_content.decode('utf-8')
            except Exception as e:
                logger.warning(f"Could not read existing file, will create new: {e}")
                existing_content = ""

            if existing_content:
                final_content = self._merge_articles(existing_content, summary, slack_link)
            else:
                final_content = f"""{summary}

---

**Sources:**
- [Slack Thread]({slack_link})"""
        else:
            final_content = f"""{summary}

---

**Source:** [Slack Thread]({slack_link})"""

        # Create or update file
        commit_message = f"{'Update' if is_update else 'Add'} KB article: {title}"
        try:
            existing_file = repo.get_contents(file_path, ref=branch_name)
            logger.debug("File exists in new branch, updating")
            repo.update_file(
                path=file_path,
                message=commit_message,
                content=final_content,
                sha=existing_file.sha,
                branch=branch_name
            )
        except GithubException:
            logger.debug("Creating new file in branch")
            repo.create_file(
                path=file_path,
                message=commit_message,
                content=final_content,
                branch=branch_name
            )

        # Create pull request
        pr_title = f"{'Update' if is_update else 'Add'} KB article: {title}"
        body = f"""## {'Updated' if is_update else 'New'} Knowledge Base Article from Slack

**Source:** [Slack Thread]({slack_link})
**Channel:** #{channel_name}
**Action:** {'Extended existing article with new information' if is_update else 'Created new article'}

This PR {'updates an existing' if is_update else 'adds a new'} knowledge base article generated from a Slack thread that was marked with a :pushpin: reaction.

### File
- `{file_path}`"""

        pr = repo.create_pull(
            title=pr_title,
            body=body,
            head=branch_name,
            base=default_branch
        )

        logger.info(f"Pull request created: {pr.html_url}")
        return pr.html_url

    def _search_existing_article(self, repo, sanitized_title: str, branch: str) -> Optional[str]:
        """Search for existing article with same/similar topic."""
        try:
            contents = repo.get_contents("knowledge-base", ref=branch)

            # Look for exact match
            for content in contents:
                if content.name == f"{sanitized_title}.md":
                    logger.debug(f"Found exact match: {content.path}")
                    return content.path

            # Look for similar titles (fuzzy match)
            title_words = [w for w in sanitized_title.split("-") if len(w) > 3]
            for content in contents:
                existing_title = content.name.removesuffix(".md")
                existing_words = [w for w in existing_title.split("-") if len(w) > 3]

                # If they share 50%+ of significant words, consider it a match
                common_words = set(title_words) & set(existing_words)
                if len(common_words) >= min(len(title_words), len(existing_words)) // 2:
                    logger.debug(f"Found similar match: {content.path}")
                    return content.path

            return None
        except Exception as e:
            logger.debug(f"Could not search for existing articles: {e}")
            return None

    def _merge_articles(self, existing_content: str, new_summary: str, new_slack_link: str) -> str:
        """Merge new content into existing article."""
        # Extract existing sources section
        sources_regex = re.compile(r"---\s*\*\*Source[s]?:\*\*(.+)$", re.DOTALL)
        existing_sources_match = sources_regex.search(existing_content)
        existing_sources = existing_sources_match.group(1).strip() if existing_sources_match else ""

        # Remove sources section from existing content
        content_without_sources = sources_regex.sub("", existing_content).strip()

        # Extract keywords from new summary and merge with existing
        new_keywords_regex = re.compile(r"\*\*Keywords:\*\*\s*(.+)")
        new_keywords_match = new_keywords_regex.search(new_summary)
        new_keywords = [k.strip() for k in new_keywords_match.group(1).split(",")] if new_keywords_match else []

        existing_keywords_match = new_keywords_regex.search(content_without_sources)
        existing_keywords = [k.strip() for k in existing_keywords_match.group(1).split(",")] if existing_keywords_match else []

        # Merge keywords (deduplicate)
        merged_keywords = list(dict.fromkeys(existing_keywords + new_keywords))[:10]

        # Update keywords line in existing content
        if existing_keywords_match and merged_keywords:
            content_with_updated_keywords = content_without_sources.replace(
                existing_keywords_match.group(0),
                f"**Keywords:** {', '.join(merged_keywords)}"
            )
        elif merged_keywords:
            # Add keywords after title if not present
            lines = content_without_sources.split("\n")
            title_index = next((i for i, line in enumerate(lines) if line.startswith("#")), -1)
            if title_index >= 0 and title_index + 1 < len(lines):
                lines.insert(title_index + 1, "")
                lines.insert(title_index + 2, f"**Keywords:** {', '.join(merged_keywords)}")
                content_with_updated_keywords = "\n".join(lines)
            else:
                content_with_updated_keywords = content_without_sources
        else:
            content_with_updated_keywords = content_without_sources

        # Extract main content from new summary (without title and keywords)
        new_summary_lines = new_summary.split("\n")
        new_content_start = next(
            (i for i, line in enumerate(new_summary_lines)
             if not line.startswith("#") and "**Keywords:**" not in line and line.strip()),
            0
        )
        new_content = "\n".join(new_summary_lines[new_content_start:]) if new_content_start >= 0 else new_summary

        # Build new sources list
        sources_list = []

        # Parse existing sources
        if existing_sources:
            if existing_sources.startswith("- [Slack Thread]"):
                sources_list.append(existing_sources.removeprefix("- "))
            elif "\n- [Slack Thread]" in existing_sources:
                for line in existing_sources.split("\n"):
                    if line.strip().startswith("- [Slack Thread]"):
                        sources_list.append(line.strip().removeprefix("- "))
            else:
                sources_list.append(f"[Slack Thread]({existing_sources})")

        # Add new source
        sources_list.append(f"[Slack Thread]({new_slack_link})")

        sources_section = (
            f"**Source:** {sources_list[0]}" if len(sources_list) == 1
            else f"**Sources:**\n" + "\n".join(f"- {s}" for s in sources_list)
        )

        return f"""{content_with_updated_keywords}

## Additional Context

{new_content}

---

{sources_section}"""

    def _build_slack_link(self, workspace_id: Optional[str], channel_id: str, timestamp: str) -> str:
        """Build Slack deep link."""
        message_id = timestamp.replace(".", "")
        if workspace_id:
            return f"https://slack.com/app_redirect?team={workspace_id}&channel={channel_id}&message_ts={timestamp}"
        else:
            return f"https://app.slack.com/client/{channel_id}/thread/{channel_id}/{message_id}"

    def _sanitize_for_filename(self, title: str) -> str:
        """Sanitize title for use as filename."""
        return re.sub(r"[^a-z0-9]+", "-", title.removeprefix("#").strip().lower()).strip("-")[:50]

    def _extract_title(self, summary: str) -> str:
        """Extract first heading from markdown."""
        lines = summary.split("\n")
        for line in lines:
            if line.startswith("#"):
                return line.removeprefix("#").strip()
        return "Slack Thread Summary"
