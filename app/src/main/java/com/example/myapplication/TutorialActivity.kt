package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * TUTORIAL - teaches the headband gestures in 3 phases:
 *   1. LEARN        - "Tilt your head to the RIGHT to play G" + big arrow, 2 rounds
 *   2. RECALL       - "Play A minor", no hints (each chord 2x, random; a hint appears after 2 mistakes)
 *   3. PROGRESSION  - C -> G -> A minor -> F in order
 * Then a summary + "Repeat tutorial" (head RIGHT or tap). Two strong blinks at any time = back to menu.
 *
 * File layout:  MODEL -> STATE -> LIFECYCLE -> FLOW -> GESTURES -> UI HELPERS
 */
class TutorialActivity : AppCompatActivity() {

    // =================================================================== MODEL

    /** Head gestures and their chords - same mapping as in PlayActivity. */
    private enum class Gesture(val chord: Akordy.Akord, val chordName: String, val move: String, val arrow: String) {
        LEFT(Akordy.Akord.C, "C", "Tilt your head to the LEFT", "⬅"),
        RIGHT(Akordy.Akord.G, "G", "Tilt your head to the RIGHT", "➡"),
        UP(Akordy.Akord.AM, "A minor", "Tilt your head UP", "⬆"),
        DOWN(Akordy.Akord.F, "F", "Tilt your head DOWN", "⬇"),
    }

    /** The three phases: header name, intro screen texts. */
    private enum class Phase(val number: Int, val title: String, val introTitle: String, val introText: String) {
        LEARN(
            1, "Learn the moves", "Learn the moves",
            "Each head move plays a different chord.\nMove and come straight back to the neutral position.\n\nTwo strong blinks = back to menu."
        ),
        RECALL(
            2, "No hints", "Now without hints!",
            "I will only show the chord name.\nDo you remember which move plays it?"
        ),
        PROGRESSION(
            3, "Progression", "Play the progression",
            "C → G → A minor → F\nThe most popular progression in pop music."
        ),
    }

    /** One task: which gesture we expect, in which phase and round. */
    private data class Step(val phase: Phase, val gesture: Gesture, val round: Int = 1)

    private companion object {
        const val PHASE_INTRO_MS = 3000L      // phase intro screen
        const val ROUND_INTRO_MS = 2000L      // "Round 2" screen
        const val AFTER_SUCCESS_MS = 1200L    // pause after a correct move
        const val HINT_AFTER_MISTAKES = 2     // phases 2-3: show a hint after this many mistakes in one step
        const val LEARN_ROUNDS = 2

        val GREEN = Color.rgb(79, 138, 91)
        val RED = Color.rgb(198, 40, 40)
        val ORANGE = Color.rgb(181, 101, 29)

        const val LEARN_BUBBLE = "Tilt and come straight back to the middle!"
    }

    // =================================================================== STATE

    /** What the screen is doing right now - decides how gestures are handled. */
    private enum class State { INTRO, WAITING_FOR_GESTURE, CORRECT, FINISHED }

    private var state = State.INTRO
    private var steps: List<Step> = emptyList()
    private var index = 0
    private var mistakes = 0
    private var mistakesInStep = 0

    private val currentStep get() = steps[index]

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var chords: Akordy
    private lateinit var blinkExit: DoubleBlinkExit

    private lateinit var tvPhase: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvInstruction: TextView
    private lateinit var tvArrow: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvFeedback: TextView
    private lateinit var btnRepeat: Button
    private lateinit var bubbleRow: LinearLayout
    private lateinit var rowToPlay: LinearLayout
    private lateinit var tvChordChip: TextView

    // =================================================================== LIFECYCLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        tvPhase = findViewById(R.id.tvPhase)
        progressBar = findViewById(R.id.tutorialProgress)
        tvInstruction = findViewById(R.id.tvInstruction)
        tvArrow = findViewById(R.id.tvArrow)
        tvHint = findViewById(R.id.tvHint)
        tvFeedback = findViewById(R.id.tvFeedback)
        btnRepeat = findViewById(R.id.btnRepeat)
        bubbleRow = findViewById(R.id.bubbleRow)
        rowToPlay = findViewById(R.id.rowToPlay)
        tvChordChip = findViewById(R.id.tvChordChip)
        btnRepeat.setOnClickListener { startTutorial() }

