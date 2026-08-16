package com.liquidglass.fluxhub.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.room.withTransaction

@Serializable
data class BackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val conversations: List<ConversationEntity>,
    val messages: List<MessageEntity>,
    val assistants: List<AssistantEntity>,
    val providers: List<ProviderEntity>,
    // Includes key settings
    val settings: AppSettingsBackup? = null
)

@Serializable
data class AppSettingsBackup(
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val model: String? = null,
    val defaultModel: String? = null,
    val thinkingBudget: Int? = null
)

class DataRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val settingsRepository = SettingsRepository(context)
    
    // Permissive JSON serializer/deserializer
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun exportData(): String = withContext(Dispatchers.IO) {
        val conversations = database.conversationDao().getAllConversationsSync()
        val messages = database.messageDao().getAllMessagesSync()
        val assistants = database.assistantDao().getAllAssistantsSync()
        val providers = database.providerDao().getAllProvidersSync().map { provider ->
            provider.copy(apiKey = "")
        }
        
        // Do not include secrets in portable backups. Users can re-enter keys after import.
        val settings = AppSettingsBackup(
            baseUrl = settingsRepository.baseUrl.first(),
            model = settingsRepository.model.first(),
            defaultModel = settingsRepository.defaultModel.first(),
            thinkingBudget = settingsRepository.thinkingBudget.first()
        )
        
        val backupData = BackupData(
            conversations = conversations,
            messages = messages,
            assistants = assistants,
            providers = providers,
            settings = settings
        )
        
        return@withContext json.encodeToString(backupData)
    }
    
    suspend fun importData(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            } ?: return@withContext false
            
            val backupData = json.decodeFromString<BackupData>(content)
            
            // Restore data (clean and overwrite strategy for consistency)
            database.withTransaction {
                // Clear existing data
                database.messageDao().deleteAllMessages()
                database.conversationDao().deleteAllConversations()
                database.assistantDao().deleteAllAssistants()
                database.providerDao().deleteAllProviders()
                
                // Insert backup data
                database.assistantDao().insertAssistants(backupData.assistants)
                database.providerDao().insertProviders(backupData.providers)
                database.conversationDao().insertConversations(backupData.conversations)
                database.messageDao().insertMessages(backupData.messages)
            }
            
            // Restore settings
            backupData.settings?.let { s ->
                s.apiKey?.let { settingsRepository.setApiKey(it) }
                s.baseUrl?.let { settingsRepository.setBaseUrl(it) }
                s.model?.let { settingsRepository.setModel(it) }
                s.defaultModel?.let { settingsRepository.setDefaultModel(it) }
                s.thinkingBudget?.let { settingsRepository.setThinkingBudget(it) }
            }
            
            return@withContext true
        } catch (e: Exception) {
            Log.e("DataRepository", "Import failed", e)
            return@withContext false
        }
    }
}
