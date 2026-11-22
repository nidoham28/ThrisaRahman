package com.naira.assistant.core.prompt.character

sealed class Assistant(val name: String) {
    object Naira  : Assistant("Naira")
    object Venom  : Assistant("Venom")
    object Jervis : Assistant("Jervis")
}

object AssistantRegistry {
    val characters: List<Assistant> = listOf(
        Assistant.Naira,
        Assistant.Venom,
        Assistant.Jervis
    )
}
