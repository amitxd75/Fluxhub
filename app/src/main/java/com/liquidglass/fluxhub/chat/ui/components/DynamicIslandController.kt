package com.liquidglass.fluxhub.chat.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Global Dynamic Island Controller
 * Singleton object to trigger dynamic island notifications across the app.
 */
object DynamicIslandController {
    
    // ========== State ==========
    var state by mutableStateOf(DynamicIslandState.Hidden)
        private set
    
    var title by mutableStateOf("Thinking...")
        private set
    
    var modelName by mutableStateOf<String?>(null)
        private set
    
    var assistantAvatar by mutableStateOf<String?>(null)
        private set
    
    var tokenCount by mutableIntStateOf(0)
        private set
    
    var elapsedSeconds by mutableIntStateOf(0)
        private set
    
    var isCompleted by mutableStateOf(false)
        private set
    
    var isFailed by mutableStateOf(false)
        private set
    
    var successMessage by mutableStateOf("Done")
        private set

    var triggerId by mutableStateOf(0L)
        private set
    
    // ========== Settings (synced from ViewModel) ==========
    var isEnabled by mutableStateOf(true)
    var showTokenCountEnabled by mutableStateOf(true)
    var showElapsedTimeEnabled by mutableStateOf(true)
    var loginNotificationMode by mutableStateOf("first")
    
    // ========== Internal State ==========
    private var timerJob: Job? = null
    private var autoHideJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    
    // ========== Public API ==========
    
    /**
     * Show loading state (e.g. for AI generation)
     */
    fun showLoading(
        title: String = "Thinking...",
        modelName: String? = null,
        avatar: String? = null
    ) {
        if (!isEnabled) return
        
        triggerId++
        this.title = title
        this.modelName = modelName
        this.assistantAvatar = avatar
        this.isCompleted = false
        this.isFailed = false
        this.tokenCount = 0
        this.elapsedSeconds = 0
        this.state = DynamicIslandState.Collapsed
        
        startTimer()
    }
    
    /**
     * Update Token Count
     */
    fun updateTokenCount(count: Int) {
        this.tokenCount = count
    }
    
    /**
     * Increment Token Count
     */
    fun incrementTokenCount(delta: Int = 1) {
        this.tokenCount += delta
    }
    
    /**
     * Show success state
     */
    fun showSuccess(
        message: String = "Done",
        autoHideDelayMs: Long = 3500,
        avatar: String? = null,
        customTitle: String? = null
    ) {
        if (!isEnabled) return
        
        triggerId++
        stopTimer()
        this.successMessage = message
        this.isCompleted = true
        this.isFailed = false
        if (avatar != null) this.assistantAvatar = avatar
        if (customTitle != null) this.title = customTitle
        this.modelName = null
        this.state = DynamicIslandState.Collapsed
        
        scheduleAutoHide(autoHideDelayMs)
    }
    
    /**
     * Show error state
     */
    fun showError(message: String = "Failed", autoHideDelayMs: Long = 3500) {
        if (!isEnabled) return
        
        triggerId++
        stopTimer()
        this.successMessage = message
        this.isCompleted = false
        this.isFailed = true
        this.state = DynamicIslandState.Collapsed
        
        scheduleAutoHide(autoHideDelayMs)
    }
    
    /**
     * Hide Dynamic Island
     */
    fun hide() {
        stopTimer()
        scheduleAutoHide(0)
        this.state = DynamicIslandState.Hidden
    }
    
    /**
     * Expand Dynamic Island to show details
     */
    fun expand() {
        if (state != DynamicIslandState.Hidden) {
            state = DynamicIslandState.Expanded
        }
    }
    
    /**
     * Collapse Dynamic Island
     */
    fun collapse() {
        if (state != DynamicIslandState.Hidden) {
            state = DynamicIslandState.Collapsed
        }
    }
    
    /**
     * Show long press menu
     */
    fun showLongPressMenu() {
        if (state != DynamicIslandState.Hidden) {
            state = DynamicIslandState.LongPressMenu
        }
    }
    
    // ========== Internal Methods ==========
    
    private fun startTimer() {
        timerJob?.cancel()
        elapsedSeconds = 0
        timerJob = scope.launch {
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }
    
    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
    
    private fun scheduleAutoHide(delayMs: Long) {
        autoHideJob?.cancel()
        autoHideJob = scope.launch {
            delay(delayMs)
            hide()
        }
    }
    
    /**
     * Generate DynamicIslandData representing current state
     */
    fun toData(): DynamicIslandData {
        return DynamicIslandData(
            title = title,
            modelName = modelName,
            assistantAvatar = assistantAvatar,
            state = state,
            tokenCount = tokenCount,
            elapsedSeconds = elapsedSeconds,
            isCompleted = isCompleted,
            isFailed = isFailed,
            successMessage = successMessage,
            showTokenCount = showTokenCountEnabled,
            showElapsedTime = showElapsedTimeEnabled,
            triggerId = triggerId
        )
    }
}
