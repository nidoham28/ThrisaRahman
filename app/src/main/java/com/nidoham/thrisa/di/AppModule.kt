package com.nidoham.thrisa.di

import android.content.Context
import com.nidoham.thrisa.core.sound.SpeechToText
import com.nidoham.thrisa.core.sound.SpeechToTextFactory
import com.nidoham.thrisa.core.sound.TextToSpeech
import com.nidoham.thrisa.domain.AccountSettingsStorage
import com.nidoham.thrisa.domain.sttLanguageTag

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
