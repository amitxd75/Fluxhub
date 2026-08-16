package com.liquidglass.fluxhub.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

@Serializable
private data class ModelsResponse(
    val data: List<ModelResponse> = emptyList()
)

@Serializable
private data class ModelResponse(
    val id: String = ""
)

interface ChatStreamCallback {
    fun onOpen(responseCode: Int)
    fun onRawEvent(data: String) {}
    fun onDelta(delta: ChatStreamDelta)
    fun onDone()
    fun onClosed()
    fun onFailure(message: String, throwable: Throwable?, responseCode: Int?)
    fun onParseError(error: Exception)
}

class ChatApiClient(
    private val client: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun buildUrl(baseUrl: String, endpoint: String): String {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val cleanEndpoint = endpoint.trimStart('/')
        val fullUrl = if (!cleanBase.startsWith("http://", ignoreCase = true) && !cleanBase.startsWith("https://", ignoreCase = true)) {
            "https://$cleanBase"
        } else {
            cleanBase
        }
        return "$fullUrl/$cleanEndpoint"
    }

    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String
    ): List<String> = withContext(Dispatchers.IO) {
        val targetUrl = buildUrl(baseUrl, "models")
        val cleanApiKey = apiKey.trim()
        
        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .get()

        if (cleanApiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $cleanApiKey")
        }

        val response = client.newCall(requestBuilder.build()).execute()

        response.use {
            val body = it.body?.string() ?: "{}"
            if (!it.isSuccessful) {
                val errorMsg = try {
                    val element = json.parseToJsonElement(body)
                    val err = element.jsonObject["error"]
                    err?.jsonObject?.get("message")?.jsonPrimitive?.content
                        ?: (err as? JsonPrimitive)?.contentOrNull
                        ?: "HTTP ${it.code}: ${it.message}"
                } catch (e: Exception) {
                    "HTTP ${it.code}: ${it.message}"
                }
                throw ChatApiException(errorMsg)
            }

            try {
                val parsed = json.parseToJsonElement(body).jsonObject
                val listArray = parsed["data"]?.jsonArray ?: parsed["models"]?.jsonArray
                if (listArray != null) {
                    listArray.mapNotNull { item ->
                        val obj = item as? JsonObject
                        val id = (obj?.get("id") as? JsonPrimitive)?.contentOrNull
                            ?: (obj?.get("name") as? JsonPrimitive)?.contentOrNull
                            ?: (obj?.get("model") as? JsonPrimitive)?.contentOrNull
                            ?: (item as? JsonPrimitive)?.contentOrNull
                        id?.removePrefix("models/")
                    }.filter { it.isNotBlank() }.distinct().sorted()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                throw ChatApiException("Failed to parse model list: ${e.message}", e)
            }
        }
    }

    suspend fun executeNonStreaming(
        baseUrl: String,
        apiKey: String,
        requestBody: String
    ): ChatCompletionResult = withContext(Dispatchers.IO) {
        val targetUrl = buildUrl(baseUrl, "chat/completions")
        val cleanApiKey = apiKey.trim()

        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .post(requestBody.toRequestBody("application/json".toMediaType()))

        if (cleanApiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $cleanApiKey")
        }

        val response = client.newCall(requestBuilder.build()).execute()

        response.use {
            val body = it.body?.string() ?: "{}"
            if (!it.isSuccessful) {
                val errorMsg = try {
                    val element = json.parseToJsonElement(body)
                    val err = element.jsonObject["error"]
                    err?.jsonObject?.get("message")?.jsonPrimitive?.content
                        ?: "HTTP ${it.code}: ${it.message}"
                } catch (e: Exception) {
                    "HTTP ${it.code}: ${it.message}"
                }
                throw ChatApiException(errorMsg)
            }
            ChatResponseParser.parseNonStreaming(body)
        }
    }

    fun startStreaming(
        baseUrl: String,
        apiKey: String,
        requestBody: String,
        callback: ChatStreamCallback
    ): EventSource {
        val targetUrl = buildUrl(baseUrl, "chat/completions")
        val cleanApiKey = apiKey.trim()

        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(requestBody.toRequestBody("application/json".toMediaType()))

        if (cleanApiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $cleanApiKey")
        }

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                callback.onOpen(response.code)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                callback.onRawEvent(data)
                if (data == "[DONE]") {
                    callback.onDone()
                    return
                }

                try {
                    ChatResponseParser.parseStreamingEventData(data).forEach { delta ->
                        if (delta.isDone) {
                            callback.onDone()
                        } else {
                            callback.onDelta(delta)
                        }
                    }
                } catch (e: Exception) {
                    callback.onParseError(e)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                callback.onClosed()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val errorBody = try {
                    val bodyStr = response?.body?.string()?.take(1000)
                    if (!bodyStr.isNullOrBlank()) {
                        val element = json.parseToJsonElement(bodyStr)
                        val err = element.jsonObject["error"]
                        err?.jsonObject?.get("message")?.jsonPrimitive?.content
                            ?: (err as? kotlinx.serialization.json.JsonPrimitive)?.content
                            ?: bodyStr
                    } else null
                } catch (e: Exception) {
                    null
                }
                val failureMessage = when {
                    !errorBody.isNullOrBlank() -> errorBody
                    response != null -> "HTTP ${response.code} ${response.message}".trim()
                    t != null -> t.localizedMessage ?: t.message ?: "Network connection failed"
                    else -> "Unknown network error"
                }
                callback.onFailure(
                    message = failureMessage,
                    throwable = t,
                    responseCode = response?.code
                )
            }
        }

        return EventSources.createFactory(client).newEventSource(requestBuilder.build(), listener)
    }
}
