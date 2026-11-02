package net.kollnig.reddblockandroid.data

/** Bounds for every friction and tamper-protection word count, matching the sliders. */
object WordCount {
    const val MIN = 1
    const val MAX = 50

    fun clamp(count: Int): Int = count.coerceIn(MIN, MAX)
}
