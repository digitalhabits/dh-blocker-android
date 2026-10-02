package net.kollnig.reddblockandroid

import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
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
        composeRule.runOnIdle {
            composeRule.activity.enableEdgeToEdge()
        }
        composeRule.setContent {
            ReDDBlockAndroidTheme {
                FrictionGateScreen(wordCount = 15, onPassed = {}, onBackPressed = {})
            }
        }

        val input = composeRule.onNode(hasSetTextAction())
        assertInputAboveIme()
        input.performTextInput("hello")
        input.assertTextContains("hello")

        composeRule.runOnIdle {
            composeRule.activity.window.insetsController?.hide(WindowInsets.Type.ime())
        }
        composeRule.waitUntil(5_000) { imeBottom() == 0 }
        input.performClick()
        assertInputAboveIme()
        input.assertTextContains("hello")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.cacheDir, "friction-keyboard.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun assertInputAboveIme() {
        composeRule.waitUntil(10_000) { imeBottom() > 0 }
        composeRule.waitForIdle()
        val input = composeRule.onNode(hasSetTextAction()).assertIsFocused()
        val density = composeRule.activity.resources.displayMetrics.density
        val inputBottom = input.getUnclippedBoundsInRoot().bottom.value * density
        val imeTop = composeRule.activity.window.decorView.height - imeBottom()
        assertTrue(
            "Focused input bottom $inputBottom is below IME top $imeTop",
            inputBottom <= imeTop + density
        )
        listOf(R.string.cancel, R.string.friction_gate_next).forEach { label ->
            val button = composeRule.onNodeWithText(composeRule.activity.getString(label))
                .assertIsDisplayed()
            val buttonBottom = button.getUnclippedBoundsInRoot().bottom.value * density
            assertTrue("Action bottom $buttonBottom is below IME top $imeTop", buttonBottom <= imeTop)
        }
    }

    private fun imeBottom(): Int = composeRule.activity.window.decorView.rootWindowInsets
        ?.getInsets(WindowInsets.Type.ime())
        ?.bottom
        ?: 0
}
