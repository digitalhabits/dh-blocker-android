package net.kollnig.reddblockandroid.service

import net.kollnig.reddblockandroid.util.SettingsAccessWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPageChecksTest {
    @Test
    fun latePageContentIsCheckedEvenWhenEventsStop() {
        val fixture = Fixture()
        fixture.checks.request(checkImmediately = true)
        fixture.advanceTo(100)
        fixture.protectedPageShowing = true
        assertFalse(fixture.checks.request(checkImmediately = false))

        fixture.advanceTo(499)
        assertEquals(0, fixture.redirects)
        fixture.advanceTo(500)
        assertEquals(1, fixture.redirects)
        assertEquals(0, fixture.pending.size)
    }

    @Test
    fun contentBurstCoalescesWithoutPushingBackTheDeadline() {
        val fixture = Fixture()
        fixture.checks.request(checkImmediately = true)
        fixture.protectedPageShowing = true
        listOf(100L, 200L, 450L).forEach {
            fixture.advanceTo(it)
            fixture.checks.request(checkImmediately = false)
            assertEquals(1, fixture.pending.size)
        }
        fixture.advanceTo(500)
        assertEquals(1, fixture.redirects)
    }

    @Test
    fun immediateStateCheckCancelsTheOlderTrailingCheck() {
        val fixture = Fixture()
        fixture.checks.request(checkImmediately = true)
        fixture.advanceTo(100)
        fixture.checks.request(checkImmediately = false)
        fixture.advanceTo(200)
        fixture.protectedPageShowing = true
        assertTrue(fixture.checks.request(checkImmediately = true))
        assertTrue(fixture.pending.isEmpty())
        fixture.advanceTo(600)
        assertEquals(1, fixture.redirects)
    }

    @Test
    fun trailingCheckUsesTheCurrentPageAndCannotCoverTheGate() {
        val fixture = Fixture()
        fixture.checks.request(checkImmediately = true)
        fixture.advanceTo(100)
        fixture.protectedPageShowing = true
        fixture.checks.request(checkImmediately = false)
        // The gate or another app is now foreground; the queued event is obsolete.
        fixture.protectedPageShowing = false
        fixture.advanceTo(500)
        assertEquals(0, fixture.redirects)
    }

    @Test
    fun lockingTheScreenPreventsDeferredRedirects() {
        val fixture = Fixture()
        fixture.checks.request(checkImmediately = true)
        fixture.advanceTo(100)
        fixture.protectedPageShowing = true
        fixture.checks.request(checkImmediately = false)
        fixture.canCheck = false
        fixture.advanceTo(500)
        assertEquals(0, fixture.redirects)
        assertTrue(fixture.pending.isEmpty())
    }

    @Test
    fun serviceLifecycleCancellationRemovesPendingWork() {
        val fixture = Fixture()
        fixture.checks.request(checkImmediately = true)
        fixture.advanceTo(100)
        fixture.checks.request(checkImmediately = false)
        fixture.checks.cancel()
        fixture.protectedPageShowing = true
        fixture.advanceTo(500)
        assertEquals(1, fixture.inspections)
        assertTrue(fixture.pending.isEmpty())
    }

    @Test
    fun accessExpiresOnAQuietProtectedPageWithoutPausingForMoreEvents() {
        val fixture = Fixture()
        fixture.access.grant(wordCount = 30)
        fixture.protectedPageShowing = true
        listOf(100L, 200L, 59_999L).forEach {
            fixture.advanceTo(it)
            assertFalse(fixture.checks.request(checkImmediately = true))
            assertEquals(1, fixture.pending.size)
        }
        assertEquals(0, fixture.redirects)
        fixture.advanceTo(60_000)
        assertEquals(1, fixture.redirects)
    }

    @Test
    fun expiryCannotRedirectAnotherForegroundAppOrALockedScreen() {
        val anotherApp = Fixture()
        anotherApp.access.grant(wordCount = 30)
        anotherApp.advanceTo(60_000)
        assertEquals(0, anotherApp.redirects)

        val locked = Fixture()
        locked.access.grant(wordCount = 30)
        locked.protectedPageShowing = true
        locked.canCheck = false
        locked.advanceTo(60_000)
        assertEquals(0, locked.redirects)
    }

    @Test
    fun expirySurvivesATransientWindowAndChecksSettingsWhenItReturns() {
        val fixture = Fixture()
        fixture.access.grant(wordCount = 30)
        fixture.protectedPageShowing = true
        fixture.advanceTo(10_000)
        // An IME/SystemUI window can briefly obscure Settings. It must not
        // discard the expiry callback when Settings then becomes quiet.
        fixture.protectedPageShowing = false
        fixture.advanceTo(11_000)
        fixture.protectedPageShowing = true
        fixture.advanceTo(60_000)
        assertEquals(1, fixture.redirects)
    }

    @Test
    fun aNewStricterScheduleInvalidatesAccessOnTheNextSettingsCheck() {
        val fixture = Fixture()
        fixture.access.grant(wordCount = 10)
        fixture.advanceTo(100)
        fixture.requiredWordCount = 30
        fixture.protectedPageShowing = true
        assertTrue(fixture.checks.request(checkImmediately = true))
        assertEquals(1, fixture.redirects)
        assertEquals(0L, fixture.access.remainingMillis())
        assertTrue(fixture.pending.isEmpty())
    }

    private class Fixture {
        var now = 0L
        var canCheck = true
        var protectedPageShowing = false
        var requiredWordCount = 10
        var redirects = 0
        var inspections = 0
        val pending = mutableMapOf<Runnable, Long>()
        val access = SettingsAccessWindow { now }
        val checks = SettingsPageChecks(
            nowMillis = { now },
            postDelayed = { runnable, delay -> pending[runnable] = now + delay },
            removeCallbacks = { pending.remove(it) },
            accessWindow = access,
            requiredWordCount = { requiredWordCount },
            canCheck = { canCheck },
            checkActivePage = {
                inspections++
                protectedPageShowing.also { if (it) redirects++ }
            },
        )

        init {
            access.onGranted = { checks.request(checkImmediately = true) }
        }

        fun advanceTo(target: Long) {
            while (true) {
                val next = pending.minByOrNull { it.value } ?: break
                if (next.value > target) break
                now = next.value
                pending.remove(next.key)
                next.key.run()
            }
            now = target
        }
    }
}
