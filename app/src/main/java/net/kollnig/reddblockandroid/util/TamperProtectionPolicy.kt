package net.kollnig.reddblockandroid.util

import net.kollnig.reddblockandroid.data.Schedule
import net.kollnig.reddblockandroid.data.WordCount

/**
 * Settings can disable all blocking, so every active schedule contributes to this floor.
 * The result never exceeds [WordCount.MAX].
 */
internal fun requiredTamperWordCount(configuredWordCount: Int, activeSchedules: List<Schedule>): Int =
    WordCount.clamp(maxOf(configuredWordCount, activeSchedules.maxOfOrNull { it.frictionWordCount } ?: 0))
