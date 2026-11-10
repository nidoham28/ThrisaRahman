package com.naira.assistant.di

import android.content.Context
import com.naira.assistant.core.sound.SpeechToText
import com.naira.assistant.core.sound.SpeechToTextFactory
import com.naira.assistant.core.sound.TextToSpeech
import com.naira.assistant.domain.AccountSettingsStorage
import com.naira.assistant.domain.sttLanguageTag

/**
 * App-wide dependencies.
 */
object AppModule {

    fun createSpeechToText(context: Context): SpeechToText =
        SpeechToTextFactory.create(
            context.applicationContext,
            AccountSettingsStorage(context.applicationContext).getLanguage().sttLanguageTag(),
        )

    fun createTextToSpeech(context: Context): TextToSpeech =
        TextToSpeech(
            context.applicationContext,
            AccountSettingsStorage(context.applicationContext).getLanguage().sttLanguageTag(),
        )
}
