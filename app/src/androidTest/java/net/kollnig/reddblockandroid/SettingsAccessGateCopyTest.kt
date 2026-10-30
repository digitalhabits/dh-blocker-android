package net.kollnig.reddblockandroid

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import net.kollnig.reddblockandroid.ui.screen.FrictionGateScreen
import net.kollnig.reddblockandroid.ui.theme.ReDDBlockAndroidTheme
import net.kollnig.reddblockandroid.util.prefs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsAccessGateCopyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var originalPrefs: SharedPreferences
    private val testPrefsName = "settings-access-copy-test"

    @Before
    fun useIsolatedEnglishGatePreferences() {
        originalPrefs = prefs
        prefs = composeRule.activity.getSharedPreferences(testPrefsName, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("debug_chinese_mode_enabled", false).commit()
    }

    @After
    fun restorePreferences() {
        prefs = originalPrefs
        composeRule.activity.deleteSharedPreferences(testPrefsName)
    }

    @Test
    fun settingsGateExplainsIndependentAccessAndDisplaysItsFullAction() {
        composeRule.setContent {
            ReDDBlockAndroidTheme {
                FrictionGateScreen(
                    wordCount = 1,
                    onPassed = {},
                    onBackPressed = {},
                    isBlockMode = true,
                    isSettingsAccessMode = true,
                    scheduleName = "A schedule that must stay active",
                    unlockDurationText = "24 hours",
                    blockedTargetLabel = "A blocked target",
                )
            }
        }

        val activity = composeRule.activity
        composeRule.onNodeWithText(activity.getString(R.string.settings_access_title)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.settings_access_desc)).assertIsDisplayed()
        val allow = composeRule.onNodeWithText(activity.getString(R.string.settings_access_allow))
            .assertIsDisplayed()
        val cancel = composeRule.onNodeWithText(activity.getString(R.string.cancel)).assertIsDisplayed()
        composeRule.onNodeWithText("24 hours", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("A schedule that must stay active", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("A blocked target", substring = true).assertDoesNotExist()
        // The long Settings action must fit beside Cancel, including when it wraps.
        val actionBounds = allow.getUnclippedBoundsInRoot()
        val cancelBounds = cancel.getUnclippedBoundsInRoot()
        val density = activity.resources.displayMetrics.density
        assertTrue(actionBounds.left >= cancelBounds.right)
        assertTrue(actionBounds.right.value * density <= activity.window.decorView.width)
        assertTrue(actionBounds.bottom.value * density <= activity.window.decorView.height)
    }
}
