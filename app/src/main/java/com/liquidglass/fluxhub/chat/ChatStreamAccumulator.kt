package com.liquidglass.fluxhub.chat

data class ChatStreamSnapshot(
    val content: String,
    val thinkingContent: String?,
    val tokenCount: Int
) {
    val hasContent: Boolean = content.isNotBlank() || (!thinkingContent.isNullOrBlank())
    val contentLength: Int = content.length + (thinkingContent?.length ?: 0)
}

class ChatStreamAccumulator {
    private var content = ""
    private var thinkingContent = ""
    private var tokenCount = 0
    private var finished = false
    var chunkCount = 0
        private set
    var lastRawData = ""
        private set
    var lastHttpCode: Int? = null

    val isFinished: Boolean
        get() = finished

    fun recordEvent(rawData: String) {
        chunkCount++
        if (rawData.isNotBlank() && rawData != "[DONE]") {
            lastRawData = rawData.take(300)
        }
    }

    fun applyDelta(delta: ChatStreamDelta): ChatStreamSnapshot? {
        if (finished) return null

        var changed = false

        if (delta.reasoningContent.isNotEmpty()) {
            thinkingContent += delta.reasoningContent
            changed = true
        }

        if (delta.content.isNotEmpty()) {
            content += delta.content
            tokenCount += (delta.content.length / 4).coerceAtLeast(1)
            changed = true
        }

        return if (changed) snapshot() else null
    }

    fun finish(): Boolean {
        if (finished) return false
        finished = true
        return true
    }

    fun isStreamResetSuccess(message: String): Boolean {
        return message.contains("stream was reset", ignoreCase = true) && snapshot().hasContent
    }

    fun snapshot(): ChatStreamSnapshot {
        return ChatStreamSnapshot(
            content = content,
            thinkingContent = thinkingContent.takeIf { it.isNotEmpty() },
            tokenCount = tokenCount
        )
    }

    fun getDebugSummary(targetUrl: String, model: String): String {
        return buildString {
            append("⚠️ No content returned.\n\n")
            append("• Endpoint: $targetUrl\n")
            append("• Model: $model\n")
            if (lastHttpCode != null) append("• HTTP Status: $lastHttpCode\n")
            append("• SSE Chunks Received: $chunkCount\n")
            if (lastRawData.isNotEmpty()) {
                append("• Last Server Payload: $lastRawData")
            } else {
                append("• Server closed the connection immediately without sending text tokens.")
            }
        }
    }
}
