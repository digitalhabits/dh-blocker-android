package net.kollnig.reddblockandroid.util

import android.os.SystemClock
import androidx.core.content.edit
import net.kollnig.reddblockandroid.schedule.Schedules

/**
 * Protection against escaping a block via Settings, enabled by default: while a schedule
 * is active, the blocker's App info page (Force stop, Uninstall) and its
 * accessibility toggle require at least [wordCount] words and the highest
 * word count of all active schedules. Passing allows Settings access for one minute;
 * it does not pause any schedule.
 */
object TamperProtection {
    private const val ENABLED_KEY = "tamper_protection_enabled"
    private const val WORD_COUNT_KEY = "tamper_protection_word_count"
    const val DEFAULT_WORD_COUNT = 10

    internal val settingsAccess = SettingsAccessWindow(SystemClock::elapsedRealtime)

    fun requiredWordCount(): Int = requiredTamperWordCount(wordCount, Schedules.findActiveSchedules())

    var isEnabled: Boolean
        get() = prefs.getBoolean(ENABLED_KEY, true)
        set(value) = prefs.edit { putBoolean(ENABLED_KEY, value) }

    var wordCount: Int
        get() = prefs.getInt(WORD_COUNT_KEY, DEFAULT_WORD_COUNT)
        set(value) = prefs.edit { putInt(WORD_COUNT_KEY, value) }

    /** True when saving [enabled] and [wordCount] would make protection weaker than it is now. */
    fun wouldWeaken(enabled: Boolean, wordCount: Int): Boolean =
        isEnabled && (!enabled || wordCount < this.wordCount)
}
