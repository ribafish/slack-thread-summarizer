package com.ribafish.slacksummarizer.services

import com.ribafish.slacksummarizer.config.SlackConfig
import com.ribafish.slacksummarizer.models.SlackMessage
import com.ribafish.slacksummarizer.models.SlackThread
import com.slack.api.Slack
import com.slack.api.methods.MethodsClient
import com.slack.api.methods.request.conversations.ConversationsRepliesRequest
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class SlackService(private val config: SlackConfig) {
    private val slack: Slack = Slack.getInstance()
    private val client: MethodsClient = slack.methods(config.botToken)

    suspend fun fetchThread(channelId: String, threadTs: String): SlackThread {
        logger.debug { "Fetching thread $threadTs from channel $channelId" }

        // Try to join the channel if it's public (will fail silently for private channels)
        try {
            val joinResponse = client.conversationsJoin { req ->
                req.channel(channelId)
            }
            if (joinResponse.isOk) {
                logger.debug { "Joined channel $channelId" }
            } else if (joinResponse.error != "already_in_channel") {
                logger.debug { "Could not join channel $channelId: ${joinResponse.error}" }
            }
        } catch (e: Exception) {
            logger.debug(e) { "Exception joining channel $channelId: ${e.message}" }
        }

        // Get channel info
        val channelInfo = client.conversationsInfo { req ->
            req.channel(channelId)
        }

        val channelName = if (channelInfo.isOk) {
            channelInfo.channel.name ?: channelId
        } else {
            logger.warn { "Failed to get channel info: ${channelInfo.error}" }
            channelId
        }

        // Get thread messages
        val repliesRequest = ConversationsRepliesRequest.builder()
            .channel(channelId)
            .ts(threadTs)
            .build()

        val repliesResponse = client.conversationsReplies(repliesRequest)

        if (!repliesResponse.isOk) {
            logger.error { "Failed to fetch thread: ${repliesResponse.error}" }
            return SlackThread(channelId, channelName, threadTs, emptyList())
        }

        val messages = repliesResponse.messages.map { message ->
            val userId = message.user ?: "unknown"
            val username = getUserName(userId)

            SlackMessage(
                user = userId,
                username = username,
                text = message.text ?: "",
                timestamp = message.ts
            )
        }

        logger.debug { "Fetched ${messages.size} messages from thread" }

        return SlackThread(
            channelId = channelId,
            channelName = channelName,
            threadTs = threadTs,
            messages = messages
        )
    }

    private fun getUserName(userId: String): String {
        return try {
            val userInfo = client.usersInfo { req ->
                req.user(userId)
            }

            if (userInfo.isOk) {
                userInfo.user.realName ?: userInfo.user.name ?: userId
            } else {
                logger.warn { "Failed to get user info for $userId: ${userInfo.error}" }
                userId
            }
        } catch (e: Exception) {
            logger.warn(e) { "Error fetching user info for $userId" }
            userId
        }
    }
}
