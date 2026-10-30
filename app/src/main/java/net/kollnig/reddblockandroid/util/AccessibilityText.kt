package net.kollnig.reddblockandroid.util

import android.view.accessibility.AccessibilityNodeInfo

/** Finds text in both classic Views and Compose virtual accessibility nodes. */
internal fun AccessibilityNodeInfo.containsAccessibilityText(text: String): Boolean {
    if (text.isBlank()) return false
    val matches = findAccessibilityNodeInfosByText(text)
    val found = matches.isNotEmpty()
    matches.forEach { it.recycle() }
    if (found) return true

    // Some Compose providers return no search results even though their child
    // nodes expose the text. Walk those nodes without recycling the caller's root.
    return containsTextInSubtree(text)
}

private fun AccessibilityNodeInfo.containsTextInSubtree(query: String): Boolean {
    if (text?.contains(query, ignoreCase = true) == true ||
        contentDescription?.contains(query, ignoreCase = true) == true
    ) return true

    for (index in 0 until childCount) {
        val child = getChild(index) ?: continue
        try {
            if (child.containsTextInSubtree(query)) return true
        } finally {
            child.recycle()
        }
    }
    return false
}
