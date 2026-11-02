package net.kollnig.reddblockandroid.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WordCountTest {
    @Test
    fun clampKeepsCountsWithinTheSliderRange() {
        assertEquals(1, WordCount.clamp(0))
        assertEquals(1, WordCount.clamp(-5))
        assertEquals(30, WordCount.clamp(30))
        assertEquals(50, WordCount.clamp(50))
        assertEquals(50, WordCount.clamp(51))
    }
}
