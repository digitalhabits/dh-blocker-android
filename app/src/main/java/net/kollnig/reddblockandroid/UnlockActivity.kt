package net.kollnig.reddblockandroid

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.kollnig.reddblockandroid.schedule.Schedules
import net.kollnig.reddblockandroid.ui.screen.FrictionGateScreen
import net.kollnig.reddblockandroid.ui.theme.ReDDBlockAndroidTheme
import net.kollnig.reddblockandroid.util.TamperProtection

/**
 * Standalone activity launched by BlockerService when an app or website is blocked.
 * Shows the friction gate; on completion, temporarily disables the
 * schedule that caused an app or website block. A tamper-protection gate
 * instead allows Settings access for one minute without pausing schedules.
 * Protected Settings pages are never reopened automatically.
 */
class UnlockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        const val EXTRA_SCHEDULE_NAME = "schedule_name"
        const val EXTRA_BLOCKED_TARGET = "blocked_target"
        const val EXTRA_IS_WEBSITE = "is_website"
        const val EXTRA_IS_SETTINGS = "is_settings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID)
        val scheduleName = intent.getStringExtra(EXTRA_SCHEDULE_NAME)
        val blockedTarget = intent.getStringExtra(EXTRA_BLOCKED_TARGET)
        val isWebsite = intent.getBooleanExtra(EXTRA_IS_WEBSITE, false)
        val isSettings = intent.getBooleanExtra(EXTRA_IS_SETTINGS, false)
        val schedule = scheduleId?.let { Schedules.get(it) }

        if (!isSettings && (schedule == null || !schedule.isEnabled)) {
            finish()
            return
        }

        val requiredWordCount = if (isSettings) TamperProtection.requiredWordCount()
            else requireNotNull(schedule).frictionWordCount
        val unlockDurationText = if (isSettings) null
            else formatUnlockDuration(requireNotNull(schedule).autoReenableMinutes)

        setContent {
            ReDDBlockAndroidTheme {
                FrictionGateScreen(
                    wordCount = requiredWordCount,
                    unlockDurationText = unlockDurationText,
                    scheduleName = if (isSettings) null else scheduleName ?: schedule?.name,
                    blockedTargetLabel = if (isSettings) null else blockedTarget,
                    isBlockMode = true,
                    isSettingsAccessMode = isSettings,
                    onPassed = {
                        if (isSettings) {
                            // A stricter schedule may have started while the user was typing.
                            if (TamperProtection.requiredWordCount() > requiredWordCount) {
                                recreate()
                            } else {
                                TamperProtection.settingsAccess.grant(requiredWordCount)
                                startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                })
                                finish()
                            }
                        } else {
                            Schedules.temporaryUnlock(this@UnlockActivity, requireNotNull(schedule).id)
                            finish()
                        }
                    },
                    onBackPressed = {
                        if (isWebsite || isSettings) {
                            // The browser is already on the focus page, and
                            // Settings has been reset to its home screen.
                            finish()
                        } else {
                            // For blocked apps, returning to them would immediately
                            // re-trigger the gate. Go home instead, and finish so this
                            // gate doesn't linger in its task and resurface when the
                            // user later relaunches the app from the launcher.
                            goHomeAndFinish()
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask delivers a fresh block target to the existing instance
        // instead of creating a new activity; re-render with the new extras.
        setIntent(intent)
        recreate()
    }

    /** Leaves to the home screen and finishes the gate so it doesn't linger
     *  in its own task and reappear when the launcher icon is tapped. */
    private fun goHomeAndFinish() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
        finish()
    }

    private fun formatUnlockDuration(autoReenableMinutes: Int): String? {
        if (autoReenableMinutes <= 0) return null
        val hours = autoReenableMinutes / 60
        val minutes = autoReenableMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> getString(R.string.duration_hours_minutes, hours, minutes)
            hours > 0 -> resources.getQuantityString(R.plurals.duration_hours, hours, hours)
            else -> resources.getQuantityString(R.plurals.duration_minutes, minutes, minutes)
        }
    }

}
