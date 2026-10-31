package net.kollnig.reddblockandroid.util

import net.kollnig.reddblockandroid.data.CHINESE_VOCABULARY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChineseQuizTest {
    private val pool = CHINESE_VOCABULARY.filter { it.hskLevel <= 1 }

    @Test
    fun asksTwiceAsManyDistinctWordsAsConfigured() {
        val quiz = ChineseQuiz.build(pool, wordCount = 5, random = Random(1))
        assertEquals(10, quiz.size)
        assertEquals(10, quiz.map { it.word }.toSet().size)
    }

    @Test
    fun cyclesQuestionTypes() {
        repeat(20) { seed ->
            ChineseQuiz.build(pool, wordCount = 6, random = Random(seed)).forEachIndexed { i, q ->
                when (i % 3) {
                    0 -> assertEquals(QuestionType.CHINESE_TO_ENGLISH, q.type)
                    1 -> assertEquals(QuestionType.ENGLISH_TO_CHINESE, q.type)
                    // Neutral-tone-only words fall back to a meaning question.
                    else -> assertTrue(
                        q.type == QuestionType.TONE ||
                            (q.type == QuestionType.CHINESE_TO_ENGLISH && ChineseQuiz.toneVariants(q.word.pinyin).isEmpty())
                    )
                }
            }
        }
    }

    @Test
    fun everyQuestionHasFourUnambiguousOptions() {
        repeat(50) { seed ->
            ChineseQuiz.build(pool, wordCount = 10, random = Random(seed)).forEach { q ->
                assertEquals(ChineseQuiz.OPTION_COUNT, q.options.size)
                assertEquals(q.word, q.options[q.correctIndex])
                assertEquals(1, q.options.count { it == q.word })
                if (q.type == QuestionType.TONE) {
                    assertTrue(q.options.all { it.character == q.word.character })
                    assertEquals(q.options.size, q.options.map { it.pinyin }.toSet().size)
                } else {
                    assertEquals(q.options.size, q.options.map { it.character }.toSet().size)
                    assertEquals(q.options.size, q.options.map { it.meaning.lowercase() }.toSet().size)
                }
            }
        }
    }

    @Test
    fun capsQuestionCountAtPoolSize() {
        val small = pool.take(6)
        val quiz = ChineseQuiz.build(small, wordCount = 10, random = Random(3))
        assertEquals(6, quiz.size)
        assertTrue(quiz.all { it.options.size <= ChineseQuiz.OPTION_COUNT })
    }

    @Test
    fun toneVariantsChangeOnlyMarkedSyllables() {
        assertEquals(setOf("hāo", "háo", "hào", "hao"), ChineseQuiz.toneVariants("hǎo").toSet())
        val xihuan = ChineseQuiz.toneVariants("xǐhuan")
        assertEquals(4, xihuan.size)
        assertTrue(xihuan.all { it.endsWith("huan") })
        // Two marked syllables: 5 × 5 patterns, minus the real one.
        assertEquals(24, ChineseQuiz.toneVariants("Zhōngguó").size)
        assertTrue("lǚ" !in ChineseQuiz.toneVariants("lǚ") && "lü" in ChineseQuiz.toneVariants("lǚ"))
    }

    @Test
    fun noToneVariantsForNeutralOrUnknownPinyin() {
        assertEquals(emptyList<String>(), ChineseQuiz.toneVariants("de"))
        assertEquals(emptyList<String>(), ChineseQuiz.toneVariants("ńg"))
    }
}
