package com.naira.assistant.core.tools.definition

internal fun universalToolDescription(
    purpose: String,
    trigger: String,
    notFor: String,
    prerequisite: String = "None.",
): String = """
    - **Purpose:** $purpose
    - **Mandatory trigger:** $trigger
    - **No guessing:** When this tool applies, answer only from its result; never infer missing data or claim success without confirmation.
    - **Do not use (negative rule):** $notFor
    - **Prerequisites:** $prerequisite
""".trimIndent()
