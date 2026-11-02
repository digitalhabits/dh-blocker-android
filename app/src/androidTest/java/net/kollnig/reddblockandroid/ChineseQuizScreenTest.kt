package net.kollnig.reddblockandroid

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.kollnig.reddblockandroid.data.CHINESE_VOCABULARY
import net.kollnig.reddblockandroid.ui.screen.FrictionGateScreen
import net.kollnig.reddblockandroid.ui.theme.ReDDBlockAndroidTheme
import net.kollnig.reddblockandroid.util.prefs
import org.junit.*
import org.junit.Assert.*

class ChineseQuizScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var original: SharedPreferences
    @Before fun setup() {
        original = prefs
        prefs = rule.activity.getSharedPreferences("pinyin-quiz-test", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("debug_chinese_mode_enabled", true).commit()
    }
    @After fun restore() {
        prefs = original
        rule.activity.deleteSharedPreferences("pinyin-quiz-test")
    }
    @Test fun wrongAnswerLocksThenAllQuestionTypesComplete() {
        var passed = false
        rule.setContent { ReDDBlockAndroidTheme { FrictionGateScreen(2, { passed = true }, {}) } }
        val pool = CHINESE_VOCABULARY.filter { it.hskLevel == 1 }
        fun shown(text: String) = rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        fun assertNoCharacters() {
            assertFalse(Regex("""\p{IsHan}""").containsMatchIn(rule.onRoot(useUnmergedTree = true).printToString()))
        }
        assertNoCharacters()
        val first = pool.first { shown(it.pinyin) && shown(it.meaning) }
        val wrong = pool.first { it.meaning != first.meaning && shown(it.meaning) }
        rule.onNodeWithText(wrong.meaning).performClick()
        rule.onNodeWithText(first.meaning).assertIsNotEnabled()
        rule.onNodeWithText("4 questions remaining").assertExists()
        assertNoCharacters()
        rule.waitUntil(15000) { runCatching { rule.onNodeWithText(first.meaning).assertIsEnabled(); true }.getOrDefault(false) }
        rule.waitForIdle()
        rule.onNodeWithText(first.meaning).assertIsEnabled().performClick()
        rule.onNodeWithText("3 questions remaining").assertExists()
        assertNoCharacters()
        val second = pool.first { shown(it.meaning) && shown(it.pinyin) }
        rule.onNodeWithText(second.pinyin).performClick()
        rule.onNodeWithText("2 questions remaining").assertExists()
        assertNoCharacters()
        val third = pool.first { shown(it.meaning) && shown(it.pinyin) }
        if (!shown(rule.activity.getString(R.string.friction_gate_quiz_pick_meaning))) rule.onNodeWithText(third.pinyin).performClick()
        else rule.onNodeWithText(third.meaning).performClick()
        rule.onNodeWithText("1 question remaining").assertExists()
        val fourth = pool.first { shown(it.pinyin) && shown(it.meaning) }
        rule.onNodeWithText(fourth.meaning).performClick()
        rule.waitUntil(7_000) { passed }
        rule.runOnIdle { assertTrue(passed) }
    }
}
