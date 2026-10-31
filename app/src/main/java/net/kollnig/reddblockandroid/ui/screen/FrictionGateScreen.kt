package net.kollnig.reddblockandroid.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.speech.tts.TextToSpeech
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.kollnig.reddblockandroid.BuildConfig
import net.kollnig.reddblockandroid.R
import net.kollnig.reddblockandroid.data.CHINESE_VOCABULARY
import net.kollnig.reddblockandroid.data.ChineseWord
import net.kollnig.reddblockandroid.util.ChineseQuiz
import net.kollnig.reddblockandroid.util.ChineseTypingStats
import net.kollnig.reddblockandroid.util.QuestionType
import net.kollnig.reddblockandroid.util.prefs

// Common English words for the friction gate
private val WORD_LIST = listOf(
    "apple", "bridge", "candle", "desert", "eagle", "forest", "garden",
    "harbor", "island", "jungle", "kitchen", "lemon", "mirror", "needle",
    "orange", "palace", "garden", "river", "silver", "temple", "under",
    "valley", "winter", "yellow", "anchor", "basket", "castle", "dragon",
    "engine", "flower", "guitar", "hammer", "insect", "jacket", "kitten",
    "lantern", "marble", "nature", "ocean", "pencil", "rabbit", "saddle",
    "timber", "umbrella", "velvet", "walnut", "zenith", "branch", "copper",
    "danger", "eleven", "falcon", "gentle", "hollow", "ivory", "jigsaw",
    "kettle", "lumber", "mango", "narrow", "oyster", "pepper", "quartz",
    "rocket", "sunset", "trophy", "unfold", "voyage", "window", "absent",
    "butter", "circle", "dinner", "elbow", "finger", "gravel", "helmet",
    "indent", "jumble", "kernel", "ladder", "mental", "notice", "offset",
    "planet", "riddle", "spiral", "thread", "unique", "vertex", "wander",
    "ballet", "carbon", "differ", "effort", "fabric", "global", "hidden",
    "impact", "jungle", "knight", "linear", "method", "normal", "obtain",
    "parent", "random", "simple", "travel", "update", "vision", "weekly"
)


/**
 * @param wordCount          Words to type in English mode. Chinese mode asks
 *   [ChineseQuiz.QUESTIONS_PER_WORD] times as many multiple-choice questions.
 * @param unlockDurationText When non-null, shown in the block mode title to
 *   tell the user how long the block will be lifted (e.g. "10 minutes").
 * @param scheduleName       Name of the schedule that caused the block (shown in block mode).
 * @param isBlockMode        True when launched by the blocker service (shows close button
 *   instead of back, different title/instruction). False for in-app friction gate.
 * @param onPassed           Called when the gate is passed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrictionGateScreen(
    wordCount: Int,
    onPassed: () -> Unit,
    onBackPressed: () -> Unit,
    unlockDurationText: String? = null,
    scheduleName: String? = null,
    blockedTargetLabel: String? = null,
    isBlockMode: Boolean = false,
    isSettingsAccessMode: Boolean = false,
) {
    val useChineseMode = remember {
        BuildConfig.DEBUG && prefs.getBoolean("debug_chinese_mode_enabled", true)
    }

    // Build contextual title for block mode
    val topBarTitle = if (isSettingsAccessMode) {
        stringResource(R.string.settings_access_title)
    } else if (isBlockMode && blockedTargetLabel != null) {
        if (unlockDurationText != null)
            stringResource(R.string.block_gate_title_duration, blockedTargetLabel, unlockDurationText)
        else
            stringResource(R.string.block_gate_title, blockedTargetLabel)
    } else if (isBlockMode) {
        stringResource(R.string.app_name)
    } else {
        stringResource(R.string.friction_gate_title)
    }

    val contextText = if (isSettingsAccessMode) {
        stringResource(R.string.settings_access_desc)
    } else if (isBlockMode && scheduleName != null) {
        stringResource(R.string.block_gate_schedule, scheduleName)
    } else {
        null
    }
    val cancelText = stringResource(
        if (isBlockMode && !isSettingsAccessMode) R.string.keep_blocked else R.string.cancel
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        topBarTitle,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        if (isBlockMode) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (useChineseMode) {
                ChineseQuizBody(
                    wordCount = wordCount,
                    contextText = contextText,
                    cancelText = cancelText,
                    onPassed = onPassed,
                    onCancel = onBackPressed,
                )
            } else {
                TypingBody(
                    wordCount = wordCount,
                    contextText = contextText,
                    cancelText = cancelText,
                    finalActionText = if (isSettingsAccessMode)
                        stringResource(R.string.settings_access_allow)
                    else if (isBlockMode && unlockDurationText != null)
                        stringResource(R.string.friction_gate_unlock_duration, unlockDurationText)
                    else stringResource(R.string.override_button),
                    onPassed = onPassed,
                    onCancel = onBackPressed,
                )
            }
        }
    }
}

@Composable
private fun GateHeader(remainingText: String, contextText: String?, instruction: String) {
    Text(
        remainingText,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    if (contextText != null) {
        Text(
            contextText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
    Text(
        instruction,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CancelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** English mode: type each word shown. */
