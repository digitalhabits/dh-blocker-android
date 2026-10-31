package net.kollnig.reddblockandroid.util

import net.kollnig.reddblockandroid.data.ChineseWord
import kotlin.random.Random

enum class QuestionType {
    /** Show the characters, pick the English meaning. */
    CHINESE_TO_ENGLISH,

    /** Show the English meaning, pick the characters. */
    ENGLISH_TO_CHINESE,

    /** Show the characters and meaning, pick the pinyin with the right tones. */
    TONE,
}

data class QuizQuestion(
    val word: ChineseWord,
    val type: QuestionType,
    /**
     * Shuffled answer options, exactly one of which is [word]. For [QuestionType.TONE]
     * the others are copies of [word] whose pinyin carries different tones.
     */
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

    private val TYPE_CYCLE = listOf(
        QuestionType.CHINESE_TO_ENGLISH,
        QuestionType.ENGLISH_TO_CHINESE,
        QuestionType.TONE,
    )

    /**
     * Builds a quiz of `wordCount * QUESTIONS_PER_WORD` questions from distinct
     * words in [pool], cycling through the question types. A word whose tones
     * can't be varied (neutral tone only) gets a meaning question instead.
     */
    fun build(pool: List<ChineseWord>, wordCount: Int, random: Random = Random.Default): List<QuizQuestion> {
        val questionCount = (wordCount * QUESTIONS_PER_WORD).coerceIn(0, pool.size)
        return pool.shuffled(random).take(questionCount).mapIndexed { i, word ->
            var type = TYPE_CYCLE[i % TYPE_CYCLE.size]
            if (type == QuestionType.TONE && toneVariants(word.pinyin).size < OPTION_COUNT - 1) {
                type = QuestionType.CHINESE_TO_ENGLISH
            }
            question(word, type, pool, random)
        }
    }

    /** Builds a question for [word] with fresh distractors. */
    fun question(
        word: ChineseWord,
        type: QuestionType,
        pool: List<ChineseWord>,
        random: Random = Random.Default,
    ): QuizQuestion {
        val distractors = if (type == QuestionType.TONE) {
            toneVariants(word.pinyin).shuffled(random).take(OPTION_COUNT - 1).map { word.copy(pinyin = it) }
        } else {
            meaningDistractors(word, pool, random)
        }
        return QuizQuestion(word, type, (distractors + word).shuffled(random))
    }

    /**
     * Distractors never share the target's characters or meaning, nor each
     * other's, so exactly one option is right.
     */
    private fun meaningDistractors(word: ChineseWord, pool: List<ChineseWord>, random: Random): List<ChineseWord> {
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
        return distractors
    }

    // Tone-marked vowels by base vowel, in tone order 1–4.
    private val TONE_MARKS: Map<Char, String> = mapOf(
        'a' to "āáǎà", 'e' to "ēéěè", 'i' to "īíǐì", 'o' to "ōóǒò", 'u' to "ūúǔù", 'ü' to "ǖǘǚǜ",
        'A' to "ĀÁǍÀ", 'E' to "ĒÉĚÈ", 'I' to "ĪÍǏÌ", 'O' to "ŌÓǑÒ", 'U' to "ŪÚǓÙ", 'Ü' to "ǕǗǙǛ",
    )

    /** Marked vowel → its base vowel. */
    private val BASE_VOWEL: Map<Char, Char> =
        TONE_MARKS.flatMap { (base, marks) -> marks.map { it to base } }.toMap()

    /**
     * Every other tone pattern for [pinyin]: each tone-marked syllable takes one of
     * tones 1–4 or the neutral tone. Syllables already in the neutral tone are left
     * alone, as their position can't be told without splitting syllables. Returns
     * an empty list for pinyin with no tone marks or with characters outside the
     * standard tone-mark set.
     */
    fun toneVariants(pinyin: String): List<String> {
        if (pinyin.any { !it.isAsciiOrUmlaut() && it !in BASE_VOWEL }) return emptyList()
        val positions = pinyin.indices.filter { pinyin[it] in BASE_VOWEL }
        if (positions.isEmpty()) return emptyList()

        var patterns = listOf(pinyin)
        for (pos in positions) {
            val base = BASE_VOWEL.getValue(pinyin[pos])
            val forms = listOf(base) + TONE_MARKS.getValue(base).toList()
            patterns = patterns.flatMap { p -> forms.map { p.substring(0, pos) + it + p.substring(pos + 1) } }
        }
        return patterns - pinyin
    }

    private fun Char.isAsciiOrUmlaut() = this.code < 128 || this == 'ü' || this == 'Ü'
}
