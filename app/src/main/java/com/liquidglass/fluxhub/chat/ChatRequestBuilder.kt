package com.liquidglass.fluxhub.chat

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ChatMessage(
    val role: String,
    val content: JsonElement? = null
)

object ChatRequestBuilder {
    fun buildMessages(
        history: List<UiMessage>,
        aiMessageId: String?,
        contextSize: Int,
        systemPrompt: String?,
        webSearch: Boolean = false,
        imageBase64Loader: (String) -> String?
    ): List<ChatMessage> {
        val baseMessages = history
            .filter {
                it.role == "user" ||
                    (it.role == "assistant" && !it.isStreaming && it.content.isNotBlank() && it.id != aiMessageId)
            }

        // Determine message slice: token-based (> 128), legacy count (<= 128), or unlimited (0)
        val selectedMessages: List<UiMessage> = when {
            contextSize <= 0 -> baseMessages
            contextSize <= 128 -> baseMessages.takeLast(contextSize)
            else -> {
                // Token-budget-based trimming (~4 chars per token)
                var accumulatedTokens = (systemPrompt?.length ?: 0) / 4
                val reversedList = mutableListOf<UiMessage>()
                for (msg in baseMessages.reversed()) {
                    val msgTokens = (msg.content.length / 4) + 4
                    if (accumulatedTokens + msgTokens <= contextSize || reversedList.isEmpty()) {
                        accumulatedTokens += msgTokens
                        reversedList.add(msg)
                    } else {
                        break
                    }
                }
                reversedList.reversed()
            }
        }

        val processedMessages = selectedMessages.map { message ->
            ChatMessage(message.role, buildContentElement(message.content, imageBase64Loader))
        }.filter { it.content != null }

        val webSearchDirective = if (webSearch) {
            "\n\n[Web Search Mode: Active. You have live Internet search capabilities. When answering questions about real-time, current events, recent developments, people, dates, or factual queries, perform web search and retrieve fresh web information. Provide a detailed, helpful, and up-to-date answer directly to the user.]"
        } else ""

        val finalSystemPrompt = when {
            systemPrompt.isNullOrBlank() && webSearch -> webSearchDirective.trim()
            !systemPrompt.isNullOrBlank() -> (systemPrompt + webSearchDirective).trim()
            else -> null
        }

        return if (!finalSystemPrompt.isNullOrBlank()) {
            listOf(ChatMessage("system", JsonPrimitive(finalSystemPrompt))) + processedMessages
        } else {
            processedMessages
        }
    }

    fun buildRequestJson(
        model: String,
        messages: List<ChatMessage>,
        stream: Boolean,
        temperature: Float,
        topP: Float,
        maxTokens: Int?,
        reasoningEffort: String?,
        thinkingBudget: Int = 0,
        includeStreamOptions: Boolean = false,
        webSearch: Boolean = false,
        effectiveBaseUrl: String = ""
    ): JsonObject {
        val modelLower = model.lowercase()
        val isReasoningModel = modelLower.startsWith("o1") || 
                               modelLower.startsWith("o3") || 
                               modelLower.contains("reasoner") || 
                               modelLower.contains("r1")

        return buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                messages.forEach { message ->
                    add(buildJsonObject {
                        put("role", message.role)
                        put("content", message.content ?: JsonPrimitive(""))
                    })
                }
            })
            put("stream", stream)
            put("temperature", temperature)
            put("top_p", topP)
            maxTokens?.let { put("max_tokens", it) }
            
            if (!reasoningEffort.isNullOrBlank()) {
                put("reasoning_effort", reasoningEffort)
            }

            if (includeStreamOptions && stream) {
                put("stream_options", buildJsonObject {
                    put("include_usage", true)
                })
            }
        }
    }

    fun shouldSendOpenAiOnlyOptions(effectiveBaseUrl: String): Boolean {
        return false
    }

    fun reasoningEffortOrNull(effectiveBaseUrl: String, thinkingBudget: Int): String? {
        if (thinkingBudget == 0) return null
        if (thinkingBudget == -1) return null
        return when (thinkingBudget) {
            in 1..4096 -> "low"
            in 4097..16000 -> "medium"
            else -> "high"
        }
    }

    private fun buildContentElement(
        content: String,
        imageBase64Loader: (String) -> String?
    ): JsonElement {
        val parsedContent = ChatMessageContentParser.parse(content)
        val imageUrl = parsedContent.imageUrl
        if (imageUrl != null) {
            val textContent = parsedContent.text.trim()
            val base64 = imageBase64Loader(imageUrl)

            if (base64 != null) {
                return buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", textContent)
                    })
                    add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject {
                            put("url", "data:image/jpeg;base64,$base64")
                        })
                    })
                }
            }
        }

        return parseJsonArrayOrText(content)
    }

    private fun parseJsonArrayOrText(content: String): JsonElement {
        val trimmed = content.trim()
        if (trimmed.startsWith("[")) {
            val parsed = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(trimmed)
            }.getOrNull()
            if (parsed is JsonArray) {
                return parsed
            }
        }
        return JsonPrimitive(content)
    }
}
