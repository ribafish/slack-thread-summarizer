package com.ribafish.slacksummarizer.models

data class SlackThread(
    val channelId: String,
    val channelName: String,
    val threadTs: String,
    val messages: List<SlackMessage>,
    val workspaceId: String? = null
)

data class SlackMessage(
    val user: String,
    val username: String,
    val text: String,
    val timestamp: String
)
