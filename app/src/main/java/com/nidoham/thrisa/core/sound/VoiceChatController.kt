package com.nidoham.thrisa.core.sound

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** One assistant chat bubble as seen by the voice loop. */
data class AssistantReply(
    val text: String,
    val isError: Boolean = false,
)

/**
 * The chat surface the voice loop drives — the seam between the sound layer
 * and the chat implementation. The presentation layer adapts its
 * AssistantViewModel to this; core.sound never depends on the UI layer.
 */
interface VoiceChatController {

    /** Emits the latest assistant reply (null before the first one). */
    val replies: Flow<AssistantReply?>

    /** Null = idle; non-null = a one-line "what is happening right now" status. */
    val busyStatus: StateFlow<String?>

    /** Sends a user utterance through the chat; the reply streams into [replies]. */
    fun send(text: String)

    /** Aborts the in-flight request. */
    fun cancelCurrent()
}
