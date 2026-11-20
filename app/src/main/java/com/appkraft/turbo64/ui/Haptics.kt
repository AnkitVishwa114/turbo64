package com.appkraft.turbo64.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Touch vibration that works the same on every phone. View haptics are skipped by many OEM skins
 * and by the system "touch feedback" switch, so this drives the vibrator directly: predefined
 * tick/click effects where the motor supports them (Android 10+), short one-shots everywhere else.
 * Silent no-op on devices without a vibrator. VIBRATE is a normal permission, so nothing is asked.
 */
object Haptics {
    private const val FILE = "app_settings"
    private const val KEY = "haptics"
    private var vibrator: Vibrator? = null
    private var lastAt = 0L

    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (vibrator == null) {
            val app = context.applicationContext
            vibrator = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION") app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
            }.getOrNull()
        }
        enabled = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
        if (value) click()
    }

    /** Light tick for selection changes: tabs, chips, toggles, list taps. */
    fun tick() = play(Strength.TICK)

    /** Firmer click for actions: buttons, play, download. */
    fun click() = play(Strength.CLICK)

    /** Heavy click for important moments such as starting a game. */
    fun heavy() = play(Strength.HEAVY)

    /** In-game buttons: always crisp, follows the player's own vibration switch instead of the app one. */
    fun gameTap() = play(Strength.GAME, force = true)

    /**
     * How strong each kind of touch feels. Earlier pulses of 14–20 ms at half strength could not
     * be felt on most phones: the common (ERM) vibration motor needs 25–30 ms just to spin up.
     *
     * - [primitive]: scale for the crisp "click" primitive on phones whose motor supports it
     *   (Android 11+, most Pixel / Samsung / OnePlus): short, sharp and never mushy.
     * - [lraMillis]/[amplitude]: phones with strength control (linear motors).
     * - [ermMillis]: phones without strength control (most budget phones): longer, full strength.
     */
    private enum class Strength(val primitive: Float, val lraMillis: Long, val amplitude: Int, val ermMillis: Long) {
        TICK(0.55f, 22, 165, 30),
        GAME(0.8f, 26, 205, 34),
        CLICK(0.8f, 30, 205, 40),
        HEAVY(1f, 45, 255, 60)
    }

    /** Game usage follows the phone's media/game vibration setting, which is on by default. */
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    /** Whether the motor plays the "click" primitive; checked once, it never changes. */
    private var clickPrimitive: Boolean? = null

    private fun effectFor(motor: Vibrator, strength: Strength): VibrationEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val supported = clickPrimitive ?: runCatching {
                motor.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
            }.getOrDefault(false).also { clickPrimitive = it }
            if (supported) {
                return VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, strength.primitive).compose()
            }
        }
        return if (motor.hasAmplitudeControl()) VibrationEffect.createOneShot(strength.lraMillis, strength.amplitude)
        else VibrationEffect.createOneShot(strength.ermMillis, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    private fun play(strength: Strength, force: Boolean = false) {
        if (!enabled && !force) return
        val motor = vibrator ?: return
        // Rapid scrolling through chips can fire many events; keep them from smearing into a buzz.
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < 30) return
        lastAt = now
        runCatching {
            if (!motor.hasVibrator()) return
            val effect = effectFor(motor, strength)
            @Suppress("DEPRECATION")
            runCatching { motor.vibrate(effect, attributes) }.onFailure { motor.vibrate(effect) }
        }
    }

    /** Lets every existing `LocalHapticFeedback` call in the UI use this vibrator. */
    val feedback = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            when (hapticFeedbackType) {
                HapticFeedbackType.LongPress, HapticFeedbackType.Confirm -> click()
                HapticFeedbackType.Reject -> heavy()
                else -> tick()
            }
        }
    }
}
