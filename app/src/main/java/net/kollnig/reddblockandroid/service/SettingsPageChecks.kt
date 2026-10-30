package net.kollnig.reddblockandroid.service

import net.kollnig.reddblockandroid.util.SettingsAccessWindow

/** Coalesces content events without losing the final update or the access-window expiry. */
internal class SettingsPageChecks(
    private val nowMillis: () -> Long,
    private val postDelayed: (Runnable, Long) -> Unit,
    private val removeCallbacks: (Runnable) -> Unit,
    private val accessWindow: SettingsAccessWindow,
    private val requiredWordCount: () -> Int,
    private val canCheck: () -> Boolean,
    private val checkActivePage: () -> Boolean,
) {
    private var lastCheckMillis: Long? = null
    private var pendingAtMillis: Long? = null
    private val recheck = Runnable {
        pendingAtMillis = null
        request(checkImmediately = true)
    }

    fun request(checkImmediately: Boolean): Boolean {
        if (!canCheck()) {
            cancel()
            return false
        }
        val accessRemaining = if (accessWindow.remainingMillis() > 0)
            accessWindow.remainingMillis(requiredWordCount()) else 0L
        if (accessRemaining > 0) {
            scheduleAfter(accessRemaining)
            return false
        }
        val now = nowMillis()
        val lastCheck = lastCheckMillis
        if (!checkImmediately && lastCheck != null && now - lastCheck < THROTTLE_MS) {
            scheduleAfter(THROTTLE_MS - (now - lastCheck))
            return false
        }
        cancel()
        lastCheckMillis = now
        // The callback must inspect the current active window, never an event's old source.
        return checkActivePage()
    }

    private fun scheduleAfter(delayMillis: Long) {
        val due = nowMillis() + delayMillis
        if (pendingAtMillis?.let { it <= due } == true) return
        cancel()
        pendingAtMillis = due
        postDelayed(recheck, delayMillis)
    }

    fun cancel() {
        removeCallbacks(recheck)
        pendingAtMillis = null
    }

    private companion object {
        const val THROTTLE_MS = 500L
    }
}
