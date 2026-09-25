package net.kollnig.reddblockandroid

import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import net.kollnig.reddblockandroid.data.CHINESE_VOCABULARY
import net.kollnig.reddblockandroid.ui.screen.FrictionGateScreen
import net.kollnig.reddblockandroid.ui.theme.ReDDBlockAndroidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 30)
class FrictionGateKeyboardVisibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun focusedInputRemainsAboveIme() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.enableEdgeToEdge()
        }
        composeRule.setContent {
            ReDDBlockAndroidTheme {
                FrictionGateScreen(
                    wordCount = 15,
                    onPassed = {},
                    onBackPressed = {}
                )
            }
        }

        val input = composeRule.onNode(hasSetTextAction())
        input.assertIsFocused()
        composeRule.waitUntil(10_000) {
            imeBottom() > 0
        }

        composeRule.waitForIdle()
        assertInputAboveIme(input)

        val firstWord = CHINESE_VOCABULARY
            .asSequence()
            .filter { it.hskLevel <= 1 }
            .firstOrNull { word ->
                runCatching {
                    composeRule.onNodeWithText(word.character, useUnmergedTree = true).assertExists()
                }.isSuccess
            }
            ?: error("Could not identify the current Chinese challenge word")
        input.performTextInput(firstWord.pinyinNormalized)
        input.performImeAction()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Character 2 of 15", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        input.assertIsFocused()
        assertInputAboveIme(input)

        composeRule.activity.runOnUiThread {
            composeRule.activity.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(composeRule.activity.window.decorView.windowToken, 0)
        }
        composeRule.waitUntil(5_000) { imeBottom() == 0 }
        input.performClick()
        composeRule.waitUntil(10_000) {
            runCatching { input.assertIsFocused() }.isSuccess && imeBottom() > 0
        }
        composeRule.waitForIdle()
        assertInputAboveIme(input)
    }

    private fun assertInputAboveIme(input: androidx.compose.ui.test.SemanticsNodeInteraction) {
        val density = composeRule.activity.resources.displayMetrics.density
        val inputBottom = input.getUnclippedBoundsInRoot().bottom.value * density
        val rootBottom = composeRule.onRoot().getUnclippedBoundsInRoot().bottom.value * density
        val imeTop = rootBottom - imeBottom()
        assertTrue(
            "Focused input bottom $inputBottom is below IME top $imeTop",
            inputBottom <= imeTop + density
        )
    }

    private fun imeBottom(): Int = composeRule.activity.window.decorView.rootWindowInsets
        ?.getInsets(WindowInsets.Type.ime())
        ?.bottom
        ?: 0
}
