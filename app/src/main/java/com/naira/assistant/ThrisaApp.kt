package com.naira.assistant

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.naira.assistant.domain.LegacySettingsCleanup
import com.naira.assistant.presentation.main.AssistantViewModel
import com.naira.service.util.NewPipeExtractorInstance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ThrisaApp : Application(), ViewModelStoreOwner {

    override val viewModelStore = ViewModelStore()

    private val assistantViewModelFactory = viewModelFactory {
        initializer { AssistantViewModel(this@ThrisaApp) }
    }

    fun assistantViewModel(): AssistantViewModel =
        ViewModelProvider(this, assistantViewModelFactory)[AssistantViewModel::class.java]

    /**
     * Process-lifetime scope for fire-and-forget startup work.
     * [SupervisorJob] so one failure can't cancel unrelated init tasks.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        cleanUpLegacySettings()
        NewPipeExtractorInstance.init()
    }

    override fun onTerminate() {
        viewModelStore.clear()
        super.onTerminate()
    }

    /**
     * Runs once, off the main thread.
     *
     * This is fire-and-forget: any reader that touches settings between
     * [onCreate] and this completing may briefly observe legacy values.
     * If that's unacceptable, expose a `Deferred<Unit>` and await it at the
     * first read site instead of returning immediately.
     */
    private fun cleanUpLegacySettings() {
        appScope.launch {
            try {
                LegacySettingsCleanup.clearUnsupportedSettings(this@ThrisaApp)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Legacy settings cleanup completed")
                }
            } catch (cancellation: CancellationException) {
                // Cooperative cancellation must propagate, never be swallowed.
                throw cancellation
            } catch (error: Throwable) {
                // Log in release too: a silent failure is undebuggable in the wild.
                // TODO: forward to Crashlytics/Sentry once integrated.
                Log.e(TAG, "Legacy settings cleanup failed", error)
            }
        }
    }

    private companion object {
        const val TAG = "ThrisaApp"
    }
}