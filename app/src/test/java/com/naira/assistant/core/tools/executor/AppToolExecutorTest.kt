package com.naira.assistant.core.tools.executor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppToolExecutorTest {

    @Test
    fun `ambiguous display name does not select the first app`() {
        val apps = listOf(
            InstalledAppInfo("Google Photos", "com.google.photos", false, true),
            InstalledAppInfo("Google Play", "com.google.play", false, true),
        )

        val result = AppToolExecutor.resolveAppMatch(apps, "Google")

        assertTrue(result is AppMatchResult.Ambiguous)
        assertEquals(2, (result as AppMatchResult.Ambiguous).apps.size)
    }

    @Test
    fun `exact display name wins over broader partial matches`() {
        val exact = InstalledAppInfo("Maps", "com.example.maps", false, true)
        val apps = listOf(
            InstalledAppInfo("Google Maps", "com.google.maps", false, true),
            exact,
        )

        assertEquals(
            AppMatchResult.Found(exact),
            AppToolExecutor.resolveAppMatch(apps, "Maps"),
        )
    }
}
