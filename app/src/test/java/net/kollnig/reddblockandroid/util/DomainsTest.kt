package net.kollnig.reddblockandroid.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainsTest {
    @Test
    fun acceptsPlainDomains() {
        assertEquals("pinterest.com", normaliseDomain("pinterest.com"))
        assertEquals("pinterest.com", normaliseDomain("pinterest.com "))
        assertEquals("pinterest.com", normaliseDomain("  Pinterest.COM"))
    }

    @Test
    fun stripsSchemeWwwAndPath() {
        assertEquals("pinterest.com", normaliseDomain("www.pinterest.com"))
        assertEquals("pinterest.com", normaliseDomain("https://www.pinterest.com/"))
        assertEquals("reddit.com", normaliseDomain("http://reddit.com/r/android?x=1"))
        assertEquals("example.co.uk", normaliseDomain("example.co.uk:8080"))
        assertEquals("news.ycombinator.com", normaliseDomain("news.ycombinator.com."))
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull(normaliseDomain(""))
        assertNull(normaliseDomain("   "))
        assertNull(normaliseDomain("pinterest"))
        assertNull(normaliseDomain("pin terest.com"))
        assertNull(normaliseDomain("-pinterest.com"))
    }
}
