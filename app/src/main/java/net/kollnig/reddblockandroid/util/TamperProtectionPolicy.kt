package net.kollnig.reddblockandroid.util

import net.kollnig.reddblockandroid.data.Schedule

/** Settings can disable all blocking, so every active schedule contributes to this floor. */
internal fun requiredTamperWordCount(configuredWordCount: Int, activeSchedules: List<Schedule>): Int =
    maxOf(configuredWordCount, activeSchedules.maxOfOrNull { it.frictionWordCount } ?: 0)
