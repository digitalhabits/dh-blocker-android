package net.kollnig.reddblockandroid.util

/** Process-local permission to visit protected Settings pages, measured by a monotonic clock. */
internal class SettingsAccessWindow(private val nowMillis: () -> Long) {
    private var allowedUntilMillis = 0L
    private var grantedWordCount = 0
    var onGranted: (() -> Unit)? = null

    fun grant(wordCount: Int) {
        grantedWordCount = wordCount
        allowedUntilMillis = nowMillis() + DURATION_MS
        onGranted?.invoke()
    }

    fun remainingMillis(requiredWordCount: Int = 0): Long {
        // A newly active, stricter schedule must not inherit a cheaper Settings grant.
        if (requiredWordCount > grantedWordCount) allowedUntilMillis = 0L
        return (allowedUntilMillis - nowMillis()).coerceAtLeast(0L)
    }

    companion object {
        const val DURATION_MS = 60_000L
    }
}
