package net.kollnig.reddblockandroid.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import net.kollnig.reddblockandroid.R
import net.kollnig.reddblockandroid.UnlockActivity
import net.kollnig.reddblockandroid.schedule.Schedules

import net.kollnig.reddblockandroid.util.isPrefsInitialized
import net.kollnig.reddblockandroid.util.prefs
import androidx.core.net.toUri

@SuppressLint("AccessibilityPolicy")
class BlockerService : AccessibilityService() {

    private var lastCheckedUrl: String? = null
    private var lastUrlCheckTime: Long = 0
    private val URL_CHECK_THROTTLE_MS = 500L

    private var lastBlockedPkg: String? = null
    private var lastBlockedTime: Long = 0
    private val APP_BLOCK_THROTTLE_MS = 2000L

    private var lastSettingsCheckTime: Long = 0
    private var lastSettingsGateTime: Long = 0
    private val SETTINGS_CHECK_THROTTLE_MS = 500L
    private var cachedForceStopLabel: Pair<java.util.Locale, String>? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        if (!isPrefsInitialized) {
            val deviceContext = createDeviceProtectedStorageContext()
            prefs = deviceContext.getSharedPreferences("prefs", MODE_PRIVATE)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (keyguardManager.isKeyguardLocked) return

        val isStateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && !isStateChange) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        // Force-stopping the blocker also disables its accessibility service, so
        // while any block is active, gate our own App info page in Settings.
        if (pkg == SETTINGS_PACKAGE && guardOwnAppInfoPage(checkImmediately = isStateChange)) return

        // Window state changes are only needed for the Settings guard above.
        if (isStateChange) return

