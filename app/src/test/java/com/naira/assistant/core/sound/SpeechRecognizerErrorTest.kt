package com.naira.assistant.core.sound

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRecognizerErrorTest {

    @Test
    fun `transient recognizer errors are retried`() {
        assertTrue(isRetryableSttError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertTrue(isRetryableSttError(SpeechRecognizer.ERROR_SERVER_DISCONNECTED))
        assertTrue(isRetryableSttError(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS))
        assertFalse(isRetryableSttError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
    }

    @Test
    fun `language failures use the platform error constants`() {
        assertEquals(
            "This language isn't available for speech recognition on this device.",
            sttErrorMessage(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED),
        )
        assertEquals(
            "This language isn't available for speech recognition on this device.",
            sttErrorMessage(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE),
        )
    }

    @Test
    fun `permission and network errors are actionable`() {
        assertEquals(
            "Microphone permission is required.",
            sttErrorMessage(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS),
        )
        assertEquals(
            "Speech recognition needs a network connection.",
            sttErrorMessage(SpeechRecognizer.ERROR_NETWORK),
        )
    }
}
