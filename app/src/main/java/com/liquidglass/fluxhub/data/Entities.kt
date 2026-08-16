package com.liquidglass.fluxhub.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val thinkingContent: String? = null,
    val model: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val assistantId: String? = null, // Associated assistant ID
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Assistant entity - Each assistant has its own prompt and parameter configuration
 */
@Serializable
@Entity(tableName = "assistants")
data class AssistantEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val avatar: String? = null, // Emoji or image URI
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val maxTokens: Int? = null,
    val modelId: String? = null, // Specific model ID (optional)
    val createdAt: Long = System.currentTimeMillis(),
    val isDefault: Boolean = false // Default assistant flag
)

/**
 * Provider entity - Supports multiple API providers
 */
@Serializable
@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val icon: String? = null, // Emoji or image URI
    val isActive: Boolean = false, // Currently active provider
    val createdAt: Long = System.currentTimeMillis()
)