        // Check for website blocking in supported browsers.
        // If the browser app itself is blocked, skip URL-based website handling
        // so the whole browser is gated as an app (go home on cancel) rather than
        // redirected to the digitalhabits.org focus page like a single blocked site.
        if (isSupportedBrowser(pkg) && Schedules.findBlockingScheduleForApp(pkg) == null) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUrlCheckTime >= URL_CHECK_THROTTLE_MS) {
                val url = extractUrlFromEvent(event)
                if (url != null) {
                    lastUrlCheckTime = currentTime
                    if (url != lastCheckedUrl) {
                        lastCheckedUrl = url
                        val domain = extractDomain(url)
                        if (domain != null) {
                            val blockingSchedule = Schedules.findBlockingScheduleForWebsite(domain)
                            if (blockingSchedule != null) {
                                Log.d(TAG, "Blocking website $domain in browser ($pkg)")
                                navigateBrowserToBlank(pkg)
                                launchFrictionGate(blockingSchedule.id, blockingSchedule.name, domain, isWebsite = true)
                                return
                            }
                        }
                    }
                }
            }
        }

        // Check app blocking
        if (shouldSkipPackage(pkg)) return

        val blockingSchedule = Schedules.findBlockingScheduleForApp(pkg)
        if (blockingSchedule != null) {
            val now = System.currentTimeMillis()
            if (pkg != lastBlockedPkg || now - lastBlockedTime >= APP_BLOCK_THROTTLE_MS) {
                lastBlockedPkg = pkg
                lastBlockedTime = now
                Log.d(TAG, "Blocking app $pkg by schedule: $blockingSchedule")
                val appLabel = getAppLabel(pkg)
                launchFrictionGate(blockingSchedule.id, blockingSchedule.name, appLabel, isWebsite = false)
            }
        }
    }

    /**
     * Shows the friction gate if our own App info page (the one with "Force
     * stop" and "Uninstall") is open while a schedule is active. Returns true
     * when the page is being gated.
     */
    private fun guardOwnAppInfoPage(checkImmediately: Boolean): Boolean {
        val now = System.currentTimeMillis()
        // Content changes in Settings are frequent, so throttle them; a window
        // state change (the page opening) is always checked straight away.
        if (!checkImmediately && now - lastSettingsCheckTime < SETTINGS_CHECK_THROTTLE_MS) return false
        lastSettingsCheckTime = now

        val schedule = Schedules.findActiveSchedule() ?: return false
        if (!isOwnAppInfoPageShowing()) return false
        if (now - lastSettingsGateTime < APP_BLOCK_THROTTLE_MS) return true
        lastSettingsGateTime = now

        Log.d(TAG, "Guarding own App info page; schedule ${schedule.name} is active")
        launchFrictionGate(
            schedule.id,
            schedule.name,
            getString(R.string.own_settings_target, getString(R.string.launcher_name)),
            isWebsite = false
        )
        return true
    }

    private fun isOwnAppInfoPageShowing(): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            if (root.packageName?.toString() != SETTINGS_PACKAGE) return false
            val ownLabel = applicationInfo.loadLabel(packageManager).toString()
            return containsText(root, ownLabel) && containsText(root, forceStopLabel())
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting Settings window", e)
            return false
        } finally {
            root.recycle()
        }
    }

    private fun containsText(
        root: android.view.accessibility.AccessibilityNodeInfo,
        text: String
    ): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        nodes.forEach { it.recycle() }
        return nodes.isNotEmpty()
    }

    /** The "Force stop" button label in the device's language, read from the
     *  Settings app's own resources so the guard works in every locale. */
    private fun forceStopLabel(): String {
        val locale = resources.configuration.locales[0]
        cachedForceStopLabel?.let { (cachedLocale, label) ->
            if (cachedLocale == locale) return label
        }
        val label = try {
            val res = packageManager.getResourcesForApplication(SETTINGS_PACKAGE)
            val id = res.getIdentifier("force_stop", "string", SETTINGS_PACKAGE)
            if (id != 0) res.getString(id) else null
        } catch (_: Exception) {
            null
        } ?: "Force stop"
        cachedForceStopLabel = locale to label
        return label
    }

    private fun launchFrictionGate(
        scheduleId: String,
        scheduleName: String,
        blockedTarget: String,
        isWebsite: Boolean
    ) {
        val intent = Intent(this, UnlockActivity::class.java).apply {
            putExtra(UnlockActivity.EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(UnlockActivity.EXTRA_SCHEDULE_NAME, scheduleName)
            putExtra(UnlockActivity.EXTRA_BLOCKED_TARGET, blockedTarget)
            putExtra(UnlockActivity.EXTRA_IS_WEBSITE, isWebsite)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            this.packageManager.getApplicationLabel(
                this.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun shouldSkipPackage(packageName: String): Boolean {
        return try {
            val info = this.packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem) {
                this.packageManager.getLaunchIntentForPackage(packageName) == null
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Maps browser package names to their URL bar view IDs */
    private val browserUrlViewIds = mapOf(
        // Firefox variants
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title", "ADDRESSBAR_URL_BOX"),
        "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view", "url_bar_title", "ADDRESSBAR_URL_BOX"),
        "org.mozilla.fenix" to listOf("mozac_browser_toolbar_url_view", "url_bar_title", "ADDRESSBAR_URL_BOX"),
        "org.mozilla.fenix.nightly" to listOf("mozac_browser_toolbar_url_view", "url_bar_title", "ADDRESSBAR_URL_BOX"),
        "org.mozilla.focus" to listOf("mozac_browser_toolbar_url_view", "url_bar_title", "ADDRESSBAR_URL_BOX"),
        // Chrome / Chromium
        "com.android.chrome" to listOf("url_bar", "origin", "display_url"),
        "com.chrome.beta" to listOf("url_bar", "display_url"),
        "org.chromium.chrome" to listOf("url_bar", "display_url"),
        // Brave
        "com.brave.browser" to listOf("url_bar", "display_url"),
        "com.brave.browser_beta" to listOf("url_bar", "display_url"),
        "com.brave.browser_nightly" to listOf("url_bar", "display_url"),
        // Samsung Internet
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text"),
        // Microsoft Edge
        "com.microsoft.emmx" to listOf("url_bar"),
        // Opera variants
        "com.opera.browser" to listOf("url_field"),
        "com.opera.browser.beta" to listOf("url_field"),
        "com.opera.mini.native" to listOf("url_field"),
        "com.opera.mini.native.beta" to listOf("url_field"),
        "com.opera.touch" to listOf("addressbarEdit"),
        // Vivaldi
        "com.vivaldi.browser" to listOf("url_bar", "display_url"),
        // Vanadium (GrapheneOS)
        "app.vanadium.browser" to listOf("url_bar", "display_url"),
        // Kiwi Browser
        "com.kiwibrowser.browser" to listOf("url_bar", "display_url"),
        // DuckDuckGo
        "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
        // Ecosia
        "com.ecosia.android" to listOf("url_bar"),
        // Huawei Browser
        "com.huawei.browser" to listOf("url_bar"),
        // Android system browser (AOSP)
        "com.android.browser" to listOf("url"),
        // Google Search app (in-app browser)
        "com.google.android.googlequicksearchbox" to listOf("googleapp_srp_search_box_text"),
    )

    private fun isSupportedBrowser(packageName: String): Boolean {
        return packageName in browserUrlViewIds
    }

    private fun navigateBrowserToBlank(browserPackage: String) {
        try {
            val uri = "https://digitalhabits.org".toUri()
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(browserPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            lastCheckedUrl = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate browser to blocked page", e)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun extractUrlFromEvent(event: AccessibilityEvent): String? {
        val root = rootInActiveWindow ?: return null
        try {
            val pkg = event.packageName?.toString() ?: return null
            val viewIds = browserUrlViewIds[pkg] ?: return null
            val knownUrlViewIds = viewIds.map { "$pkg:id/$it" } + viewIds

            // First try the standard API with fully-qualified resource IDs
            for (viewId in knownUrlViewIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
                if (nodes.isNullOrEmpty()) continue
                val url = extractUrlFromNodes(nodes)
                if (url != null) return url
            }

            // Fallback: manually traverse the tree to find nodes by bare resource-id.
            // Newer browsers (e.g. Firefox with Jetpack Compose) use test tags as
            // resource-ids without the "package:id/" prefix, which
            // findAccessibilityNodeInfosByViewId cannot match.
            val bareIds = viewIds.toSet()
            val fallbackNodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
            findNodesByBareResourceId(root, bareIds, fallbackNodes)
            if (fallbackNodes.isNotEmpty()) {
                val url = extractUrlFromNodes(fallbackNodes)
                if (url != null) return url
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting URL", e)
        } finally {
            root.recycle()
        }
        return null
    }

    private fun extractUrlFromNodes(
        nodes: List<android.view.accessibility.AccessibilityNodeInfo>
    ): String? {
        for (node in nodes) {
            try {
                // Skip if the URL bar is focused — user is typing,
                // don't block on autocomplete suggestions
                if (node.isFocused) continue
                val rawText = node.text?.toString()?.takeIf { it.isNotBlank() }
                    ?: node.contentDescription?.toString()
                if (rawText != null) {
                    val words = rawText.split("\\s+".toRegex())
                    for (word in words) {
                        val cleanWord = word.trimEnd('.', ',')
                        if (isValidUrlFormat(cleanWord)) {
                            return cleanWord
                        }
                    }
                }
            } finally {
                node.recycle()
            }
        }
        return null
    }

    /** Recursively walks the accessibility tree looking for nodes whose
     *  viewIdResourceName matches one of [targetIds] (bare, without package prefix). */
    private fun findNodesByBareResourceId(
        node: android.view.accessibility.AccessibilityNodeInfo,
        targetIds: Set<String>,
        results: MutableList<android.view.accessibility.AccessibilityNodeInfo>
    ) {
        val resName = node.viewIdResourceName
        if (resName != null && resName in targetIds) {
            results.add(android.view.accessibility.AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByBareResourceId(child, targetIds, results)
            child.recycle()
        }
    }

    private fun isValidUrlFormat(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return true
        if (trimmed.contains(" ") || trimmed.length < 4) return false
        val domainPattern = Regex("^[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}(/.*)?$")
        return domainPattern.matches(trimmed)
    }

    private fun extractDomain(url: String): String? {
        return try {
            var normalizedUrl = url.trim()
            if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
                normalizedUrl = "https://$normalizedUrl"
            }
            val uri = java.net.URI(normalizedUrl)
            uri.host?.lowercase()?.removePrefix("www.")
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting domain from URL: $url", e)
            null
        }
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "BlockerService"
        private const val SETTINGS_PACKAGE = "com.android.settings"
    }
}