        chords = Akordy(this)
        blinkExit = DoubleBlinkExit(handler, findViewById(R.id.exitHintText)) { finish() }

        startTutorial()
    }

    override fun onResume() {
        super.onResume()
        AppControler.ustawAkcje(
            left = { onGesture(Gesture.LEFT) },
            right = { onGesture(Gesture.RIGHT) },
            up = { onGesture(Gesture.UP) },
            down = { onGesture(Gesture.DOWN) },
            blink = { blinkExit.onBlink() },
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        chords.free()
    }

    // =================================================================== FLOW

    private fun startTutorial() {
        handler.removeCallbacksAndMessages(null)
        blinkExit.reset()
        steps = buildSteps()
        index = 0
        mistakes = 0
        btnRepeat.visibility = View.GONE
        progressBar.max = steps.size
        showPhaseIntro(currentStep)
    }

    /** All tasks of the tutorial, in order. */
    private fun buildSteps(): List<Step> {
        val inOrder = listOf(Gesture.RIGHT, Gesture.LEFT, Gesture.UP, Gesture.DOWN)

        // phase 1: round 1 in a fixed order, round 2 shuffled (not starting with the last move of round 1)
        val learn = (1..LEARN_ROUNDS).flatMap { round ->
            val order = if (round == 1) inOrder else shuffledNoRepeats(inOrder, notFirst = inOrder.last())
            order.map { Step(Phase.LEARN, it, round) }
        }
        // phase 2: each gesture twice, shuffled, never the same twice in a row
        val recall = shuffledNoRepeats(Gesture.values().toList() + Gesture.values().toList())
            .map { Step(Phase.RECALL, it) }
        // phase 3: C G Am F
        val progression = listOf(Gesture.LEFT, Gesture.RIGHT, Gesture.UP, Gesture.DOWN)
            .map { Step(Phase.PROGRESSION, it) }

        return learn + recall + progression
    }

    /** Called after each correct move - decides what comes next. */
    private fun goToNextStep() {
        val previous = currentStep
        index++
        when {
            index >= steps.size -> showSummary()
            currentStep.phase != previous.phase -> showPhaseIntro(currentStep)
            currentStep.round != previous.round -> showRoundIntro(currentStep)
            else -> showStep()
        }
    }

    private fun showPhaseIntro(step: Step) =
        showIntro(step, step.phase.introTitle, step.phase.introText, PHASE_INTRO_MS)

    private fun showRoundIntro(step: Step) =
        showIntro(step, "Once more!", "Each move once more - this time in a different order.", ROUND_INTRO_MS)

    private fun showIntro(step: Step, title: String, text: String, durationMs: Long) {
        state = State.INTRO
        render(header = headerFor(step), instruction = title, hint = text)
        handler.postDelayed({ showStep() }, durationMs)
    }

    private fun showStep() {
        state = State.WAITING_FOR_GESTURE
        mistakesInStep = 0
        progressBar.progress = index
        val gesture = currentStep.gesture

        when (currentStep.phase) {
            Phase.LEARN -> render(
                header = headerFor(currentStep),
                instruction = gesture.move.replace(" to the ", "\nto the "),   // "Tilt your head\nto the RIGHT"
                arrow = gesture.arrow,
                hint = LEARN_BUBBLE,
                chord = gesture,                                                // "to play [G]"
            )
            Phase.RECALL -> render(
                header = headerFor(currentStep),
                instruction = "Play ${gesture.chordName}",
            )
            Phase.PROGRESSION -> render(
                header = headerFor(currentStep),
                instruction = "Play ${gesture.chordName}",
                hint = progressionStatus(),
            )
        }
    }

    private fun showSummary() {
        state = State.FINISHED
        progressBar.progress = progressBar.max
        val accuracy = 100 * steps.size / (steps.size + mistakes)
        render(
            header = "Done!",
            instruction = "Tutorial completed 🎉",
            hint = "Accuracy: $accuracy%  (mistakes: $mistakes)\n\nBlink hard twice to go back to the menu",
        )
        btnRepeat.visibility = View.VISIBLE
    }

    // =================================================================== GESTURES

    private fun onGesture(gesture: Gesture) {
        // every move always plays its chord - the user hears what they did
        chords.play(gesture.chord)

        when (state) {
            State.WAITING_FOR_GESTURE ->
                if (gesture == currentStep.gesture) onCorrect() else onMistake(gesture)
            State.FINISHED ->
                if (gesture == Gesture.RIGHT) pressButton(btnRepeat)      // head right = "Repeat tutorial"
            State.INTRO, State.CORRECT -> Unit                            // ignore during transitions
        }
    }

    private fun onCorrect() {
        state = State.CORRECT
        feedback("Well done! ✓  ${currentStep.gesture.chordName}", GREEN)
        handler.postDelayed({ goToNextStep() }, AFTER_SUCCESS_MS)
    }

    private fun onMistake(played: Gesture) {
        mistakes++
        mistakesInStep++
        val target = currentStep.gesture
        when {
            currentStep.phase == Phase.LEARN ->
                feedback("That was ${played.chordName}. Try: ${target.move.lowercase()}", RED)
            mistakesInStep >= HINT_AFTER_MISTAKES -> {
                feedback("Hint: ${target.move.lowercase()}", ORANGE)
                tvArrow.text = target.arrow
                tvArrow.setTextColor(chordColor(target.chord))
                tvArrow.visibility = View.VISIBLE
            }
            else ->
                feedback("That was ${played.chordName}. Try again!", RED)
        }
    }

    // =================================================================== UI HELPERS

    /** Sets all main texts at once (anything not given is cleared / hidden). */
    private fun render(
        header: String,
        instruction: String,
        arrow: String = "",
        hint: String = "",
        chord: Gesture? = null,
    ) {
        tvPhase.text = header
        tvInstruction.text = instruction
        tvArrow.text = arrow
        tvArrow.visibility = if (arrow.isEmpty()) View.GONE else View.VISIBLE
        if (chord != null) tvArrow.setTextColor(chordColor(chord.chord))

        // speech bubble only when there is something to say
        tvHint.text = hint
        bubbleRow.visibility = if (hint.isEmpty()) View.GONE else View.VISIBLE

        // "to play [G]" chip in the chord's colour (phase 1 only)
        if (chord != null) {
            tvChordChip.text = chord.chord.label
            tvChordChip.background = roundedBg(chordColor(chord.chord), 12f)
            rowToPlay.visibility = View.VISIBLE
        } else {
            rowToPlay.visibility = View.GONE
        }
        tvFeedback.text = ""
    }

    private fun feedback(text: String, color: Int) {
        tvFeedback.text = text
        tvFeedback.setTextColor(color)
    }

    private fun headerFor(step: Step): String =
        "Phase ${step.phase.number}/${Phase.values().size} · ${step.phase.title}"

    /** e.g. "✓C → ✓G → [A minor] → F" */
    private fun progressionStatus(): String {
        val first = steps.indexOfFirst { it.phase == Phase.PROGRESSION }
        return steps.drop(first).mapIndexed { i, step ->
            val name = step.gesture.chordName
            when {
                first + i < index -> "✓$name"
                first + i == index -> "[$name]"
                else -> name
            }
        }.joinToString("  →  ")
    }

    private fun pressButton(button: Button) {
        button.isPressed = true
        handler.postDelayed({ button.isPressed = false; button.performClick() }, 250)
    }

    /** Shuffles until no item repeats twice in a row, the order differs from the input and doesn't start with [notFirst]. */
    private fun <T> shuffledNoRepeats(items: List<T>, notFirst: T? = null): List<T> {
        repeat(50) {
            val candidate = items.shuffled()
            val ok = candidate.zipWithNext().none { (a, b) -> a == b } &&
                    candidate != items &&
                    candidate.first() != notFirst
            if (ok) return candidate
        }
        return items.shuffled()
    }
}