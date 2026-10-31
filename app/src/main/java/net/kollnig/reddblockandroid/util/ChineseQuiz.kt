package net.kollnig.reddblockandroid.util

import net.kollnig.reddblockandroid.data.ChineseWord
import kotlin.random.Random

/** Which side of a word is shown as the prompt. */
enum class QuizDirection {
    /** Show the characters, pick the English meaning. */
    CHINESE_TO_ENGLISH,

    /** Show the English meaning, pick the characters. */
    ENGLISH_TO_CHINESE,
}

data class QuizQuestion(
    val word: ChineseWord,
    val direction: QuizDirection,
    /** Shuffled answer options, exactly one of which is [word]. */
    val options: List<ChineseWord>,
) {
    val correctIndex: Int get() = options.indexOf(word)
}

object ChineseQuiz {
    const val OPTION_COUNT = 4

    /** Questions asked per word the user configured; quizzes are quicker than typing. */
    const val QUESTIONS_PER_WORD = 2

    /** How long a wrong answer locks the quiz before the user may try again. */
    const val WRONG_ANSWER_LOCKOUT_MS = 10_000L

    /**
     * Builds a quiz of `wordCount * QUESTIONS_PER_WORD` questions from distinct
     * words in [pool], alternating Chinese → English and English → Chinese.
     */
    fun build(pool: List<ChineseWord>, wordCount: Int, random: Random = Random.Default): List<QuizQuestion> {
        val questionCount = (wordCount * QUESTIONS_PER_WORD).coerceIn(0, pool.size)
        return pool.shuffled(random).take(questionCount).mapIndexed { i, word ->
            val direction = if (i % 2 == 0) QuizDirection.CHINESE_TO_ENGLISH else QuizDirection.ENGLISH_TO_CHINESE
            question(word, direction, pool, random)
        }
    }

    /**
     * Builds a question for [word] with fresh distractors. Distractors never share
     * the target's characters or meaning, nor each other's, so exactly one option is right.
     */
    fun question(
        word: ChineseWord,
        direction: QuizDirection,
        pool: List<ChineseWord>,
        random: Random = Random.Default,
    ): QuizQuestion {
        val distractors = mutableListOf<ChineseWord>()
        val usedCharacters = mutableSetOf(word.character)
        val usedMeanings = mutableSetOf(word.meaning.lowercase())
        for (candidate in pool.shuffled(random)) {
            if (distractors.size == OPTION_COUNT - 1) break
            if (candidate.character in usedCharacters || candidate.meaning.lowercase() in usedMeanings) continue
            distractors += candidate
            usedCharacters += candidate.character
            usedMeanings += candidate.meaning.lowercase()
        }
        return QuizQuestion(word, direction, (distractors + word).shuffled(random))
    }
}
