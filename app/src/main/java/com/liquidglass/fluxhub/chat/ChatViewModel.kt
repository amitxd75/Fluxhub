package com.liquidglass.fluxhub.chat

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.liquidglass.fluxhub.data.AppDatabase
import com.liquidglass.fluxhub.data.ConversationEntity
import com.liquidglass.fluxhub.data.DataRepository
import com.liquidglass.fluxhub.data.MessageEntity
import com.liquidglass.fluxhub.data.AssistantEntity
import com.liquidglass.fluxhub.data.ProviderEntity
import com.liquidglass.fluxhub.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.sse.EventSource
import java.util.UUID
import java.util.concurrent.TimeUnit
import android.net.Uri
import com.liquidglass.fluxhub.utils.FileUtils
import kotlinx.serialization.json.*

import java.util.Collections

private const val TAG = "ChatViewModel"

// Message model for UI display (supports message branching)
// @Immutable helps Compose skip unnecessary recompositions
@androidx.compose.runtime.Immutable
data class UiMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val thinkingContent: String? = null,
    val isStreaming: Boolean = false,
    val model: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    // Message branch support
    val parentId: String? = null,  // Parent message ID (for branch tracking)
    val versionIndex: Int = 0,     // Current version index
    val totalVersions: Int = 1     // Total versions count
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val chatApiClient = ChatApiClient(client)
    
    private val database = AppDatabase.getDatabase(application)
    
    private val messageDao = database.messageDao()
    private val conversationDao = database.conversationDao()
    private val assistantDao = database.assistantDao()
    private val providerDao = database.providerDao()
    private val settingsRepository = SettingsRepository(application)
    private val dataRepository = DataRepository(application)
    
    // UI State
    var messages = mutableStateListOf<UiMessage>()
    val availableModels = mutableStateListOf<String>()
    val assistants = mutableStateListOf<AssistantEntity>()
    val providers = mutableStateListOf<ProviderEntity>()
    
    var isLoading by mutableStateOf(false)
        private set
    var streamingTokenCount by mutableIntStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var showError by mutableStateOf(false)
        private set
    
    // Whether settings are initialized (prevents wallpaper flashing)
    var isSettingsInitialized by mutableStateOf(false)
        private set
    
    // Configuration (loaded from current Provider or DataStore)
    var apiKey by mutableStateOf("")
    var baseUrl by mutableStateOf("https://api.openai.com/v1")
    var model by mutableStateOf("") // Empty by default, selected by user
    var defaultModel by mutableStateOf("") // Global default model
    
    // Request parameters (read from current assistant)
    var temperature by mutableStateOf(0.7f)
    var topP by mutableStateOf(1.0f)
    var maxTokens by mutableStateOf<Int?>(null) // null = use model default
    
    // Track deleted message IDs to prevent resurrection during sync
    private val deletedMessageIds = Collections.synchronizedSet(HashSet<String>())
    
    // Track transient conversation IDs (not yet saved to DB)
    private val transientConversationIds = Collections.synchronizedSet(HashSet<String>())
    // Pending system prompt for first user message
    private var pendingSystemPrompt: String? = null
    
    // Selected image URI (Vision)
    var selectedImageUri by mutableStateOf<Uri?>(null)

    fun stopGeneration() {
        currentEventSource?.cancel()
        isLoading = false
    }
    
    // Input text (persisted in ViewModel across navigation)
    var inputText by mutableStateOf("")
    
    // Editing state: ID of the message being edited
    var editingMessageId by mutableStateOf<String?>(null)
        private set
    
    /**
     * Start editing message
     */
    fun startEditingMessage(messageId: String, content: String) {
        editingMessageId = messageId
        inputText = content
    }
    
    /**
     * Cancel editing
     */
    fun cancelEditing() {
        editingMessageId = null
        inputText = ""
    }
    
    /**
     * Check if currently editing
     */
    fun isEditing(): Boolean = editingMessageId != null
    
    // Display settings
    var themeMode by mutableStateOf("system") // system, light, dark
    var wallpaperUri by mutableStateOf<String?>(null)

    var glassOpacity by mutableStateOf(0.1f)
        private set

    var glassBlur by mutableStateOf(16f)
        private set
    
    var glassColor by mutableStateOf("default") // default, or hex color like "FF007AFF"
        private set
    
    // Current assistant
    var currentAssistant by mutableStateOf<AssistantEntity?>(null)
        private set
    
    // Current provider
    var currentProvider by mutableStateOf<ProviderEntity?>(null)
        private set

    // Current conversation
    var currentConversationId by mutableStateOf<String?>(null)
        private set
    var currentConversationTitle by mutableStateOf("New Chat")
        private set
    
    // Conversation list
    val conversations = mutableStateListOf<ConversationEntity>()
    
    // User agreement state
    var agreementAccepted by mutableStateOf(true)
        private set
    
    // ========== Toolbox configuration (Global persistent storage) ==========
    var thinkingBudget by mutableStateOf(1024)
        private set
    var webSearchEnabled by mutableStateOf(false)
        private set
    var searchProvider by mutableStateOf(0)
        private set
    var streamEnabled by mutableStateOf(true)
        private set
    var contextSize by mutableStateOf(64)
        private set
    
    // ========== Dynamic Island configuration ==========
    var dynamicIslandEnabled by mutableStateOf(true)
        private set
    var loginNotificationMode by mutableStateOf("first") // "first" or "every"
    var dynamicIslandDuration by mutableStateOf(3) // seconds
        private set
    var showTokenCount by mutableStateOf(true)
        private set
    var showElapsedTime by mutableStateOf(true)
        private set
    
    // Haptic feedback
    var hapticFeedbackEnabled by mutableStateOf(true)
        private set
    
    // ========== Typography style configuration ==========
    var textColorMode by mutableStateOf("white") // white, black
        private set
    var textShadowEnabled by mutableStateOf(true)
        private set

    // Active EventSource (for cancellation)
    private var currentEventSource: EventSource? = null
    
    // Generation task Job
    private var generationJob: Job? = null
    private var requestTimeoutJob: Job? = null
    
    // Flow collection jobs
    private var messagesJob: Job? = null
    private var conversationsJob: Job? = null
    
    // Model list fetching job (debounced)
    private var fetchModelsJob: Job? = null
    
    init {
        loadSettings()
        startConversationsCollection()
        startAssistantsCollection()
        startProvidersCollection()
        createNewConversation()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                wallpaperUri = settingsRepository.wallpaperUri.first()
                agreementAccepted = settingsRepository.agreementAccepted.first()
                defaultModel = settingsRepository.defaultModel.first()
                isSettingsInitialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to preload settings", e)
                isSettingsInitialized = true
            }

            collectSetting(settingsRepository.apiKey) { value ->
                if (currentProvider == null) {
                    apiKey = value
                    if (value.isNotBlank() && baseUrl.isNotBlank()) fetchModels()
                }
            }
            collectSetting(settingsRepository.baseUrl) { value ->
                if (currentProvider == null) {
                    baseUrl = value
                    if (value.isNotBlank() && apiKey.isNotBlank()) fetchModels()
                }
            }
            collectSetting(settingsRepository.model) { model = it }
            collectSetting(settingsRepository.defaultModel) { defaultModel = it }
            collectSetting(settingsRepository.themeMode) { themeMode = it }
            collectSetting(settingsRepository.wallpaperUri) { wallpaperUri = it }
            collectSetting(settingsRepository.glassOpacity) { glassOpacity = it }
            collectSetting(settingsRepository.glassBlur) { glassBlur = it }
            collectSetting(settingsRepository.glassColor) { glassColor = it }
            collectSetting(settingsRepository.agreementAccepted) { agreementAccepted = it }
            // Load toolbox configurations
            collectSetting(settingsRepository.thinkingBudget) { thinkingBudget = it }
            collectSetting(settingsRepository.webSearchEnabled) { webSearchEnabled = it }
            collectSetting(settingsRepository.searchProvider) { searchProvider = it }
            collectSetting(settingsRepository.streamEnabled) { streamEnabled = it }
            collectSetting(settingsRepository.contextSize) { contextSize = it }
            // Load dynamic island configurations
            collectSetting(settingsRepository.dynamicIslandEnabled) { dynamicIslandEnabled = it }
            collectSetting(settingsRepository.loginNotificationMode) { loginNotificationMode = it }
            collectSetting(settingsRepository.dynamicIslandDuration) { dynamicIslandDuration = it }
            collectSetting(settingsRepository.showTokenCount) { showTokenCount = it }
            collectSetting(settingsRepository.showElapsedTime) { showElapsedTime = it }
            // Haptic feedback
            collectSetting(settingsRepository.hapticFeedbackEnabled) { hapticFeedbackEnabled = it }
            // Load typography configuration
            collectSetting(settingsRepository.textColorMode) { textColorMode = it }
            collectSetting(settingsRepository.textShadowEnabled) { textShadowEnabled = it }
        }
    }

    private fun <T> CoroutineScope.collectSetting(
        settingFlow: Flow<T>,
        onValue: (T) -> Unit
    ) {
        launch {
            settingFlow.collect { value -> onValue(value) }
        }
    }

    private fun persistSetting(write: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch {
            settingsRepository.write()
        }
    }
    
    fun updateHapticFeedbackEnabled(enabled: Boolean) {
        hapticFeedbackEnabled = enabled
        persistSetting { setHapticFeedbackEnabled(enabled) }
    }
    
    fun updateTextColorMode(mode: String) {
        persistSetting { setTextColorMode(mode) }
    }
    
    fun updateTextShadowEnabled(enabled: Boolean) {
        persistSetting { setTextShadowEnabled(enabled) }
    }
    
    fun updateGlassColor(color: String) {
        persistSetting { setGlassColor(color) }
    }
    
    fun updateDefaultModel(value: String) {
        defaultModel = value
        persistSetting { setDefaultModel(value) }
    }
    
    fun updateGlassBlur(blur: Float) {
        glassBlur = blur
        persistSetting { setGlassBlur(blur) }
    }
    
    fun exportData(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val json = dataRepository.exportData()
                onResult(json)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }
    
    fun importData(uri: android.net.Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = dataRepository.importData(uri)
            if (success) {
                currentConversationId?.let { switchConversation(it) }
            }
            onResult(success)
        }
    }
    
    fun updateGlassOpacity(opacity: Float) {
        glassOpacity = opacity
        persistSetting { setGlassOpacity(opacity) }
    }
    
    fun acceptAgreement() {
        agreementAccepted = true
        persistSetting { setAgreementAccepted(true) }
    }
    
    fun fetchModels() {
        if (apiKey.isBlank() || baseUrl.isBlank()) return
        
        fetchModelsJob?.cancel()
        
        fetchModelsJob = viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            
            try {
                Log.d(TAG, "Fetching models from: $baseUrl/models")

                val modelIds = chatApiClient.fetchModels(baseUrl, apiKey)

                Log.d(TAG, "Fetched ${modelIds.size} models from $baseUrl")
                availableModels.clear()
                availableModels.addAll(modelIds)

                if (model.isNotBlank() && !modelIds.contains(model)) {
                    Log.w(TAG, "Current model $model not available in $baseUrl")
                }
            } catch (e: ChatApiException) {
                Log.e(TAG, "Failed to fetch models from $baseUrl", e)
                if (availableModels.isEmpty()) {
                    val errorMessage = e.message ?: "Failed to fetch model list"
                    showErrorMessage(
                        if (errorMessage.startsWith("HTTP ")) {
                            "Failed to fetch model list: $errorMessage"
                        } else {
                            errorMessage
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network error fetching models from $baseUrl", e)
                if (availableModels.isEmpty()) {
                    showErrorMessage("Network error, unable to fetch model list")
                }
            }
        }
    }

    private fun loadOrCreateConversation() {
        viewModelScope.launch {
            val savedId = settingsRepository.currentConversationId.first()
            if (savedId != null) {
                val conversation = conversationDao.getConversation(savedId)
                if (conversation != null) {
                    currentConversationId = savedId
                    currentConversationTitle = conversation.title
                    startMessagesCollection(savedId)
                    return@launch
                }
            }
            createNewConversation(showNotification = false)
        }
    }
    
    private fun startMessagesCollection(conversationId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            messageDao.getMessagesForConversation(conversationId).collect { entities ->
                val dbMessages = entities.map { entity ->
                    UiMessage(
                        id = entity.id,
                        role = entity.role,
                        content = entity.content,
                        thinkingContent = entity.thinkingContent,
                        isStreaming = false,
                        model = entity.model,
                        timestamp = entity.timestamp
                    )
                }

                val finalMessages = ChatMessageSyncPlanner.merge(
                    dbMessages = dbMessages,
                    currentMessages = messages,
                    deletedMessageIds = deletedMessageIds
                )
                val uiOnlyMessageCount = finalMessages.size - dbMessages.size
                
                if (messages.size != finalMessages.size) {
                    messages.clear()
                    messages.addAll(finalMessages)
                } else {
                    finalMessages.forEachIndexed { index, newMessage ->
                        if (messages[index] != newMessage) {
                            messages[index] = newMessage
                        }
                    }
                }
                
                Log.d(TAG, "Messages synced: ${dbMessages.size} from DB, $uiOnlyMessageCount UI-only. Total: ${messages.size}")
            }
        }
    }
    
    fun createNewConversation(systemPrompt: String? = null, title: String = "New Chat", showNotification: Boolean = false) {
        val newId = UUID.randomUUID().toString()
        currentConversationId = newId
        currentConversationTitle = title
        messages.clear()
        
        if (defaultModel.isNotBlank()) {
            model = defaultModel
        }
        
        transientConversationIds.add(newId)
        pendingSystemPrompt = systemPrompt
        
        startMessagesCollection(newId)
        
        viewModelScope.launch {
            settingsRepository.setCurrentConversationId(newId)
            if (showNotification) {
                com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showSuccess(
                    message = "New chat created",
                    avatar = "✨"
                )
            }
        }
    }
    
    fun switchConversation(conversationId: String) {
        if (conversationId == currentConversationId) return
        
        val previousConversationId = currentConversationId
        
        currentConversationId = conversationId
        messages.clear()
        
        cancelGeneration()
        
        viewModelScope.launch {
            if (previousConversationId != null) {
                if (transientConversationIds.contains(previousConversationId)) {
                    Log.d(TAG, "Discarding transient conversation: $previousConversationId")
                    transientConversationIds.remove(previousConversationId)
                } else {
                    val previousMessages = messageDao.getMessageCountForConversation(previousConversationId)
                    if (previousMessages == 0) {
                        Log.d(TAG, "Cleaning up empty conversation: $previousConversationId")
                        conversationDao.deleteConversation(previousConversationId)
                    }
                }
            }
            
            val conversation = conversationDao.getConversation(conversationId)
            if (conversation != null) {
                currentConversationTitle = conversation.title
                settingsRepository.setCurrentConversationId(conversationId)
                startMessagesCollection(conversationId)
            }
        }
    }
    
    fun deleteConversation(conversationId: String) {
        if (conversationId == currentConversationId) {
            messages.clear()
        }

        viewModelScope.launch {
            messageDao.deleteMessagesForConversation(conversationId)
            conversationDao.deleteConversation(conversationId)
            
            com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showSuccess(
                message = "Conversation deleted",
                avatar = "🗑️"
            )
        }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            conversationDao.updateConversationTitle(conversationId, newTitle)
            if (conversationId == currentConversationId) {
                currentConversationTitle = newTitle
            }
            com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showSuccess(
                message = "Conversation renamed",
                avatar = "✏️"
            )
        }
    }

    fun deleteMessage(messageId: String) {
        deletedMessageIds.add(messageId)
        messages.removeAll { it.id == messageId }
        
        viewModelScope.launch {
            messageDao.deleteMessage(messageId)
        }
    }
    
    /**
     * Delete specified message and all following messages
     */
    fun deleteMessageAndFollowing(messageId: String) {
        val plan = ChatMessageBranchPlanner.deleteMessageAndFollowing(messages, messageId)
        if (plan == null) {
            Log.w(TAG, "deleteMessageAndFollowing: message not found: $messageId")
            return
        }
        
        deletedMessageIds.addAll(plan.idsToDelete)
        
        Log.d(TAG, "deleteMessageAndFollowing: deleting ${plan.idsToDelete.size} messages starting from index ${plan.startIndex}")
        
        val messagesToKeep = messages.take(plan.startIndex)
        messages.clear()
        messages.addAll(messagesToKeep)
        
        viewModelScope.launch {
            plan.idsToDelete.forEach { id ->
                messageDao.deleteMessage(id)
            }
            Log.d(TAG, "deleteMessageAndFollowing: database deletion complete")
        }
    }
    
    /**
     * Handle message editing
     * 
     * @param newContent The updated content
     */
    fun handleMessageEdit(newContent: String) {
        val messageId = editingMessageId ?: return
        val conversationId = currentConversationId ?: return
        
        if (newContent.isBlank()) {
            cancelEditing()
            return
        }
        
        val plan = ChatMessageBranchPlanner.editMessage(messages, messageId)
        if (plan == null) {
            cancelEditing()
            return
        }
        
        Log.d(TAG, "handleMessageEdit: editing message at index ${plan.messageIndex}")
        
        cancelGeneration()
        
        // 1. Delete following messages
        deletedMessageIds.addAll(plan.idsToDelete)
        
        // 2. Update UI message content
        val originalMessage = messages[plan.messageIndex]
        messages[plan.messageIndex] = originalMessage.copy(content = newContent)
        
        // 3. Remove subsequent messages from UI
        messages.removeAll { plan.idsToDelete.contains(it.id) }
        
        // 4. Clear editing state
        editingMessageId = null
        inputText = ""
        
        // 5. Update database asynchronously
        viewModelScope.launch {
            plan.idsToDelete.forEach { id ->
                messageDao.deleteMessage(id)
            }
            
            val existingMessage = messageDao.getMessage(messageId)
            if (existingMessage != null) {
                messageDao.updateMessage(existingMessage.copy(content = newContent))
            }
            
            conversationDao.updateConversationTimestamp(conversationId, System.currentTimeMillis())
        }
        
        // 6. Regenerate AI response
        initiateAiResponse(conversationId)
    }
    
    /**
     * Regenerate message
     */
    fun regenerate(messageId: String) {
        if (ChatMessageBranchPlanner.regenerateFrom(messages, messageId) == null) return
        val conversationId = currentConversationId ?: return
        
        cancelGeneration()
        
        generationJob = viewModelScope.launch {
            try {
                val plan = ChatMessageBranchPlanner.regenerateFrom(messages, messageId) ?: return@launch
                deletedMessageIds.addAll(plan.idsToDelete)

                plan.idsToDelete.forEach { id ->
                    messageDao.deleteMessage(id)
                }
                messages.removeAll { plan.idsToDelete.contains(it.id) }
                initiateAiResponse(conversationId)
            } catch (e: Exception) {
                Log.e(TAG, "regenerate failed", e)
                showErrorMessage("Retry failed: ${e.message}")
            }
        }
    }
    
    private fun initiateAiResponseWithParent(conversationId: String, parentId: String) {
        val parentMessage = messages.find { it.id == parentId }
        val newVersionIndex = (parentMessage?.versionIndex ?: 0) + 1
        
        val aiMessageId = UUID.randomUUID().toString()
        messages.add(UiMessage(
            id = aiMessageId,
            role = "assistant",
            content = "",
            thinkingContent = "",
            isStreaming = streamEnabled,
            model = model,
            parentId = parentId,
            versionIndex = newVersionIndex,
            totalVersions = newVersionIndex + 1
        ))
        
        isLoading = true
        clearError()
        
        if (streamEnabled) {
            callStreamingApiWithEventSource(aiMessageId, conversationId)
        } else {
            callNonStreamingApi(aiMessageId, conversationId)
        }
    }
    
    /**
     * Switch message version (for branching)
     */
    fun switchMessageVersion(messageId: String, direction: Int) {
        val plan = ChatMessageVersionPlanner.planSwitch(messages, messageId, direction) ?: return
        messages[plan.messageIndex] = plan.updatedMessage
    }
    
    /**
     * Cancel active generation task
     */
    fun cancelGeneration() {
        currentEventSource?.cancel()
        currentEventSource = null
        generationJob?.cancel()
        generationJob = null
        requestTimeoutJob?.cancel()
        requestTimeoutJob = null
        isLoading = false
        
        val lastMessage = messages.lastOrNull()
        if (lastMessage?.isStreaming == true) {
            val index = messages.indexOfLast { it.isStreaming }
            if (index >= 0) {
                messages[index] = messages[index].copy(isStreaming = false)
            }
        }
    }

    private fun startConversationsCollection() {
        conversationsJob?.cancel()
        conversationsJob = viewModelScope.launch {
            combine(
                snapshotFlow { currentAssistant },
                conversationDao.getAllConversations()
            ) { assistant, allConversations ->
                ChatConversationSelector.filterForAssistant(assistant, allConversations)
            }.collect { filteredList ->
                conversations.clear()
                conversations.addAll(filteredList)
                
                when (val selection = ChatConversationSelector.nextSelection(
                    currentConversationId = currentConversationId,
                    conversations = conversations,
                    transientConversationIds = transientConversationIds
                )) {
                    ConversationSelection.KeepCurrent -> {
                        if (currentConversationId != null && transientConversationIds.contains(currentConversationId)) {
                            Log.d(TAG, "Current conversation $currentConversationId is transient, staying put.")
                        }
                    }
                    is ConversationSelection.SwitchTo -> switchConversation(selection.conversationId)
                    ConversationSelection.CreateNew -> createNewConversation()
                }
            }
        }
    }
    
    private var assistantsJob: Job? = null
    
    private fun startAssistantsCollection() {
        assistantsJob?.cancel()
        assistantsJob = viewModelScope.launch {
            assistantDao.getAllAssistants().collect { list ->
                if (list.isEmpty()) {
                    launch {
                        val defaultAssistant = AssistantEntity(
                            id = UUID.randomUUID().toString(),
                            name = "General Assistant",
                            avatar = "🤖",
                            systemPrompt = "You are a helpful AI assistant.",
                            isDefault = true,
                            modelId = null,
                            temperature = 0.7f,
                            topP = 1.0f
                        )
                        assistantDao.insertAssistant(defaultAssistant)
                    }
                    assistants.clear()
                } else {
                    assistants.clear()
                    assistants.addAll(list)
                    
                    if (currentAssistant == null) {
                        currentAssistant = ChatAssistantSelector.defaultAssistant(list)
                        currentAssistant?.let { applyAssistantSettings(it) }
                    }
                }
            }
        }
    }
    
    fun switchAssistant(assistant: AssistantEntity) {
        currentAssistant = assistant
        applyAssistantSettings(assistant)
        com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showSuccess(
            message = "Assistant switched",
            avatar = assistant.avatar ?: "🤖"
        )
    }
    
    private fun applyAssistantSettings(assistant: AssistantEntity) {
        temperature = assistant.temperature
        topP = assistant.topP
        maxTokens = assistant.maxTokens
    }
    
    fun createAssistant(
        name: String,
        avatar: String? = null,
        systemPrompt: String = "",
        temperature: Float = 0.7f,
        topP: Float = 1.0f,
        maxTokens: Int? = null,
        modelId: String? = null
    ) {
        viewModelScope.launch {
            val assistant = AssistantEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                avatar = avatar,
                systemPrompt = systemPrompt,
                temperature = temperature,
                topP = topP,
                maxTokens = maxTokens,
                modelId = modelId,
                isDefault = assistants.isEmpty()
            )
            assistantDao.insertAssistant(assistant)
            com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showSuccess(
                message = "Assistant created",
                avatar = avatar ?: "🤖"
            )
        }
    }
    
    fun updateAssistant(assistant: AssistantEntity) {
        viewModelScope.launch {
            assistantDao.updateAssistant(assistant)
            if (currentAssistant?.id == assistant.id) {
                currentAssistant = assistant
                applyAssistantSettings(assistant)
            }
            com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showSuccess(
                message = "Assistant updated",
                avatar = assistant.avatar
            )
        }
    }
    
    fun deleteAssistant(assistantId: String) {
        viewModelScope.launch {
            assistantDao.deleteAssistant(assistantId)
            if (currentAssistant?.id == assistantId) {
                currentAssistant = ChatAssistantSelector.fallbackAfterDelete(assistantId, assistants)
                currentAssistant?.let { applyAssistantSettings(it) }
            }
            com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showSuccess(
                message = "Assistant deleted",
                avatar = "🗑️"
            )
        }
    }
    
    // ========== Provider Management ==========
    
    private var providersJob: Job? = null
    
    private fun startProvidersCollection() {
        providersJob?.cancel()
        providersJob = viewModelScope.launch {
            providerDao.getAllProviders().collect { list ->
                providers.clear()
                providers.addAll(list)

                ChatProviderSelector.updatedConfigurationProvider(currentProvider, list)?.let { updated ->
                    Log.d(TAG, "Active provider config updated from DB")
                    currentProvider = updated
                    applyProviderSettings(updated)
                    fetchModels()
                }

                if (currentProvider == null && list.isNotEmpty()) {
                    currentProvider = ChatProviderSelector.defaultProvider(list)
                    currentProvider?.let { applyProviderSettings(it) }
                    fetchModels()
                }
            }
        }
    }
    
    fun switchProvider(provider: ProviderEntity) {
        viewModelScope.launch {
            providerDao.deactivateAllProviders()
            providerDao.activateProvider(provider.id)
            val activatedProvider = provider.copy(isActive = true)
            currentProvider = activatedProvider
            applyProviderSettings(activatedProvider)
            fetchModels()
            com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showSuccess(
                message = "Provider switched",
                avatar = "🔄"
            )
        }
    }
    
    private fun applyProviderSettings(provider: ProviderEntity) {
        baseUrl = provider.baseUrl
        apiKey = provider.apiKey
    }
    
    fun createProvider(
        name: String,
        baseUrl: String,
        apiKey: String,
        icon: String? = null
    ) {
        viewModelScope.launch {
            val provider = ProviderEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                baseUrl = baseUrl,
                apiKey = apiKey,
                icon = icon,
                isActive = providers.isEmpty()
            )
            providerDao.insertProvider(provider)
            if (provider.isActive) {
                currentProvider = provider
                applyProviderSettings(provider)
                fetchModels()
            }
            com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showSuccess(
                message = "Provider created",
                avatar = "➕"
            )
        }
    }
    
    fun updateProvider(provider: ProviderEntity) {
        viewModelScope.launch {
            providerDao.updateProvider(provider)
            if (currentProvider?.id == provider.id) {
                currentProvider = provider
                applyProviderSettings(provider)
                fetchModels()
            }
            com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showSuccess(
                message = "Provider updated",
                avatar = "🔧"
            )
        }
    }
    
    fun deleteProvider(providerId: String) {
        viewModelScope.launch {
            providerDao.deleteProvider(providerId)
            if (currentProvider?.id == providerId) {
                currentProvider = ChatProviderSelector.fallbackAfterDelete(providerId, providers)
                currentProvider?.let { 
                    applyProviderSettings(it)
                    fetchModels()
                }
            }
            com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showSuccess(
                message = "Provider deleted",
                avatar = "🗑️"
            )
        }
    }
    
    fun saveApiKey(value: String) {
        apiKey = value
        persistSetting { setApiKey(value) }
    }
    
    fun saveBaseUrl(value: String) {
        baseUrl = value
        persistSetting { setBaseUrl(value) }
    }
    
    fun saveModel(value: String) {
        model = value
        persistSetting { setModel(value) }
    }

    fun updateThemeMode(value: String) {
        themeMode = value
        persistSetting { setThemeMode(value) }
    }

    fun updateWallpaperUri(value: String?) {
        wallpaperUri = value
        persistSetting { setWallpaperUri(value) }
    }

    // ========== Toolbox Configuration Update Methods ==========
    
    fun updateThinkingBudget(value: Int) {
        thinkingBudget = value
        persistSetting { setThinkingBudget(value) }
    }

    fun getThinkingLevelName(): String {
        return when {
            thinkingBudget == 0 -> "Off"
            thinkingBudget == -1 -> "Auto"
            thinkingBudget <= 2048 -> "Low"
            thinkingBudget <= 10000 -> "Med"
            else -> "High"
        }
    }

    fun cycleThinkingLevel() {
        val nextBudget = when {
            thinkingBudget == 0 -> 1024
            thinkingBudget == -1 -> 0
            thinkingBudget <= 2048 -> 8192
            thinkingBudget <= 10000 -> 32000
            else -> -1
        }
        updateThinkingBudget(nextBudget)
        val name = when (nextBudget) {
            0 -> "Thinking: Off"
            -1 -> "Thinking: Auto Dynamic"
            1024 -> "Thinking: Low"
            8192 -> "Thinking: Medium"
            else -> "Thinking: High"
        }
        com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showSuccess(
            message = name,
            avatar = "🧠"
        )
    }
    
    fun updateWebSearchEnabled(value: Boolean) {
        webSearchEnabled = value
        persistSetting { setWebSearchEnabled(value) }
    }
    
    fun updateSearchProvider(value: Int) {
        searchProvider = value
        persistSetting { setSearchProvider(value) }
    }
    
    fun updateStreamEnabled(value: Boolean) {
        streamEnabled = value
        persistSetting { setStreamEnabled(value) }
    }
    
    fun updateContextSize(value: Int) {
        contextSize = value
        persistSetting { setContextSize(value) }
    }

    fun getContextSizeDisplayText(): String {
        return when {
            contextSize <= 0 -> "Max (Unlimited)"
            contextSize <= 128 -> "$contextSize msgs"
            contextSize >= 1000 -> "${contextSize / 1024}K tokens"
            else -> "$contextSize tokens"
        }
    }

    private fun shouldSendOpenAiOnlyOptions(effectiveBaseUrl: String): Boolean {
        return ChatRequestBuilder.shouldSendOpenAiOnlyOptions(effectiveBaseUrl)
    }

    private fun reasoningEffortOrNull(effectiveBaseUrl: String): String? {
        val modelLower = model.lowercase()
        val isReasoningModel = modelLower.startsWith("o1") || 
                               modelLower.startsWith("o3") || 
                               modelLower.contains("reasoner") || 
                               modelLower.contains("r1")
        if (!isReasoningModel) return null
        return ChatRequestBuilder.reasoningEffortOrNull(effectiveBaseUrl, thinkingBudget)
    }

    private fun beginRequestTimeout() {
        requestTimeoutJob?.cancel()
        requestTimeoutJob = viewModelScope.launch {
            delay(30000)
            if (isLoading) {
                Log.w(TAG, "Request timeout, cancelling active generation")
                cancelGeneration()
                showErrorMessage("Request timed out, please retry")
            }
        }
    }

    private fun finishGeneration() {
        requestTimeoutJob?.cancel()
        requestTimeoutJob = null
        isLoading = false
    }
    
    // ========== Dynamic Island Configuration Update Methods ==========
    
    fun updateDynamicIslandEnabled(value: Boolean) {
        dynamicIslandEnabled = value
        persistSetting { setDynamicIslandEnabled(value) }
    }
    
    fun updateLoginNotificationMode(value: String) {
        loginNotificationMode = value
        persistSetting { setLoginNotificationMode(value) }
    }
    
    fun updateDynamicIslandDuration(value: Int) {
        dynamicIslandDuration = value
        persistSetting { setDynamicIslandDuration(value) }
    }
    
    fun updateShowTokenCount(value: Boolean) {
        showTokenCount = value
        persistSetting { setShowTokenCount(value) }
    }
    
    fun updateShowElapsedTime(value: Boolean) {
        showElapsedTime = value
        persistSetting { setShowElapsedTime(value) }
    }
    
    fun sendMessage(content: String) {
        val preparedInput = ChatInputPreparer.prepare(
            content = content,
            imageUri = selectedImageUri?.toString()
        ) ?: return

        if (preparedInput.hasImage) {
            selectedImageUri = null
        }
        
        val effectiveApiKey = currentProvider?.apiKey ?: apiKey
        
        if (effectiveApiKey.isBlank()) {
            showErrorMessage("Please configure a provider first")
            return
        }
        
        if (model.isBlank()) {
            showErrorMessage("Please select a model first")
            return
        }
        
        val conversationId = currentConversationId ?: return
        
        Log.d(TAG, "sendMessage: user message queued, hasImage=${preparedInput.hasImage}, length=${preparedInput.plainText.length}")
        
        val userMessageId = UUID.randomUUID().toString()
        val userMessage = UiMessage(id = userMessageId, role = "user", content = preparedInput.finalContent)
        messages.add(userMessage)
        
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.insertMessage(MessageEntity(
                id = userMessageId,
                conversationId = conversationId,
                role = "user",
                content = preparedInput.finalContent
            ))
            
            if (transientConversationIds.contains(conversationId)) {
                Log.d(TAG, "Persisting transient conversation: $conversationId")
                transientConversationIds.remove(conversationId)
                
                val conversation = ConversationEntity(
                    id = conversationId,
                    title = preparedInput.title,
                    assistantId = currentAssistant?.id,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                conversationDao.insertConversation(conversation)
                withContext(Dispatchers.Main) {
                    currentConversationTitle = conversation.title
                }
                
                pendingSystemPrompt?.let { prompt ->
                    if (prompt.isNotBlank()) {
                         messageDao.insertMessage(MessageEntity(
                            id = UUID.randomUUID().toString(),
                            conversationId = conversationId,
                            role = "system",
                            content = prompt
                        ))
                    }
                    pendingSystemPrompt = null
                }
            } else {
                if (messages.size <= 1) {
                    val newTitle = preparedInput.title
                    conversationDao.updateConversationTitle(conversationId, newTitle)
                    withContext(Dispatchers.Main) {
                        currentConversationTitle = newTitle
                    }
                }
                conversationDao.updateConversationTimestamp(conversationId, System.currentTimeMillis())
            }
        }
        
        initiateAiResponse(conversationId)
    }

    fun initiateAiResponse(conversationId: String) {
        viewModelScope.launch(Dispatchers.Main) {
            if (model.isBlank()) {
                showErrorMessage("Please select a model first")
                finishGeneration()
                return@launch
            }

            if (isLoading) {
                Log.w(TAG, "isLoading was true, cancelling previous request before starting a new one")
                cancelGeneration()
            }
            
            val useStream = this@ChatViewModel.streamEnabled
            isLoading = true
            
            val aiMessageId = UUID.randomUUID().toString()
            val initialMessage = UiMessage(
                id = aiMessageId,
                role = "assistant",
                content = "",
                thinkingContent = "",
                isStreaming = useStream,
                model = model
            )
            
            messages.add(initialMessage)
            Log.d(TAG, "Added AI bubble: $aiMessageId, model=$model")
            
            clearError()
            
            if (useStream) {
                callStreamingApiWithEventSource(aiMessageId, conversationId)
            } else {
                callNonStreamingApi(aiMessageId, conversationId)
            }
            beginRequestTimeout()
        }
    }
    
    fun stopStreaming() {
        cancelGeneration()
    }
    
    private fun callNonStreamingApi(aiMessageId: String, conversationId: String) {
        viewModelScope.launch {
            try {
                val requestMessages = buildApiMessages(messages, aiMessageId)
                val effectiveBaseUrl = currentProvider?.baseUrl ?: baseUrl
                val effectiveApiKey = currentProvider?.apiKey ?: apiKey
                val reasoningEffort = reasoningEffortOrNull(effectiveBaseUrl)
                
                val requestJson = ChatRequestBuilder.buildRequestJson(
                    model = model,
                    messages = requestMessages,
                    stream = false,
                    temperature = temperature,
                    topP = topP,
                    maxTokens = maxTokens,
                    reasoningEffort = reasoningEffort,
                    thinkingBudget = thinkingBudget,
                    includeStreamOptions = false,
                    webSearch = webSearchEnabled,
                    effectiveBaseUrl = effectiveBaseUrl
                )
                
                val requestBody = json.encodeToString(JsonObject.serializer(), requestJson)
                Log.d(TAG, "Non-streaming request prepared, messages=${requestMessages.size}, bodyLength=${requestBody.length}")

                val parsed = chatApiClient.executeNonStreaming(
                    baseUrl = effectiveBaseUrl,
                    apiKey = effectiveApiKey,
                    requestBody = requestBody
                )
                val contentStr = parsed.content
                val reasoningStr = parsed.reasoningContent.orEmpty()

                Log.d(TAG, "Parsed result - content length: ${contentStr.length}, reasoning length: ${reasoningStr.length}")

                finishGeneration()
                val index = messages.indexOfFirst { it.id == aiMessageId }
                if (index >= 0) {
                    val safeContent = if (contentStr.isEmpty() && reasoningStr.isEmpty()) "⚠️ API returned empty response" else contentStr
                    messages[index] = messages[index].copy(
                        content = safeContent,
                        thinkingContent = reasoningStr.takeIf { it.isNotEmpty() },
                        isStreaming = false
                    )

                    messageDao.insertMessage(MessageEntity(
                        id = aiMessageId,
                        conversationId = conversationId,
                        role = "assistant",
                        content = safeContent,
                        thinkingContent = reasoningStr.takeIf { it.isNotEmpty() },
                        model = model
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Non-streaming request failed", e)
                finishGeneration()
                val errorMsg = "Request failed: ${e.message}"
                showErrorMessage(errorMsg)
                
                val index = messages.indexOfFirst { it.id == aiMessageId }
                if (index >= 0) {
                    messages[index] = messages[index].copy(
                        content = "⚠️ $errorMsg",
                        isStreaming = false
                    )
                }
            }
        }
    }
    
    private fun buildApiMessages(history: List<UiMessage>, aiMessageId: String? = null): List<ChatMessage> {
        Log.d(TAG, "Building API messages from history of size ${history.size}, excluding: $aiMessageId")
        return ChatRequestBuilder.buildMessages(
            history = history,
            aiMessageId = aiMessageId,
            contextSize = contextSize,
            systemPrompt = currentAssistant?.systemPrompt,
            webSearch = webSearchEnabled,
            imageBase64Loader = { uriString ->
                try {
                    FileUtils.uriToBase64(getApplication(), Uri.parse(uriString))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load image from URI: $uriString", e)
                    null
                }
            }
        )
    }

    private fun callStreamingApiWithEventSource(aiMessageId: String, conversationId: String, retryAttempt: Int = 0) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "callStreamingApiWithEventSource: using baseUrl=$baseUrl, model=$model, contextSize=$contextSize, retry=$retryAttempt")
                
                val effectiveBaseUrl = currentProvider?.baseUrl ?: baseUrl
                val effectiveApiKey = currentProvider?.apiKey ?: apiKey
                val reasoningEffort = reasoningEffortOrNull(effectiveBaseUrl)

                val (requestBody, messagesWithSystem) = withContext(Dispatchers.Default) {
                    val messagesWithSystem = buildApiMessages(messages, aiMessageId)
                    val requestJson = ChatRequestBuilder.buildRequestJson(
                        model = model,
                        messages = messagesWithSystem,
                        stream = true,
                        temperature = temperature,
                        topP = topP,
                        maxTokens = maxTokens,
                        reasoningEffort = reasoningEffort,
                        thinkingBudget = thinkingBudget,
                        includeStreamOptions = false,
                        webSearch = webSearchEnabled,
                        effectiveBaseUrl = effectiveBaseUrl
                    )
                    val body = json.encodeToString(JsonObject.serializer(), requestJson)
                    Pair(body, messagesWithSystem)
                }
                
                Log.d(TAG, "Streaming request prepared, messages=${messagesWithSystem.size}, bodyLength=${requestBody.length}")
                
                val streamAccumulator = ChatStreamAccumulator()
                var pendingUiUpdate = false

                // Batch stream frame pacer: delivers incoming token batches every 60ms to eliminate UI thread lag
                val streamPacerJob = viewModelScope.launch(Dispatchers.Main) {
                    while (isActive && !streamAccumulator.isFinished) {
                        delay(60L)
                        if (pendingUiUpdate) {
                            pendingUiUpdate = false
                            val snapshot = streamAccumulator.snapshot()
                            val index = messages.indexOfFirst { it.id == aiMessageId }
                            if (index >= 0) {
                                messages[index] = messages[index].copy(
                                    content = snapshot.content,
                                    thinkingContent = snapshot.thinkingContent
                                )
                            }
                        }
                    }
                }

                fun completeStreamingResponse(cancelEventSource: Boolean = false) {
                    streamPacerJob.cancel()
                    if (!streamAccumulator.finish()) return
                    val snapshot = streamAccumulator.snapshot()

                    if (cancelEventSource) {
                        currentEventSource?.cancel()
                    }
                    currentEventSource = null

                    viewModelScope.launch {
                        finishGeneration()
                        
                        val index = messages.indexOfFirst { it.id == aiMessageId }
                        if (index >= 0) {
                            messages[index] = messages[index].copy(
                                content = snapshot.content,
                                thinkingContent = snapshot.thinkingContent,
                                isStreaming = false
                            )
                        }

                        if (snapshot.hasContent) {
                            messageDao.insertMessage(MessageEntity(
                                id = aiMessageId,
                                conversationId = conversationId,
                                role = "assistant",
                                content = snapshot.content,
                                thinkingContent = snapshot.thinkingContent,
                                model = model
                            ))
                        } else {
                            if (index >= 0) {
                                val targetUrl = chatApiClient.buildUrl(effectiveBaseUrl, "chat/completions")
                                val debugMsg = streamAccumulator.getDebugSummary(targetUrl, model)
                                messages[index] = messages[index].copy(content = debugMsg, isStreaming = false)
                            }
                        }
                    }
                }

                currentEventSource = chatApiClient.startStreaming(
                    baseUrl = effectiveBaseUrl,
                    apiKey = effectiveApiKey,
                    requestBody = requestBody,
                    callback = object : ChatStreamCallback {
                        override fun onOpen(responseCode: Int) {
                            Log.d(TAG, "SSE onOpen: $responseCode")
                            streamAccumulator.lastHttpCode = responseCode
                            streamingTokenCount = 0
                        }

                        override fun onRawEvent(data: String) {
                            streamAccumulator.recordEvent(data)
                        }

                        override fun onDelta(delta: ChatStreamDelta) {
                            if (streamAccumulator.isFinished) return

                            delta.errorMessage?.let { errorMsg ->
                                Log.e(TAG, "Streaming API error: $errorMsg")
                                return
                            }

                            val snapshot = streamAccumulator.applyDelta(delta) ?: return
                            streamingTokenCount = snapshot.tokenCount
                            pendingUiUpdate = true
                        }

                        override fun onDone() {
                            Log.d(TAG, "SSE stream completed with [DONE], content length: ${streamAccumulator.snapshot().contentLength}")
                            completeStreamingResponse(cancelEventSource = true)
                        }

                        override fun onClosed() {
                            Log.d(TAG, "SSE onClosed, final content length: ${streamAccumulator.snapshot().contentLength}")
                            completeStreamingResponse()
                        }

                        override fun onFailure(message: String, throwable: Throwable?, responseCode: Int?) {
                            streamPacerJob.cancel()
                            currentEventSource = null
                            if (streamAccumulator.isStreamResetSuccess(message)) {
                                Log.w(TAG, "Stream reset ignored, treating as success (content length: ${streamAccumulator.snapshot().contentLength})")
                                completeStreamingResponse()
                                return
                            }

                            // Auto-retry on 429 rate limit with exponential backoff
                            val maxRetries = 3
                            if (responseCode == 429 && retryAttempt < maxRetries) {
                                val backoffMs = (2000L shl retryAttempt) + (0..1000).random()
                                val nextAttempt = retryAttempt + 1
                                Log.w(TAG, "Rate limited (429), retrying in ${backoffMs}ms (attempt $nextAttempt/$maxRetries)")
                                
                                viewModelScope.launch {
                                    delay(backoffMs)
                                    callStreamingApiWithEventSource(aiMessageId, conversationId, nextAttempt)
                                }
                                return
                            }

                            if (!streamAccumulator.finish()) return

                            Log.e(TAG, "SSE onFailure: $message, response: $responseCode", throwable)

                            viewModelScope.launch {
                                finishGeneration()

                                val index = messages.indexOfFirst { it.id == aiMessageId }
                                if (index >= 0) {
                                    val currentMsg = messages[index]
                                    if (currentMsg.content.isBlank()) {
                                        val displayMsg = when (responseCode) {
                                            429 -> "⏳ The provider is currently rate limited or busy. Please wait a moment and try again."
                                            401 -> "🔑 Invalid API key. Please check your provider settings."
                                            404 -> "🔍 Model '$model' was not found on this endpoint."
                                            else -> "⚠️ Unable to get a response from $model. ($message)"
                                        }
                                        messages[index] = currentMsg.copy(
                                            content = displayMsg,
                                            isStreaming = false
                                        )
                                    } else {
                                        messages[index] = currentMsg.copy(isStreaming = false)
                                    }
                                }
                            }
                        }

                        override fun onParseError(error: Exception) {
                            Log.w(TAG, "Failed to parse SSE chunk", error)
                        }
                    }
                )
                Log.d(TAG, "SSE EventSource created successfully for $aiMessageId")
            } catch (e: Exception) {
                Log.e(TAG, "Streaming request setup failed", e)
                finishGeneration()
                val errorMsg = "Failed to initialize streaming: ${e.message}"
                showErrorMessage(errorMsg)

                val index = messages.indexOfFirst { it.id == aiMessageId }
                if (index >= 0) {
                    messages[index] = messages[index].copy(
                        content = "⚠️ $errorMsg",
                        isStreaming = false
                    )
                }
            }
        }
    }
    
    private fun showErrorMessage(message: String) {
        error = message
        showError = true
        
        viewModelScope.launch {
            delay(3000)
            if (error == message) {
                showError = false
                delay(300)
                error = null
            }
        }
    }
    
    fun clearError() {
        showError = false
        viewModelScope.launch {
            delay(300)
            error = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentEventSource?.cancel()
    }

    fun saveImageToGallery(imageUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val filename = "Fluxhub_${System.currentTimeMillis()}.jpg"
                
                val request = Request.Builder().url(imageUrl).build()
                val response = client.newCall(request).execute()
                val bytes = response.body?.bytes() ?: throw Exception("Download failed")
                
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Fluxhub")
                    }
                }
                
                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                
                uri?.let {
                    resolver.openOutputStream(it)?.use { stream ->
                        stream.write(bytes)
                    }
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    
                    withContext(Dispatchers.Main) {
                        com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showSuccess(
                            message = "Image saved to gallery",
                            avatar = "🖼️"
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    com.liquidglass.fluxhub.chat.ui.components.DynamicIslandController.showError(
                        message = "Failed to save image"
                    )
                }
            }
        }
    }
}
