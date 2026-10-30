package net.kollnig.reddblockandroid.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsAccessWindowTest {
    @Test
    fun accessLastsExactlyOneMinuteFromCompletion() {
        var elapsed = 5_000L
        val access = SettingsAccessWindow { elapsed }
        assertEquals(0L, access.remainingMillis())
        access.grant(wordCount = 30)
        assertEquals(60_000L, access.remainingMillis())
        elapsed += 59_999
        assertEquals(1L, access.remainingMillis())
        elapsed++
        assertEquals(0L, access.remainingMillis())
    }

    @Test
    fun aNewProcessStartsWithoutAnAccessGrant() {
        val beforeRestart = SettingsAccessWindow { 1_000L }
        beforeRestart.grant(wordCount = 30)
        val afterRestart = SettingsAccessWindow { 1_000L }
        assertEquals(60_000L, beforeRestart.remainingMillis())
        assertEquals(0L, afterRestart.remainingMillis())
    }

    @Test
    fun invalidatingACheaperGrantCannotRestoreItWhenTheStricterScheduleEnds() {
        val access = SettingsAccessWindow { 1_000L }
        access.grant(wordCount = 10)
        assertEquals(0L, access.remainingMillis(requiredWordCount = 30))
        assertEquals(0L, access.remainingMillis(requiredWordCount = 10))
    }
}