@Composable
private fun ColumnScope.TypingBody(
    wordCount: Int,
    contextText: String?,
    cancelText: String,
    finalActionText: String,
    onPassed: () -> Unit,
    onCancel: () -> Unit,
) {
    val words = remember { WORD_LIST.shuffled().take(wordCount) }
    val totalCount = words.size

    var currentWordIndex by remember { mutableIntStateOf(0) }
    var userInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        // The text field's node may not be attached on the first frame (e.g.
        // right after the activity is created/recreated), which makes
        // requestFocus() throw "FocusRequester is not initialized". Retry on
        // subsequent frames until focus is actually granted.
        repeat(10) {
            if (runCatching { focusRequester.requestFocus() }.getOrDefault(false)) {
                keyboardController?.show()
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
    }

    fun checkWord() {
        if (userInput.trim().equals(words[currentWordIndex], ignoreCase = true)) {
            isError = false
            if (currentWordIndex >= totalCount - 1) {
                onPassed()
            } else {
                currentWordIndex++
                userInput = ""
            }
        } else {
            isError = true
        }
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GateHeader(
            remainingText = pluralStringResource(
                R.plurals.friction_gate_words_remaining,
                totalCount - currentWordIndex,
                totalCount - currentWordIndex
            ),
            contextText = contextText,
            instruction = if (currentWordIndex == 0) stringResource(R.string.friction_gate_type_each_word)
            else stringResource(R.string.friction_gate_completed, currentWordIndex, totalCount),
        )
        // Word to type
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Text(
                words[currentWordIndex],
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    // Input and actions stay above the keyboard while the prompt can scroll.
    OutlinedTextField(
        value = userInput,
        onValueChange = { newValue ->
            isError = false
            // Words are shown one at a time, but people often type a space and
            // carry on with the next word. Treat a space as "submit this word"
            // and never keep it in the field.
            if (newValue.any { it.isWhitespace() }) {
                userInput = newValue.filterNot { it.isWhitespace() }
                if (userInput.isNotEmpty()) checkWord()
            } else {
                userInput = newValue
            }
        },
        placeholder = {
            Text(
                stringResource(R.string.type_here_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        shape = RoundedCornerShape(10.dp),
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password
        ),
        keyboardActions = KeyboardActions(onDone = { checkWord() }),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CancelButton(cancelText, onCancel, Modifier.weight(1f))

        Button(
            onClick = { checkWord() },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(10.dp),
            enabled = userInput.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                if (currentWordIndex >= totalCount - 1) finalActionText
                else stringResource(R.string.friction_gate_next),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Chinese mode: four-option multiple choice, alternating Chinese → English and
 * English → Chinese. A wrong pick locks the quiz for
 * [ChineseQuiz.WRONG_ANSWER_LOCKOUT_MS] and then re-asks the same word with
 * fresh options, so guessing is slower than thinking.
 */
@Composable
private fun ColumnScope.ChineseQuizBody(
    wordCount: Int,
    contextText: String?,
    cancelText: String,
    onPassed: () -> Unit,
    onCancel: () -> Unit,
) {
    // Bump this to unlock harder words
    val chineseHskLevel = 1
    val pool = remember { CHINESE_VOCABULARY.filter { it.hskLevel <= chineseHskLevel } }
    val questions = remember { ChineseQuiz.build(pool, wordCount).toMutableStateList() }
    val totalCount = questions.size

    var currentIndex by remember { mutableIntStateOf(0) }
    var wrongPick by remember { mutableStateOf<ChineseWord?>(null) }
    var lockoutEndsAt by remember { mutableStateOf<Long?>(null) }
    var lockoutSecondsLeft by remember { mutableIntStateOf(0) }
    var questionStartMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var weeklyStats by remember { mutableStateOf(ChineseTypingStats.getWeeklyStats()) }

    // TTS: speak each word once it has been answered correctly.
    val context = LocalContext.current
    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = java.util.Locale.CHINESE
            }
        }
        engine
    }
    DisposableEffect(Unit) {
        onDispose { tts?.shutdown() }
    }

    LaunchedEffect(lockoutEndsAt) {
        val endsAt = lockoutEndsAt ?: return@LaunchedEffect
        while (true) {
            val remaining = endsAt - System.currentTimeMillis()
            if (remaining <= 0) break
            lockoutSecondsLeft = ((remaining + 999) / 1000).toInt()
            delay(minOf(remaining, 250L))
        }
        // Re-ask the same word with fresh distractors in a new order.
        val q = questions[currentIndex]
        questions[currentIndex] = ChineseQuiz.question(q.word, q.type, pool)
        wrongPick = null
        lockoutEndsAt = null
    }

    if (totalCount == 0) {
        LaunchedEffect(Unit) { onPassed() }
        return
    }

    val question = questions[currentIndex]
    val isLocked = lockoutEndsAt != null

    fun choose(option: ChineseWord) {
        if (isLocked) return
        if (option == question.word) {
            ChineseTypingStats.recordWord(System.currentTimeMillis() - questionStartMillis)
            weeklyStats = ChineseTypingStats.getWeeklyStats()
            tts?.speak(option.character, TextToSpeech.QUEUE_FLUSH, null, null)
            if (currentIndex >= totalCount - 1) {
                onPassed()
            } else {
                currentIndex++
                questionStartMillis = System.currentTimeMillis()
            }
        } else {
            wrongPick = option
            lockoutSecondsLeft = (ChineseQuiz.WRONG_ANSWER_LOCKOUT_MS / 1000).toInt()
            lockoutEndsAt = System.currentTimeMillis() + ChineseQuiz.WRONG_ANSWER_LOCKOUT_MS
        }
    }

    val type = question.type

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GateHeader(
            remainingText = pluralStringResource(
                R.plurals.friction_gate_questions_remaining,
                totalCount - currentIndex,
                totalCount - currentIndex
            ),
            contextText = contextText,
            instruction = stringResource(
                when (type) {
                    QuestionType.CHINESE_TO_ENGLISH -> R.string.friction_gate_quiz_pick_meaning
                    QuestionType.ENGLISH_TO_CHINESE -> R.string.friction_gate_quiz_pick_characters
                    QuestionType.TONE -> R.string.friction_gate_quiz_pick_tones
                }
            ),
        )

        // Prompt
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    if (type == QuestionType.ENGLISH_TO_CHINESE) question.word.meaning else question.word.character,
                    style = if (type == QuestionType.ENGLISH_TO_CHINESE) MaterialTheme.typography.headlineSmall
                    else MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                // The meaning says which reading is meant; it gives nothing away about tones.
                if (type == QuestionType.TONE) {
                    Text(
                        question.word.meaning,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Options
        question.options.forEach { option ->
            val isWrongPick = option == wrongPick
            OutlinedButton(
                onClick = { choose(option) },
                enabled = !isLocked,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(10.dp),
                border = if (isWrongPick) BorderStroke(2.dp, MaterialTheme.colorScheme.error)
                else ButtonDefaults.outlinedButtonBorder(enabled = !isLocked),
                colors = if (isWrongPick) ButtonDefaults.outlinedButtonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) else ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(
                    when (type) {
                        QuestionType.CHINESE_TO_ENGLISH -> option.meaning
                        QuestionType.ENGLISH_TO_CHINESE -> option.character
                        QuestionType.TONE -> option.pinyin
                    },
                    style = if (type == QuestionType.CHINESE_TO_ENGLISH) MaterialTheme.typography.bodyLarge
                    else MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Wrong-answer feedback: explain the picked option without revealing the answer.
        val picked = wrongPick
        if (picked != null) {
            Text(
                if (type == QuestionType.TONE)
                    stringResource(R.string.friction_gate_quiz_wrong_tone, picked.character, picked.pinyin)
                else
                    stringResource(R.string.friction_gate_quiz_wrong, picked.character, picked.pinyin, picked.meaning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Text(
                pluralStringResource(R.plurals.friction_gate_quiz_retry_in, lockoutSecondsLeft, lockoutSecondsLeft),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }

        Text(
            stringResource(
                R.string.friction_gate_weekly_stats,
                weeklyStats.wordCount,
                (weeklyStats.totalDurationMs / 60_000L).toInt()
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    CancelButton(cancelText, onCancel, Modifier.fillMaxWidth())
}
