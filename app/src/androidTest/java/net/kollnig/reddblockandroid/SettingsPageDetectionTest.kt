package net.kollnig.reddblockandroid

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.kollnig.reddblockandroid.util.containsAccessibilityText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPageDetectionTest {
    @Test
    fun appInfoExposesAppLabelAndForceStop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val ownLabel = context.applicationInfo.loadLabel(context.packageManager).toString()
        val resources = context.packageManager.getResourcesForApplication("com.android.settings")
        val forceStopId = resources.getIdentifier("force_stop", "string", "com.android.settings")
        val forceStop = if (forceStopId != 0) resources.getString(forceStopId) else "Force stop"

        try {
            // Request a fresh page: older Android can otherwise bring forward
            // the safe task left behind by a previous tamper-protection test.
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            instrumentation.uiAutomation.waitForIdle(500, 5_000)

            // On Compose Settings, findAccessibilityNodeInfosByText returns no
            // matches for these labels. The production fallback must find them.
            val root = awaitAppInfo(ownLabel, forceStop)
            try {
                assertTrue(root.containsAccessibilityText(ownLabel))
                assertTrue(root.containsAccessibilityText(forceStop))
                assertFalse(root.containsAccessibilityText("not-a-settings-label-8c0590c6"))
                assertFalse(root.containsAccessibilityText(""))
            } finally {
                root.recycle()
            }
        } finally {
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    @Test
    fun onlyAccessibilityOverviewExposesItsCategoryTitles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resources = context.packageManager.getResourcesForApplication("com.android.settings")
        // Keep in sync with BlockerService.accessibilityCategoryTitles().
        val titles = listOf(
            "user_installed_services_category_title",
            "screen_reader_category_title",
            "interaction_control_category_title",
        ).mapNotNull { name ->
            resources.getIdentifier(name, "string", "com.android.settings")
                .takeIf { it != 0 }?.let(resources::getString)
        }
        assertTrue("Settings has none of the category strings", titles.isNotEmpty())

        try {
            openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            assertTrue("Accessibility overview shows no category title",
                awaitSettingsRoot { root -> titles.any { root.containsAccessibilityText(it) } })

            // Settings home links to Accessibility and must stay reachable.
            openSettings(Settings.ACTION_SETTINGS)
            assertFalse("Settings home shows an Accessibility category title",
                awaitSettingsRoot { root -> titles.any { root.containsAccessibilityText(it) } })
        } finally {
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun openSettings(action: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.targetContext.startActivity(Intent(action).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
    }

    /** True once a Settings window satisfies [predicate] within 5 seconds. */
    private fun awaitSettingsRoot(predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            if (root != null) {
                try {
                    if (root.packageName?.toString() == "com.android.settings" && predicate(root)) return true
                } finally {
                    root.recycle()
                }
            }
            SystemClock.sleep(100)
        }
        return false
    }

    private fun awaitAppInfo(label: String, forceStop: String): AccessibilityNodeInfo {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            if (root != null) {
                if (root.packageName?.toString() == "com.android.settings" &&
                    root.containsAccessibilityText(label) && root.containsAccessibilityText(forceStop)
                ) return root
                root.recycle()
            }
            SystemClock.sleep(100)
        }
        throw AssertionError("App info did not expose the app label and Force stop within 5 seconds")
    }
}
