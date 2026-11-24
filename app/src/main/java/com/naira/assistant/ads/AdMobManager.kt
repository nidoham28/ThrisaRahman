package com.naira.assistant.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.naira.assistant.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

object AdMobManager {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val consentRequestStarted = AtomicBoolean(false)
    private val initializationStarted = AtomicBoolean(false)
    private val _canShowAds = MutableStateFlow(false)
    private val _isInitialized = MutableStateFlow(false)
    private val _privacyOptionsRequired = MutableStateFlow(false)

    @Volatile
    private var consentInformation: ConsentInformation? = null

    @Volatile
    private var currentActivityReference: WeakReference<Activity>? = null

    val canShowAds = _canShowAds.asStateFlow()
    val isInitialized = _isInitialized.asStateFlow()
    val privacyOptionsRequired = _privacyOptionsRequired.asStateFlow()

    fun requestConsentAndInitialize(activity: Activity) {
        currentActivityReference = WeakReference(activity)
        if (!BuildConfig.ADMOB_CONFIGURED || !consentRequestStarted.compareAndSet(false, true)) return

        val appId = BuildConfig.ADMOB_APP_ID.trim()
        if (appId.isBlank()) return
        val appContext = activity.applicationContext

        val information = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = information
        _canShowAds.value = false
        _privacyOptionsRequired.value = false

        information.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                updatePrivacyOptionsState(information)
                val consentActivity = currentActivity()
                if (consentActivity == null) {
                    consentRequestStarted.set(false)
                } else {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(consentActivity) {
                        updateConsentState(information)
                        initializeIfAllowed(appContext, information, appId)
                    }
                }
            },
            {
                updateConsentState(information)
                initializeIfAllowed(appContext, information, appId)
            },
        )

        // UMP can reuse a valid consent decision from the previous session while
        // the current consent refresh is in flight. Initialization alone does
        // not request ads; the banner remains hidden until the form callback.
        initializeIfAllowed(appContext, information, appId)
    }

    fun showPrivacyOptions(context: Context) {
        val activity = context.findActivity() ?: return
        val information = consentInformation ?: return
        if (information.privacyOptionsRequirementStatus !=
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        ) {
            updatePrivacyOptionsState(information)
            return
        }

        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            updateConsentState(information)
            initializeIfAllowed(
                activity.applicationContext,
                information,
                BuildConfig.ADMOB_APP_ID.trim(),
            )
        }
    }

    private fun updateConsentState(information: ConsentInformation) {
        updatePrivacyOptionsState(information)
        _canShowAds.value = information.canRequestAds()
    }

    private fun updatePrivacyOptionsState(information: ConsentInformation) {
        _privacyOptionsRequired.value = information.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun currentActivity(): Activity? = currentActivityReference
        ?.get()
        ?.takeUnless { it.isFinishing || it.isDestroyed }

    private fun initializeIfAllowed(
        context: Context,
        information: ConsentInformation,
        appId: String,
    ) {
        if (!BuildConfig.ADMOB_CONFIGURED || !information.canRequestAds() || appId.isBlank()) return
        if (!initializationStarted.compareAndSet(false, true)) return

        backgroundScope.launch {
            try {
                MobileAds.initialize(
                    context.applicationContext,
                    InitializationConfig.Builder(appId).build(),
                )
                _isInitialized.value = true
            } catch (cancellation: CancellationException) {
                initializationStarted.set(false)
                throw cancellation
            } catch (error: Exception) {
                initializationStarted.set(false)
                Log.e(TAG, "Google Mobile Ads SDK initialization failed", error)
            }
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private const val TAG = "AdMobManager"
}
