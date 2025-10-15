# Summarizing Slack Threads with Gemini

**Keywords:** Slack, Thread Summarization, Gemini, LLM, API, Python, Github Actions

## Overview

This document describes a Python-based tool designed to summarize Slack threads using the Gemini language model. It outlines the project's architecture, usage, and example implementation.

## Project Architecture

The project, available at [https://github.com/ribafish/slack-thread-summarizer](https://github.com/ribafish/slack-thread-summarizer), leverages the Gemini language model to generate summaries of Slack threads.  The intended workflow involves a Slack workflow sending a payload to the summarizer.

## Usage

The primary method for interacting with the summarizer is via a curl command that simulates the payload that would originate from a Slack workflow.

## Example Implementation

An example of the summarizer's output can be found in this pull request: [https://github.com/ribafish/slack-thread-summarizer/pull/4](https://github.com/ribafish/slack-thread-summarizer/pull/4).  This pull request demonstrates the summarization process based on a sample Slack thread. The sample thread content has been sanitized to remove customer data.


---

**Source:** [Slack Thread](https://gradle.slack.com/archives/C0993HJU7HA/p1760429928606289)