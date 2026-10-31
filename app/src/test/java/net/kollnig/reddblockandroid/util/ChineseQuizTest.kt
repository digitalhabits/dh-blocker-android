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
    fun alternatesDirection() {
        val quiz = ChineseQuiz.build(pool, wordCount = 3, random = Random(2))
        quiz.forEachIndexed { i, q ->
            val expected = if (i % 2 == 0) QuizDirection.CHINESE_TO_ENGLISH else QuizDirection.ENGLISH_TO_CHINESE
            assertEquals(expected, q.direction)
        }
    }

    @Test
    fun everyQuestionHasFourUnambiguousOptions() {
        repeat(50) { seed ->
            ChineseQuiz.build(pool, wordCount = 10, random = Random(seed)).forEach { q ->
                assertEquals(ChineseQuiz.OPTION_COUNT, q.options.size)
                assertEquals(q.word, q.options[q.correctIndex])
                assertEquals(q.options.size, q.options.map { it.character }.toSet().size)
                assertEquals(q.options.size, q.options.map { it.meaning.lowercase() }.toSet().size)
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
}
