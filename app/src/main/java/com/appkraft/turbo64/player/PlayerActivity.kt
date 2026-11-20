package com.appkraft.turbo64.player

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.format.DateFormat
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import com.appkraft.turbo64.library.CoverTrim
import com.appkraft.turbo64.library.PlaySessions
import com.appkraft.turbo64.library.SaveStore
import com.appkraft.turbo64.ui.Accent
import com.appkraft.turbo64.ui.AppColors
import com.appkraft.turbo64.ui.Coral
import com.appkraft.turbo64.ui.DarkPalette
import com.appkraft.turbo64.ui.Haptics
import com.appkraft.turbo64.ui.OnAccent
import com.appkraft.turbo64.ui.PlatformPill
import com.appkraft.turbo64.ui.PrimaryButton
import com.appkraft.turbo64.ui.Raised
import com.appkraft.turbo64.ui.SecondaryButton
import com.appkraft.turbo64.ui.TextFaint
import com.appkraft.turbo64.ui.TextMuted
import com.appkraft.turbo64.ui.TextPrimary
import com.appkraft.turbo64.ui.TurboTheme
import com.appkraft.turbo64.ui.Stroke as StrokeColor
import com.appkraft.turbo64.ui.Surface as PanelColor
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The N64 player. What users of other N64 apps complained about is handled here: no ads at all,
 * saves written safely and often (and checked after writing), a resume point on every exit, true
 * multi-touch controls with a floating stick, both orientations without a black frame, and a
 * clear message instead of a black screen when something goes wrong.
 */
class PlayerActivity : ComponentActivity() {
    private var engine: GLRetroView? = null
    private val engineOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private lateinit var saves: File
    private lateinit var audio: AudioManager
    private lateinit var focus: AudioFocusRequest
    private var vibrator: Vibrator? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    /** File writes happen here, never on the main or the render thread. */
    private val io = Executors.newSingleThreadExecutor()
    private val pressed = mutableSetOf<Int>()
    private var gameId = ""
    /** When the game last started running (0 while paused); play time is written on every pause. */
    private var runningSince = 0L
    private var ready by mutableStateOf(false)
    private var menu by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var notice by mutableStateOf<String?>(null)
    private var undoVisible by mutableStateOf(false)
    private var resumed = false
    private var destroyed = false
    private var snapshotPending by mutableStateOf(false)
    private var pauseRequested = false
    private val preferences by lazy { getSharedPreferences("player", Context.MODE_PRIVATE) }

    // Controls
    private var scale by mutableFloatStateOf(1f)
    private var opacity by mutableFloatStateOf(0.7f)
    private var haptics by mutableStateOf(true)
    private var swapButtons by mutableStateOf(false)
    private var sensitivity by mutableFloatStateOf(1f)
    private var zToggle by mutableStateOf(false)
    /** Per game: the C buttons shown as a second stick (camera games). */
    private var cStick by mutableStateOf(false)
    /** Per game: the D-pad is the big control and the stick the small one (fighting and puzzle games). */
    /** Left-hand control for this game: "stick" (most games), "dpad" (fighting, wrestling) or "both". */
    private var leftControl by mutableStateOf("stick")
    /** Controls the user hid in "Edit layout" (by name, both orientations). */
    private var hiddenControls by mutableStateOf(emptySet<String>())
    private var gamepadActive by mutableStateOf(false)
    private var controlsHidden by mutableStateOf(false)
    private var editing by mutableStateOf(false)
    private var layoutVersion by mutableIntStateOf(0)

    // Screen
    private var portrait by mutableStateOf(false)
    /** "landscape" (both sides), "portrait" or "auto". */
    private var orientationMode by mutableStateOf("landscape")
    /** "4:3", "16:9" or "fill". */
    private var aspect by mutableStateOf("4:3")
    /** Whether the engine was started in widescreen; switching needs a restart of the game. */
    private var bootWide = false
    private var resolution by mutableStateOf("640x480")
    private var filter by mutableStateOf("smooth")
    private var brightness by mutableFloatStateOf(0f)
    /** Landscape only: enlarges the picture past the screen edges to hide black borders. */
    private var zoom by mutableFloatStateOf(1f)
    /** Shows the whole picture instead of trimming the edges a TV used to hide. */
    private var fullPicture by mutableStateOf(false)
    private var roomyDevice = false

    // Speed, sound, pak
    private var fastForward by mutableStateOf(false)
    private var ffSpeed by mutableIntStateOf(2)
    private var muteFastForward by mutableStateOf(true)
    private var keepOtherAudio by mutableStateOf(false)
    /** Per game: "memory" (Controller Pak saves) or "rumble". */
    private var pak by mutableStateOf("memory")

    private var cheats by mutableStateOf<List<Cheat>>(emptyList())
    private var cheatSlots = 0
    private var cheatsOpen by mutableStateOf(false)
    private var slotTimes by mutableStateOf(List(SaveStore.SLOTS) { 0L })
    private var slotShots by mutableStateOf<Map<Int, ImageBitmap>>(emptyMap())

    // C buttons: touch, gamepad keys and the gamepad's right stick all end up on the engine's right stick.
    private var touchC = emptySet<Int>()
    private val padC = mutableSetOf<Int>()
    private var cStickX = 0f
    private var cStickY = 0f
    private var triggerZ = false

    // Gamepad: which pad button does what, the set-up wizard and the live test.
    private var padMap: Map<Int, String> = defaultPadMap
    private var controllerOpen by mutableStateOf(false)
    /** Index into [padActions] of the button being asked for, or -1 when the wizard is not running. */
    private var wizardStep by mutableIntStateOf(-1)
    private var wizardMap = mutableMapOf<Int, String>()
    private var padLive by mutableStateOf(emptySet<String>())
    private var padStick by mutableStateOf(Offset.Zero)
    private var padCStick by mutableStateOf(Offset.Zero)
    private var padName by mutableStateOf<String?>(null)

    private val noisyReceiver = object : BroadcastReceiver() {
        // Headphones pulled out: pause instead of blasting the game from the speaker.
        override fun onReceive(context: Context?, intent: Intent?) { if (ready && !menu) pauseGame() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppColors.override = DarkPalette
        Haptics.init(this)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()
        preferSixtyHz()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        gameId = intent.getStringExtra(EXTRA_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val gameFile = intent.getStringExtra(EXTRA_PATH)?.let(::File)
        roomyDevice = runCatching {
            val info = ActivityManager.MemoryInfo()
            (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            info.totalMem >= 5_500_000_000L
        }.getOrDefault(false)
        loadPreferences()
        portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { change -> if (change < 0 && ready) pauseGame() }
            .build()
        ContextCompat.registerReceiver(this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        val store = SaveStore(this)
        saves = store.folder(gameId)
        refreshSlots()
        if (gameId.isBlank() || gameFile == null || !gameFile.isFile) {
            error = "This game is not ready. Go back and tap it again."
        } else {
            store.rotateBackups(gameId)
            try {
                bootWide = aspect == "16:9"
                val data = GLRetroViewData(this).apply {
                    coreFilePath = File(applicationInfo.nativeLibraryDir, CORE_FILE).absolutePath
                    gameFilePath = gameFile.absolutePath
                    systemDirectory = File(filesDir, "system").apply { mkdirs() }.absolutePath
                    savesDirectory = saves.absolutePath
                    saveRAMState = File(saves, SaveStore.BATTERY).takeIf { it.isFile && it.length() in 1 until 32L * 1024 * 1024 }?.readBytes()
                    preferLowLatencyAudio = true
                    skipDuplicateFrames = true
                    rumbleEventsEnabled = true
                    shader = shaderFor(filter)
                    variables = arrayOf(
                        Variable("mupen64plus-rdp-plugin", "gliden64"),
                        Variable("mupen64plus-rsp-plugin", "hle"),
                        Variable("mupen64plus-cpucore", "dynamic_recompiler"),
                        Variable("mupen64plus-43screensize", resolution),
                        Variable("mupen64plus-169screensize", wideSize(resolution)),
                        Variable("mupen64plus-aspect", if (bootWide) "16:9 adjusted" else "4:3"),
                        Variable("mupen64plus-BilinearMode", "standard"),
                        Variable("mupen64plus-FrameDuping", "True"),
                        Variable("mupen64plus-EnableFBEmulation", "True"),
                        Variable("mupen64plus-EnableOverscan", if (fullPicture) "Disabled" else "Enabled"),
                        Variable("mupen64plus-pak1", pak)
                    )
                }
                val view = GLRetroView(this, data)
                engine = view
                lifecycleScope.launch {
                    view.getGLRetroErrors().collect { code ->
                        when (code) {
                            GLRetroView.ERROR_CHEAT -> { notice = "One cheat code isn't valid"; return@collect }
                            GLRetroView.ERROR_SERIALIZATION -> { notice = "That save state could not be used"; return@collect }
                        }
                        ready = false
                        error = when (code) {
                            GLRetroView.ERROR_LOAD_GAME -> "This file can't be opened. It may be damaged or not an N64 game."
                            GLRetroView.ERROR_LOAD_LIBRARY -> "The emulator could not start on this device."
                            GLRetroView.ERROR_GL_NOT_COMPATIBLE -> "This device does not support the graphics mode the emulator needs (OpenGL ES 3)."
                            else -> "The game stopped. Your last saved progress is kept."
                        }
                    }
                }
                lifecycleScope.launch {
                    view.getGLRetroEvents().collect { event ->
                        if (event == GLRetroView.GLRetroEvents.SurfaceCreated && !ready) {
                            ready = true
                            if (resumed && !menu) runningSince = System.currentTimeMillis()
                            cheats = Cheat.load(File(saves, CHEATS_FILE))
                            applyCheats()
                            if (!preferences.getBoolean("tipShown", false)) {
                                preferences.edit().putBoolean("tipShown", true).apply()
                                notice = "Tap the menu button for saves and settings"
                            }
                            val resume = File(saves, SaveStore.AUTOSAVE)
                            if (resume.isFile && resume.length() < 96L * 1024 * 1024) {
                                // States are several MB; read them off the main thread so the screen never stalls.
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val bytes = runCatching { resume.readBytes() }.getOrNull() ?: return@launch
                                    view.queueEvent {
                                        val ok = runCatching { view.unserializeState(bytes, false) }.getOrDefault(false)
                                        mainHandler.post { notice = if (ok) "Continued where you left off" else null }
                                    }
                                }
                            }
                        }
                    }
                }
                lifecycleScope.launch {
                    view.getRumbleEvents().collect { event -> if (pak == "rumble" && haptics) rumble(maxOf(event.strengthWeak, event.strengthStrong)) }
                }
                engineOwner.lifecycle.addObserver(view)
                engineOwner.registry.currentState = Lifecycle.State.CREATED
                lifecycleScope.launch {
                    // Never leave a black screen: big games load slowly on budget phones, so allow 45 s.
                    delay(45_000)
                    if (!ready && error == null && !destroyed) error = "This game is taking too long to start. Go back and try again."
                }
            } catch (_: Throwable) {
                error = "The emulator could not start. Please open the game again."
            }
        }
        setContent { TurboTheme { PlayerScreen(title) } }
        lifecycleScope.launch {
            // The in-game save every 30 s and a resume point every 60 s: a crash or a killed app loses a minute at most.
            var tick = 0
            while (true) {
                delay(30_000)
                tick++
                if (ready && !menu && !editing && resumed) {
                    // A fresh library picture every minute too, so a crash or a killed app still leaves one.
                    if (tick % 2 == 0) capture(File(saves, SaveStore.COVER))
                    saveSnapshot(state = if (tick % 2 == 0) SaveStore.AUTOSAVE else null)
                }
            }
        }
    }

    private fun gameKey(name: String) = "${name}_$gameId"

    private fun loadPreferences() {
        scale = preferences.getFloat("scale", 1f).coerceIn(0.75f, 1.25f)
        opacity = preferences.getFloat("opacity", 0.7f).coerceIn(0.25f, 0.95f)
        haptics = preferences.getBoolean("haptics", true)
        swapButtons = preferences.getBoolean("swapButtons", false)
        sensitivity = preferences.getFloat("sensitivity", 1f).coerceIn(0.7f, 1.3f)
        zToggle = preferences.getBoolean("zToggle", false)
        cStick = preferences.getBoolean(gameKey("cStick"), false)
        // Older versions had an on/off "D-pad as the main control"; it becomes the D-pad choice.
        leftControl = preferences.getString(gameKey("left"), null)?.takeIf { it in setOf("stick", "dpad", "both") }
            ?: if (preferences.getBoolean(gameKey("dpadMain"), false)) "dpad" else "stick"
        hiddenControls = preferences.getStringSet("hidden", emptySet()).orEmpty().toSet()
        pak = preferences.getString(gameKey("pak"), "memory").takeIf { it == "rumble" } ?: "memory"
        ffSpeed = preferences.getInt("ffSpeed", 2).coerceIn(2, 4)
        muteFastForward = preferences.getBoolean("muteFastForward", true)
        keepOtherAudio = preferences.getBoolean("keepOtherAudio", false)
        filter = preferences.getString("filter", "smooth") ?: "smooth"
        aspect = preferences.getString("aspect", "4:3").takeIf { it in setOf("4:3", "16:9", "fill") } ?: "4:3"
        // Phones with plenty of memory start at double resolution; the rest at the console's own.
        resolution = preferences.getString("resolution", null).takeIf { it in resolutions } ?: if (roomyDevice) "640x480" else "320x240"
        if (!roomyDevice && resolution == "960x720") resolution = "640x480"
        brightness = preferences.getFloat("brightness", 0f).coerceIn(0f, 1f)
        zoom = preferences.getFloat("zoom", 1f).coerceIn(1f, 1.25f)
        fullPicture = preferences.getBoolean("fullPicture", false)
        padMap = parsePadMap(preferences.getString("padMap", null)) ?: defaultPadMap
        orientationMode = preferences.getString("orientation", "landscape") ?: "landscape"
        applyOrientation()
        applyBrightness()
    }

    private fun applyOrientation() {
        requestedOrientation = when (orientationMode) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "auto" -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun setOrientation(mode: String) {
        orientationMode = mode
        preferences.edit().putString("orientation", mode).apply()
        applyOrientation()
    }

    /** Quick rotate: lock to the other orientation right away. */
    private fun rotateNow() = setOrientation(if (portrait) "landscape" else "portrait")

    /** 0 follows the phone's own brightness; above that the screen is lifted for dark games. */
    private fun applyBrightness() {
        window.attributes = window.attributes.apply {
            screenBrightness = if (brightness <= 0.01f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else 0.4f + brightness * 0.6f
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val nowPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
        if (nowPortrait != portrait) {
            // A finger held during the turn would otherwise leave a button stuck down.
            releaseButtons()
            portrait = nowPortrait
        }
    }

    // ---- System bars, refresh rate ----------------------------------------------------------

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** On 90/120 Hz panels a 60 Hz mode keeps frame pacing even and saves battery. */
    private fun preferSixtyHz() {
        runCatching {
            @Suppress("DEPRECATION") val display = windowManager.defaultDisplay
            val current = display.mode
            val best = display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .minByOrNull { abs(it.refreshRate - 60f) } ?: return
            if (abs(best.refreshRate - 60f) < 1.5f && best.modeId != current.modeId) {
                window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
            }
        }
    }

    // ---- Lifecycle (engine destroyed only after its render thread stopped) ---------------------

    override fun onResume() {
        AppColors.override = DarkPalette
        super.onResume()
        resumed = true
        if (!menu && !editing && error == null) resumeEngine()
    }

    override fun onPause() {
        resumed = false
        pauseGame()
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        releaseButtons()
        runCatching { unregisterReceiver(noisyReceiver) }
        runCatching { vibrator?.cancel() }
        val view = engine
        // Sound off first: freeing the engine while the audio callback still read its buffer crashed on close.
        runCatching { view?.audioEnabled = false }
        if (view != null && view.isAttachedToWindow) {
            // Let a save queued on pause finish on the render thread before shutting down.
            val idle = CountDownLatch(1)
            view.queueEvent { idle.countDown() }
            idle.await(1500, TimeUnit.MILLISECONDS)
            // Freeing the engine while the render thread still drew a frame crashed; wait for the
            // detach, which joins the render thread first.
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) = destroyEngine()
            })
        } else destroyEngine()
        audio.abandonAudioFocusRequest(focus)
        // Queued writes still finish; nothing new is accepted.
        io.shutdown()
        super.onDestroy()
    }

    private fun destroyEngine() {
        if (engineOwner.registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            engineOwner.registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    private fun resumeEngine() {
        if (destroyed || error != null || !resumed) return
        pauseRequested = false
        // With "keep other audio playing" the app never takes the audio focus, so music apps keep going.
        if (!keepOtherAudio) audio.requestAudioFocus(focus)
        engineOwner.registry.currentState = Lifecycle.State.RESUMED
        if (ready) runningSince = System.currentTimeMillis()
    }

    /** Opening the menu, leaving or backgrounding always writes the save and a resume point. */
    private fun pauseGame() {
        releaseButtons()
        editing = false
        menu = true
        pauseRequested = true
        recordPlayTime()
        if (engineOwner.registry.currentState == Lifecycle.State.RESUMED) {
            if (ready) {
                // The library picture is taken even while a timed save is still being written.
                capture(File(saves, SaveStore.COVER))
                if (!snapshotPending) saveSnapshot(state = SaveStore.AUTOSAVE, pauseAfter = true)
            } else engineOwner.registry.currentState = Lifecycle.State.CREATED
        }
        audio.abandonAudioFocusRequest(focus)
    }

    /** Writes the play time since the game last started running; the library picks it up. */
    private fun recordPlayTime() {
        val since = runningSince
        runningSince = 0L
        if (since > 0L && gameId.isNotBlank()) PlaySessions.record(this, gameId, System.currentTimeMillis() - since)
    }

    // ---- Saving ------------------------------------------------------------------------------

    /**
     * Writes the in-game save, plus a state file named [state] when given. The engine is read on
     * its render thread (fast, memory only); the files are written on a background thread, so a
     * save never makes the game stutter or the screen hang.
     */
    private fun saveSnapshot(state: String?, pauseAfter: Boolean = false, message: String? = null, done: (() -> Unit)? = null) {
        val view = engine ?: return
        if (!ready || snapshotPending) return
        snapshotPending = true
        view.queueEvent {
            val ram = runCatching { view.serializeSRAM(false) }.getOrNull()
            val bytes = if (state != null) runCatching { view.serializeState(false) }.getOrNull() else null
            val write = Runnable {
                val result = runCatching {
                    if (ram != null && ram.isNotEmpty()) atomicWrite(File(saves, SaveStore.BATTERY), ram)
                    if (state != null) {
                        check(bytes != null && bytes.isNotEmpty()) { "The game could not be saved right now." }
                        atomicWrite(File(saves, state), bytes)
                    }
                }
                mainHandler.post {
                    snapshotPending = false
                    if (destroyed) return@post
                    if (pauseAfter || pauseRequested) engineOwner.registry.currentState = Lifecycle.State.CREATED
                    if (result.isFailure) notice = "Save failed. Free some space and try again."
                    else if (message != null) notice = message
                    refreshSlots()
                    done?.invoke()
                }
            }
            if (io.isShutdown) write.run() else runCatching { io.execute(write) }.onFailure { write.run() }
        }
    }

    /** A small picture of the game screen for a save slot or the library card. Never blocks; a miss is fine. */
    private fun capture(target: File) {
        val view = engine ?: return
        if (!ready || view.width <= 0 || view.height <= 0 || !view.isAttachedToWindow) return
        runCatching {
            val width = 480
            val height = (width * view.height / view.width.toFloat()).roundToInt().coerceIn(1, 1080)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(view, bitmap, { result ->
                if (result != PixelCopy.SUCCESS || io.isShutdown) { bitmap.recycle(); return@request }
                runCatching {
                    io.execute {
                        runCatching {
                            // Only the game picture, without the black bars around it.
                            val picture = runCatching { CoverTrim.trim(bitmap) }.getOrDefault(bitmap)
                            // A black loading or fade frame never replaces the library picture.
                            if (target.name == SaveStore.COVER && runCatching { CoverTrim.mostlyDark(picture) }.getOrDefault(false)) {
                                if (picture !== bitmap) picture.recycle()
                                return@runCatching
                            }
                            val pending = File(target.parentFile, target.name + ".tmp")
                            pending.outputStream().use { picture.compress(Bitmap.CompressFormat.JPEG, 84, it) }
                            pending.renameTo(target)
                            if (picture !== bitmap) picture.recycle()
                        }
                        bitmap.recycle()
                        mainHandler.post { if (!destroyed) refreshSlots() }
                    }
                }
            }, mainHandler)
        }
    }

    private fun slotFile(slot: Int) = File(saves, "slot$slot.state")
    private fun slotShot(slot: Int) = File(saves, "slot$slot.jpg")

    private fun refreshSlots() {
        if (!::saves.isInitialized) return
        slotTimes = List(SaveStore.SLOTS) { index -> slotFile(index + 1).takeIf { it.isFile }?.lastModified() ?: 0L }
        lifecycleScope.launch {
            slotShots = withContext(Dispatchers.IO) {
                (1..SaveStore.SLOTS).mapNotNull { slot ->
                    val file = slotShot(slot).takeIf { it.isFile && slotFile(slot).isFile } ?: return@mapNotNull null
                    runCatching { BitmapFactory.decodeFile(file.absolutePath)?.let(CoverTrim::trim)?.asImageBitmap() }.getOrNull()?.let { slot to it }
                }.toMap()
            }
        }
    }

    private fun saveToSlot(slot: Int) {
        menu = false
        resumeEngine()
        // The picture is taken a moment later, once the menu has gone and the game is on screen again.
        mainHandler.postDelayed({ if (!destroyed) capture(slotShot(slot)) }, 350)
        saveSnapshot(state = "slot$slot.state", message = "Saved to slot $slot")
    }

    private fun loadSlot(slot: Int) = loadStateFile(slotFile(slot), "Slot $slot")

    private fun quickSave() {
        if (!ready || snapshotPending) return
        saveSnapshot(state = QUICK_FILE, message = "Quick saved")
    }

    private fun quickLoad() = loadStateFile(File(saves, QUICK_FILE), "Quick save")

    /**
     * Loading a state also rewinds the in-game save (it is part of the state). The current
     * in-game save is backed up first, and the moment before the load is kept for a 5 s undo.
     */
    private fun loadStateFile(file: File, label: String, isUndo: Boolean = false) {
        if (!file.isFile) { notice = "$label is empty"; return }
        File(saves, SaveStore.BATTERY).takeIf { it.isFile }?.let { it.copyTo(File(saves, "${SaveStore.BATTERY}.before-load"), overwrite = true) }
        menu = false
        resumeEngine()
        val view = engine ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val bytes = runCatching { file.readBytes() }.getOrNull()
            view.queueEvent {
                val before = if (isUndo) null else runCatching { view.serializeState(false) }.getOrNull()
                val ok = bytes != null && runCatching { view.unserializeState(bytes, false) }.getOrDefault(false)
                if (ok && before != null && before.isNotEmpty() && !io.isShutdown) {
                    runCatching { io.execute { runCatching { atomicWrite(File(saves, UNDO_FILE), before) } } }
                }
                mainHandler.post {
                    notice = when {
                        !ok -> "This save could not be loaded"
                        isUndo -> "Back where you were"
                        else -> "$label loaded"
                    }
                    if (ok && !isUndo && before != null) {
                        undoVisible = true
                        mainHandler.postDelayed({ undoVisible = false }, 5_000)
                    }
                }
            }
        }
    }

    private fun undoLoad() {
        undoVisible = false
        loadStateFile(File(saves, UNDO_FILE), "Undo", isUndo = true)
    }

    private fun restartGame() {
        menu = false
        resumeEngine()
        engine?.queueEvent { runCatching { engine?.reset(false) } }
        notice = "Game restarted"
    }

    // ---- Input -------------------------------------------------------------------------------

    private fun toggleFastForward() {
        if (!ready || menu) return
        fastForward = !fastForward
        engine?.frameSpeed = if (fastForward) ffSpeed else 1
        engine?.audioEnabled = !(fastForward && muteFastForward)
        notice = if (fastForward) "Fast-forward ${ffSpeed}×" else "Normal speed"
    }

    private fun inputBlocked() = !ready || menu || editing || error != null

    /** Presses or releases one engine button (an Android key code LibretroDroid understands). */
    private fun key(code: Int, down: Boolean) {
        if (inputBlocked() && down) return
        val mapped = if (swapButtons) when (code) {
            N64_A -> N64_B
            N64_B -> N64_A
            else -> code
        } else code
        if (down) {
            if (pressed.add(mapped)) engine?.sendKeyEvent(KeyEvent.ACTION_DOWN, mapped, 0)
        } else if (pressed.remove(mapped)) engine?.sendKeyEvent(KeyEvent.ACTION_UP, mapped, 0)
    }

    private fun keys(all: Collection<Int>, down: Set<Int>) = all.forEach { key(it, it in down) }

    private fun stick(x: Float, y: Float) {
        val blocked = inputBlocked()
        engine?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, if (blocked) 0f else x, if (blocked) 0f else y, 0)
    }

    /** The C buttons are the engine's right stick: touch buttons, gamepad buttons and its right stick add up. */
    private fun sendC() {
        val blocked = inputBlocked()
        val (x, y) = cVector(touchC + padC)
        engine?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
            if (blocked) 0f else (x + cStickX).coerceIn(-1f, 1f), if (blocked) 0f else (y + cStickY).coerceIn(-1f, 1f), 0)
    }

    private fun releaseButtons() {
        pressed.toList().forEach { engine?.sendKeyEvent(KeyEvent.ACTION_UP, it, 0) }
        pressed.clear()
        touchC = emptySet(); padC.clear(); cStickX = 0f; cStickY = 0f; triggerZ = false
        engine?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f, 0)
        engine?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, 0f, 0f, 0)
    }

    private fun rumble(strength: Float) {
        val motor = vibrator ?: return
        runCatching {
            if (strength <= 0.02f) motor.cancel()
            else motor.vibrate(VibrationEffect.createOneShot(60, (strength * 255).roundToInt().coerceIn(1, 255)))
        }
    }

    // ---- Cheats (GameShark codes, stored per game next to its saves) ---------------------------

    private fun applyCheats() {
        val view = engine ?: return
        if (!ready) return
        val count = maxOf(cheatSlots, cheats.size)
        for (index in 0 until count) {
            val cheat = cheats.getOrNull(index)
            runCatching { view.setCheat(index, cheat?.enabled == true, cheat?.code ?: "", false) }
        }
        cheatSlots = cheats.size
    }

    private fun updateCheats(list: List<Cheat>) {
        cheats = list
        Cheat.save(File(saves, CHEATS_FILE), list)
        applyCheats()
    }

    // ---- Gamepads ------------------------------------------------------------------------------

    // Overriding (and calling super on) an Activity method is fine; lint mistakes it for a restricted call.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val fromPad = event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        if (!fromPad || event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        val down = event.action == KeyEvent.ACTION_DOWN
        if (!gamepadActive && down) gamepadActive = true
        if (padName == null) padName = event.device?.name
        if (controllerOpen) {
            // The controller screen is open: show the press, or take it as the answer in the set-up wizard.
            if (wizardStep >= 0) {
                if (down && event.repeatCount == 0) wizardAnswer(event.keyCode)
            } else padMap[event.keyCode]?.let { action -> padLive = if (down) padLive + action else padLive - action }
            return true
        }
        // Mode, Select, or Start while paused: open and close the menu. Never bound to a game button.
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE || event.keyCode == KeyEvent.KEYCODE_BUTTON_SELECT ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_START && menu) {
            if (down && event.repeatCount == 0 && !snapshotPending) {
                if (menu) { menu = false; resumeEngine() } else pauseGame()
            }
            return true
        }
        // Default for Xbox-style pads: A = A, X = B, B and Y = C-down and C-left, either trigger = Z,
        // right stick = C buttons. The user's own mapping from the set-up wizard replaces it.
        val action = padMap[event.keyCode]
        if (action != null) { padAction(action, down); return true }
        if (event.keyCode in dpadKeys) { key(event.keyCode, down); return true }
        return super.dispatchKeyEvent(event)
    }

    private fun padAction(action: String, down: Boolean) {
        fun c(button: Int) { if (down) padC += button else padC -= button; sendC() }
        when (action) {
            "A" -> key(N64_A, down)
            "B" -> key(N64_B, down)
            "Z" -> key(N64_Z, down)
            "L" -> key(KeyEvent.KEYCODE_BUTTON_L1, down)
            "R" -> key(KeyEvent.KEYCODE_BUTTON_R1, down)
            "Start" -> key(KeyEvent.KEYCODE_BUTTON_START, down)
            "C-up" -> c(C_UP)
            "C-down" -> c(C_DOWN)
            "C-left" -> c(C_LEFT)
            "C-right" -> c(C_RIGHT)
        }
    }

    private fun startWizard() {
        wizardMap = mutableMapOf()
        padLive = emptySet()
        wizardStep = 0
    }

    /** One pad button per N64 button; a button already used in this run is ignored so nothing is bound twice. */
    private fun wizardAnswer(keyCode: Int) {
        if (keyCode in wizardMap || keyCode in dpadKeys) return
        wizardMap[keyCode] = padActions[wizardStep]
        if (haptics) Haptics.gameTap()
        wizardNext()
    }

    private fun wizardNext() {
        if (wizardStep + 1 < padActions.size) { wizardStep++; return }
        wizardStep = -1
        if (wizardMap.isNotEmpty()) {
            padMap = wizardMap.toMap()
            preferences.edit().putString("padMap", padMap.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
            notice = "Controller saved"
        }
    }

    private fun resetPadMap() {
        wizardStep = -1
        padMap = defaultPadMap
        preferences.edit().remove("padMap").apply()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK || event.action != MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(event)
        }
        fun axis(id: Int): Float = event.getAxisValue(id).let { if (abs(it) < 0.12f) 0f else it }
        val x = axis(MotionEvent.AXIS_X)
        val y = axis(MotionEvent.AXIS_Y)
        if (!gamepadActive && (x != 0f || y != 0f)) gamepadActive = true
        if (controllerOpen) {
            if (padName == null) padName = event.device?.name
            padStick = Offset(x, y)
            padCStick = Offset(axis(MotionEvent.AXIS_Z), axis(MotionEvent.AXIS_RZ))
            return true
        }
        stick(x * sensitivity, y * sensitivity)
        cStickX = axis(MotionEvent.AXIS_Z).let { if (abs(it) > 0.45f) it else 0f }
        cStickY = axis(MotionEvent.AXIS_RZ).let { if (abs(it) > 0.45f) it else 0f }
        sendC()
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        key(KeyEvent.KEYCODE_DPAD_LEFT, hatX < -0.5f); key(KeyEvent.KEYCODE_DPAD_RIGHT, hatX > 0.5f)
        key(KeyEvent.KEYCODE_DPAD_UP, hatY < -0.5f); key(KeyEvent.KEYCODE_DPAD_DOWN, hatY > 0.5f)
        val trigger = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE),
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)) > 0.4f
        if (trigger != triggerZ) { triggerZ = trigger; key(N64_Z, trigger) }
        return true
    }

    // ---- UI ----------------------------------------------------------------------------------

    private val padStyle get() = PadStyle(opacity, haptics)

    @Composable
    private fun PlayerScreen(title: String) {
        BackHandler {
            when {
                error != null -> finish()
                controllerOpen -> { wizardStep = -1; controllerOpen = false }
                editing -> finishEditing()
                menu -> if (!snapshotPending) finish()
                else -> pauseGame()
            }
        }
        LaunchedEffect(notice) { if (notice != null) { delay(2200); notice = null } }
        val showControls = !gamepadActive && !controlsHidden || editing
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) {
                    // Any touch brings the on-screen controls back after a gamepad hid them.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (gamepadActive && event.changes.any { it.pressed }) gamepadActive = false
                        }
                    }
                }
                .windowInsetsPadding(WindowInsets.displayCutout)
        ) {
            GameAndControls(showControls)

            Row(Modifier.align(Alignment.TopCenter).padding(top = 52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(notice != null && !menu, enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it }, exit = fadeOut(tween(200))) {
                    Text(
                        notice.orEmpty(), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.background(PanelColor.copy(alpha = 0.94f), RoundedCornerShape(50))
                            .border(1.dp, StrokeColor, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                AnimatedVisibility(undoVisible && !menu, enter = fadeIn(tween(180)), exit = fadeOut(tween(200))) {
                    Text("Undo", color = OnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Accent).clickable { undoLoad() }.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }

            AnimatedVisibility(!ready && error == null && engine != null, enter = fadeIn(), exit = fadeOut(tween(350))) {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        com.appkraft.turbo64.ui.BrandSpinner(84.dp, Modifier.padding(bottom = 10.dp))
                        Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 24.dp))
                        Text("Starting game…", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }

            AnimatedVisibility(editing, modifier = Modifier.align(Alignment.Center), enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) { EditBar() }

            AnimatedVisibility(
                menu && error == null,
                enter = fadeIn(tween(180)) + slideInVertically(tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 10 } +
                    scaleIn(tween(260), initialScale = 0.97f),
                exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.98f)
            ) { PauseMenu(title) }

            if (cheatsOpen) CheatsPanel(cheats, onChange = ::updateCheats, onClose = { cheatsOpen = false })
            if (controllerOpen) ControllerPanel()

            error?.let { message ->
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.widthIn(max = 420.dp).padding(24.dp).background(PanelColor, RoundedCornerShape(24.dp))
                            .border(1.dp, StrokeColor, RoundedCornerShape(24.dp)).padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Coral, modifier = Modifier.size(30.dp))
                        Text("Game couldn't start", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(message, color = TextMuted, fontSize = 13.sp)
                        PrimaryButton("Back to library", { finish() }, height = 42.dp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                }
            }
        }
    }

    /**
     * The game view and the controls for the current orientation. The GL view is placed from a
     * single call site and only moved and resized: removing it from the window on rotation shut
     * down its GL thread and left the game screen black.
     */
    @Composable
    private fun GameAndControls(showControls: Boolean) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight
            val ratio = when {
                aspect == "fill" && !portrait -> screenWidth / screenHeight
                bootWide -> 16f / 9f
                else -> 4f / 3f
            }
            val gameWidth: Dp
            val gameTop: Dp
            if (portrait) {
                val maxGameHeight = if (showControls) screenHeight * 0.5f else screenHeight
                gameWidth = minOf(screenWidth, maxGameHeight * ratio)
                gameTop = if (showControls) 0.dp else (screenHeight - gameWidth / ratio) / 2
            } else {
                gameWidth = minOf(screenWidth, screenHeight * ratio) * (if (aspect == "fill") 1f else zoom)
                gameTop = (screenHeight - gameWidth / ratio) / 2
            }
            val gameHeight = gameWidth / ratio
            val gameLeft = (screenWidth - gameWidth) / 2

            Box(Modifier.offset(gameLeft, gameTop).size(gameWidth, gameHeight)) {
                engine?.let { view -> AndroidView(factory = { view }, modifier = Modifier.fillMaxSize()) }
            }

            AnimatedVisibility(showControls, enter = fadeIn(tween(220)), exit = fadeOut(tween(160))) {
                if (portrait) {
                    val unit = (screenWidth.value / 400f).coerceIn(0.8f, 1.2f) * scale
                    Controls(Modifier.fillMaxSize().padding(top = gameHeight + gameTop).windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 14.dp, vertical = 8.dp), unit)
                } else {
                    val unit = (screenHeight.value / 360f).coerceIn(0.85f, 1.2f) * scale
                    Controls(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 14.dp, vertical = 10.dp), unit)
                }
            }
            if (!showControls) GamepadShortcuts(Modifier.align(Alignment.TopStart))
        }
    }

    /**
     * One movable control. Its place is an anchor plus an offset the user can change in
     * "Edit layout" by dragging; offsets are kept apart for portrait and landscape.
     */
    @Composable
    private fun BoxScope.Slot(name: String, anchor: Alignment, x: Dp = 0.dp, y: Dp = 0.dp, hideable: Boolean = true, content: @Composable () -> Unit) {
        val hidden = hideable && name in hiddenControls
        if (hidden && !editing) return
        val id = (if (portrait) "p_" else "l_") + name
        var moved by remember(id, layoutVersion) {
            mutableStateOf(Offset(preferences.getFloat("pos_${id}_x", 0f), preferences.getFloat("pos_${id}_y", 0f)))
        }
        val density = LocalDensity.current.density
        Box(Modifier.align(anchor).offset(x + moved.x.dp, y + moved.y.dp)) {
            Box(Modifier.graphicsLayer { alpha = if (hidden) 0.3f else 1f }) { content() }
            if (editing) {
                Box(Modifier.matchParentSize().border(1.5.dp, PadColors.Yellow, RoundedCornerShape(12.dp))
                    .background(PadColors.Yellow.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .pointerInput(id) {
                        detectDragGestures(onDragEnd = {
                            preferences.edit().putFloat("pos_${id}_x", moved.x).putFloat("pos_${id}_y", moved.y).apply()
                        }) { change, drag ->
                            change.consume()
                            moved = Offset((moved.x + drag.x / density).coerceIn(-600f, 600f), (moved.y + drag.y / density).coerceIn(-600f, 600f))
                        }
                    })
                // Hide or show this control; hidden ones stay faint here so they can come back.
                if (hideable) Box(
                    Modifier.align(Alignment.TopEnd).offset(10.dp, (-10).dp).size(26.dp).clip(CircleShape)
                        .background(if (hidden) PadColors.Yellow else PanelColor).border(1.dp, StrokeColor, CircleShape)
                        .clickable { toggleHidden(name) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = if (hidden) "Show this control" else "Hide this control",
                        tint = if (hidden) Color.Black else TextPrimary, modifier = Modifier.size(15.dp))
                }
            }
        }
    }

    /** All on-screen controls. The same pieces in both orientations; only their anchors differ. */
    @Composable
    private fun Controls(modifier: Modifier, unit: Float) {
        val style = padStyle
        val big = (132 * unit).dp
        val small = (88 * unit).dp
        val mainDpad = leftControl == "dpad"
        val abSize = (128 * unit).dp
        val cSize = (104 * unit).dp
        val pill = Modifier.width((72 * unit).dp).height((34 * unit).dp)
        Box(modifier) {
            Slot("quick", Alignment.TopCenter, hideable = false) { QuickActions(if (portrait) 32.dp else 34.dp) }
            Slot("l", Alignment.TopStart, y = if (portrait) 44.dp else 0.dp) {
                HoldButton("L", style, pill, RoundedCornerShape(50)) { key(KeyEvent.KEYCODE_BUTTON_L1, it) }
            }
            Slot("r", Alignment.TopEnd, y = if (portrait) 44.dp else 0.dp) {
                HoldButton("R", style, pill, RoundedCornerShape(50)) { key(KeyEvent.KEYCODE_BUTTON_R1, it) }
            }
            // One main control under the left thumb: the stick, or the D-pad for games that use it.
            // "Both" keeps a small D-pad above the stick, as on the real controller.
            if (mainDpad) Slot("dpad", Alignment.BottomStart, hideable = false) {
                DPad(style, Modifier.size(big * 0.92f)) { keys(dpadKeys, it) }
            } else Slot("stick", Alignment.BottomStart, hideable = false) {
                AnalogStick(style, sensitivity, Modifier.size(big)) { x, y -> stick(x, y) }
            }
            if (leftControl == "both") {
                Slot("dpad_small", if (portrait) Alignment.BottomStart else Alignment.TopStart, x = 8.dp,
                    y = if (portrait) -(big + 14.dp) else (46 * unit).dp) {
                    DPad(style, Modifier.size(small)) { keys(dpadKeys, it) }
                }
            } else {
                // Swaps stick and D-pad in one tap, right next to the thumb, without opening the menu.
                Slot("swap", Alignment.BottomStart, x = (if (mainDpad) big * 0.92f else big) + 4.dp,
                    y = -((if (mainDpad) big * 0.92f else big) - (32 * unit).dp)) {
                    GlassIcon(Icons.Rounded.SwapHoriz, if (mainDpad) "Use the stick" else "Use the D-pad", style, size = (32 * unit).dp) { swapLeftControl() }
                }
            }
            Slot("ab", Alignment.BottomEnd) {
                ButtonCluster(abButtons, style, "Buttons A and B", Modifier.size(abSize)) { keys(abKeys, it) }
            }
            Slot("z", Alignment.BottomEnd, x = -(abSize + 8.dp), y = (-6).dp) {
                HoldButton("Z", style, Modifier.width((58 * unit).dp).height((46 * unit).dp), RoundedCornerShape(14.dp), toggle = zToggle) { key(N64_Z, it) }
            }
            Slot("c", if (portrait) Alignment.BottomEnd else Alignment.TopEnd, x = (-4).dp, y = if (portrait) -(abSize + 12.dp) else (44 * unit).dp) {
                if (cStick) AnalogStick(style, 1f, Modifier.size(cSize)) { x, y -> cStickX = x; cStickY = y; sendC() }
                else ButtonCluster(cButtons, style, "C buttons", Modifier.size(cSize), labelSize = 12.sp) { touchC = it; sendC() }
            }
            Slot("start", Alignment.BottomCenter) {
                HoldButton("START", style, Modifier.width((66 * unit).dp).height((26 * unit).dp), RoundedCornerShape(50)) { key(KeyEvent.KEYCODE_BUTTON_START, it) }
            }
        }
    }

    /** Small round actions over the game: menu, quick save and load, rotate, fast-forward, hide controls. */
    @Composable
    private fun QuickActions(size: Dp) {
        val style = padStyle
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIcon(Icons.Rounded.Menu, "Open menu", style, size = size) { pauseGame() }
            GlassIcon(Icons.Rounded.Save, "Quick save", style, size = size) { quickSave() }
            GlassIcon(Icons.Rounded.Restore, "Quick load", style, size = size) { quickLoad() }
            GlassIcon(Icons.Rounded.ScreenRotation, if (portrait) "Switch to landscape" else "Switch to portrait", style, size = size) { rotateNow() }
            GlassIcon(Icons.Rounded.FastForward, if (fastForward) "Normal speed" else "Fast forward", style, active = fastForward,
                badge = if (fastForward) "${ffSpeed}×" else null, size = size) { toggleFastForward() }
            GlassIcon(Icons.Rounded.VisibilityOff, "Hide controls", style, size = size) { controlsHidden = true; notice = "Controls hidden" }
        }
    }

    @Composable
    private fun GamepadShortcuts(modifier: Modifier) {
        val style = padStyle
        Row(modifier.padding(10.dp).graphicsLayer { alpha = 0.6f }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (controlsHidden) GlassIcon(Icons.Rounded.Visibility, "Show controls", style, active = true) { controlsHidden = false }
            GlassIcon(Icons.Rounded.Menu, "Open menu", style) { pauseGame() }
            GlassIcon(Icons.Rounded.Save, "Quick save", style) { quickSave() }
            GlassIcon(Icons.Rounded.Restore, "Quick load", style) { quickLoad() }
            GlassIcon(Icons.Rounded.FastForward, "Fast forward", style, active = fastForward, badge = if (fastForward) "${ffSpeed}×" else null) { toggleFastForward() }
        }
    }

    // ---- Layout editing ------------------------------------------------------------------------

    private fun startEditing() {
        // The game stays paused (the menu already paused it) while controls are dragged.
        menu = false
        controlsHidden = false
        gamepadActive = false
        editing = true
    }

    private fun finishEditing() {
        editing = false
        resumeEngine()
    }

    private fun resetLayout() {
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith("pos_") }.forEach { editor.remove(it) }
        editor.remove("hidden")
        editor.apply()
        hiddenControls = emptySet()
        layoutVersion++
    }

    private fun toggleHidden(name: String) {
        hiddenControls = if (name in hiddenControls) hiddenControls - name else hiddenControls + name
        preferences.edit().putStringSet("hidden", hiddenControls).apply()
        releaseButtons()
    }

    private fun chooseLeftControl(value: String) {
        releaseButtons()
        stick(0f, 0f)
        leftControl = value
        preferences.edit().putString(gameKey("left"), value).apply()
    }

    private fun swapLeftControl() {
        chooseLeftControl(if (leftControl == "dpad") "stick" else "dpad")
        notice = if (leftControl == "dpad") "D-pad for this game" else "Stick for this game"
    }

    @Composable
    private fun EditBar() {
        Column(
            Modifier.widthIn(max = 300.dp).background(PanelColor.copy(alpha = 0.96f), RoundedCornerShape(18.dp))
                .border(1.dp, StrokeColor, RoundedCornerShape(18.dp)).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Drag a control to move it", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Tap the eye on a control to hide it", color = TextMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Reset", { resetLayout() }, height = 38.dp, modifier = Modifier.weight(1f))
                PrimaryButton("Done", { finishEditing() }, height = 38.dp, modifier = Modifier.weight(1f))
            }
        }
    }

    // ---- Pause menu ----------------------------------------------------------------------------

    @Composable
    private fun PauseMenu(title: String) {
        val resume = { if (!snapshotPending) { menu = false; resumeEngine() } }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { resume() }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            val panel = Modifier.widthIn(max = 760.dp).background(PanelColor, RoundedCornerShape(24.dp))
                .border(1.dp, StrokeColor, RoundedCornerShape(24.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
            if (portrait) {
                Column(panel.fillMaxWidth().fillMaxHeight(0.92f).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MenuActions(title, resume)
                    MenuSettings()
                }
            } else {
                Row(panel.fillMaxHeight()) {
                    Column(Modifier.weight(0.5f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MenuActions(title, resume)
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 18.dp).background(StrokeColor))
                    Column(Modifier.weight(0.5f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MenuSettings()
                    }
                }
            }
        }
    }

    @Composable
    private fun MenuActions(title: String, resume: () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
            PrimaryButton("Resume", { resume() }, icon = Icons.Rounded.PlayArrow, enabled = !snapshotPending, height = 42.dp, modifier = Modifier.fillMaxWidth())
            SettingLabel("Save states")
            val time = remember { DateFormat.getTimeFormat(this@PlayerActivity) }
            val date = remember { DateFormat.getMediumDateFormat(this@PlayerActivity) }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((1..SaveStore.SLOTS).toList()) { slot ->
                    val saved = slotTimes[slot - 1]
                    SlotCard(slot, if (saved > 0) "${date.format(Date(saved))} ${time.format(Date(saved))}" else "Empty", saved > 0, slotShots[slot])
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Cheats", { cheatsOpen = true }, icon = Icons.Rounded.Code, enabled = ready, height = 40.dp, modifier = Modifier.weight(1f))
                SecondaryButton("Restart", { restartGame() }, icon = Icons.Rounded.RestartAlt, enabled = ready && !snapshotPending, height = 40.dp, modifier = Modifier.weight(1f))
            }
            PrimaryButton(if (snapshotPending) "Saving…" else "Save & exit", { finish() }, icon = Icons.AutoMirrored.Rounded.ExitToApp,
                enabled = !snapshotPending, height = 40.dp, container = Raised, content = TextPrimary, modifier = Modifier.fillMaxWidth())
            notice?.let { Text(it, color = Accent, fontSize = 12.sp) }
            Text("Saves on its own: in-game save every 30 seconds, resume point every minute", color = TextFaint, fontSize = 11.sp)
        }
    }

    @Composable
    private fun SlotCard(slot: Int, label: String, canLoad: Boolean, shot: ImageBitmap?) {
        Column(Modifier.width(138.dp).clip(RoundedCornerShape(14.dp)).background(Raised), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(78.dp).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                if (shot != null) Image(shot, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text(if (canLoad) "Slot $slot" else "Empty", color = TextFaint, fontSize = 11.sp)
            }
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Slot $slot", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                Text(label, color = TextMuted, fontSize = 10.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlatformPill("Save", false) { if (ready && !snapshotPending) saveToSlot(slot) }
                    if (canLoad) PlatformPill("Load", false) { if (ready && !snapshotPending) loadSlot(slot) }
                }
            }
        }
    }

    @Composable
    private fun MenuSettings() {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Screen", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Choice("Orientation", listOf("landscape" to "Landscape", "portrait" to "Portrait", "auto" to "Auto"), orientationMode) { setOrientation(it) }
            Choice("Picture shape", listOf("4:3" to "Original 4:3", "16:9" to "Wide 16:9", "fill" to "Fill screen"), aspect) { value ->
                val needsRestart = (value == "16:9") != bootWide && value != "fill"
                aspect = value
                preferences.edit().putString("aspect", value).apply()
                if (needsRestart) notice = "Applies the next time you open the game"
            }
            val sizes = if (roomyDevice) listOf("320x240" to "Original", "640x480" to "2× sharper", "960x720" to "3× sharper")
            else listOf("320x240" to "Original", "640x480" to "2× sharper")
            Choice("Picture quality", sizes, resolution) { value ->
                resolution = value
                preferences.edit().putString("resolution", value).apply()
                notice = "Applies the next time you open the game"
            }
            Choice("Screen filter", listOf("smooth" to "Smooth", "sharp" to "Sharp", "crt" to "CRT"), filter) { value ->
                filter = value
                preferences.edit().putString("filter", value).apply()
                engine?.shader = shaderFor(value)
            }
            SettingSlider("Zoom in landscape", "${(zoom * 100).roundToInt()}%", zoom, 1f..1.25f, { zoom = it }) {
                preferences.edit().putFloat("zoom", zoom).apply()
            }
            SettingSwitch("Show the whole picture (no trimmed edges)", fullPicture) {
                fullPicture = it
                preferences.edit().putBoolean("fullPicture", it).apply()
                notice = "Applies the next time you open the game"
            }
            SettingSlider("Screen brightness", if (brightness <= 0.01f) "Phone setting" else "${(40 + brightness * 60).roundToInt()}%", brightness, 0f..1f,
                { brightness = it; applyBrightness() }) { preferences.edit().putFloat("brightness", brightness).apply() }

            Text("Controls", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Edit layout", { startEditing() }, icon = Icons.Rounded.OpenWith, height = 40.dp, modifier = Modifier.weight(1f))
                SecondaryButton("Controller", { padLive = emptySet(); controllerOpen = true }, icon = Icons.Rounded.SportsEsports, height = 40.dp, modifier = Modifier.weight(1f))
            }
            SettingSlider("Controls size", "${(scale * 100).roundToInt()}%", scale, 0.75f..1.25f, { scale = it }) {
                preferences.edit().putFloat("scale", scale).apply()
            }
            SettingSlider("Controls opacity", "${(opacity * 100).roundToInt()}%", opacity, 0.25f..0.95f, { opacity = it }) {
                preferences.edit().putFloat("opacity", opacity).apply()
            }
            SettingSlider("Stick sensitivity", "${(sensitivity * 100).roundToInt()}%", sensitivity, 0.7f..1.3f, { sensitivity = it }) {
                preferences.edit().putFloat("sensitivity", sensitivity).apply()
            }
            Choice("Left control (this game)", listOf("stick" to "Stick", "dpad" to "D-pad", "both" to "Both"), leftControl) { chooseLeftControl(it) }
            SettingSwitch("C buttons as a second stick (this game)", cStick) {
                releaseButtons(); cStick = it; preferences.edit().putBoolean(gameKey("cStick"), it).apply()
            }
            SettingSwitch("Z stays pressed until tapped again", zToggle) { releaseButtons(); zToggle = it; preferences.edit().putBoolean("zToggle", it).apply() }
            SettingSwitch("Swap A / B", swapButtons) { releaseButtons(); swapButtons = it; preferences.edit().putBoolean("swapButtons", it).apply() }
            SettingSwitch("Button vibration", haptics) { haptics = it; preferences.edit().putBoolean("haptics", it).apply() }
            Choice("Controller Pak (this game)", listOf("memory" to "Memory (saves)", "rumble" to "Rumble"), pak) { value ->
                pak = value
                preferences.edit().putString(gameKey("pak"), value).apply()
                notice = "Applies the next time you open the game"
            }

            Text("Speed and sound", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Choice("Fast-forward speed", listOf("2" to "2×", "3" to "3×", "4" to "4×"), ffSpeed.toString()) { value ->
                ffSpeed = value.toInt()
                preferences.edit().putInt("ffSpeed", ffSpeed).apply()
                if (fastForward) engine?.frameSpeed = ffSpeed
            }
            SettingSwitch("Mute sound in fast-forward", muteFastForward) {
                muteFastForward = it
                preferences.edit().putBoolean("muteFastForward", it).apply()
                engine?.audioEnabled = !(fastForward && it)
            }
            SettingSwitch("Keep other audio playing (music apps)", keepOtherAudio) {
                keepOtherAudio = it
                preferences.edit().putBoolean("keepOtherAudio", it).apply()
            }
        }
    }

    /** Gamepad screen over the pause menu: what is connected, a live test of every button and stick, and the set-up wizard. */
    @Composable
    private fun ControllerPanel() {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.86f))
            .clickable(remember { MutableInteractionSource() }, indication = null) {}.padding(16.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 520.dp).background(PanelColor, RoundedCornerShape(24.dp)).border(1.dp, StrokeColor, RoundedCornerShape(24.dp))
                    .verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Controller", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(padName ?: "Press any button on your controller. To pair a new one, use the phone's Bluetooth settings.",
                    color = TextMuted, fontSize = 12.5.sp)
                if (wizardStep >= 0) {
                    Column(Modifier.fillMaxWidth().background(Raised, RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Step ${wizardStep + 1} of ${padActions.size}", color = TextFaint, fontSize = 11.sp)
                        Text("Press the button you want for ${padActions[wizardStep]}", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("The sticks and the direction pad are set up already.", color = TextMuted, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Skip this one", { wizardNext() }, height = 40.dp, modifier = Modifier.weight(1f))
                        SecondaryButton("Cancel", { wizardStep = -1 }, height = 40.dp, modifier = Modifier.weight(1f))
                    }
                } else {
                    SettingLabel("Press buttons to test them")
                    padActions.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { action -> PlatformPill(action, action in padLive) {} }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        StickMeter("Stick", padStick)
                        StickMeter("C stick", padCStick)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("Set up buttons", { startWizard() }, height = 40.dp, modifier = Modifier.weight(1f))
                        SecondaryButton("Reset", { resetPadMap(); notice = "Controller reset" }, height = 40.dp, modifier = Modifier.weight(1f))
                    }
                    SecondaryButton("Done", { controllerOpen = false }, height = 40.dp, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    @Composable
    private fun StickMeter(label: String, position: Offset) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(72.dp).background(Raised, RoundedCornerShape(50)).border(1.dp, StrokeColor, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                Box(Modifier.offset((position.x * 24).dp, (position.y * 24).dp).size(20.dp).background(Accent, RoundedCornerShape(50)))
            }
            Text(label, color = TextMuted, fontSize = 11.sp)
        }
    }

    @Composable
    private fun Choice(label: String, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingLabel(label)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (key, text) -> PlatformPill(text, current == key) { onPick(key) } }
            }
        }
    }

    @Composable
    private fun SettingLabel(text: String) {
        Text(text, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }

    @Composable
    private fun SettingSlider(label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, onDone: () -> Unit) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(value, color = TextPrimary, fontSize = 12.sp)
            }
            Slider(
                value = current, onValueChange = onChange, valueRange = range, onValueChangeFinished = onDone,
                modifier = Modifier.height(30.dp),
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Raised)
            )
        }
    }

    @Composable
    private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked, onChange, colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = Accent,
                uncheckedThumbColor = TextMuted, uncheckedTrackColor = Raised, uncheckedBorderColor = StrokeColor))
        }
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PATH = "path"
        private const val CORE_FILE = "libmupen64plus_next_gles3.so"
        private const val QUICK_FILE = "quick.state"
        private const val UNDO_FILE = "undo.state"
        private const val CHEATS_FILE = "cheats.json"

        // How the engine reads the N64 pad through LibretroDroid's key codes.
        private const val N64_A = KeyEvent.KEYCODE_BUTTON_B
        private const val N64_B = KeyEvent.KEYCODE_BUTTON_Y
        private const val N64_Z = KeyEvent.KEYCODE_BUTTON_L2

        private val abKeys = listOf(N64_A, N64_B)
        private val dpadKeys = listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
        private val resolutions = setOf("320x240", "640x480", "960x720")

        /** N64 buttons a gamepad button can be given, in the order the set-up wizard asks for them. */
        private val padActions = listOf("A", "B", "Z", "L", "R", "Start", "C-up", "C-down", "C-left", "C-right")
        private val defaultPadMap = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to "A", KeyEvent.KEYCODE_BUTTON_X to "B",
            KeyEvent.KEYCODE_BUTTON_B to "C-down", KeyEvent.KEYCODE_BUTTON_Y to "C-left",
            KeyEvent.KEYCODE_BUTTON_L2 to "Z", KeyEvent.KEYCODE_BUTTON_R2 to "Z",
            KeyEvent.KEYCODE_BUTTON_L1 to "L", KeyEvent.KEYCODE_BUTTON_R1 to "R", KeyEvent.KEYCODE_BUTTON_START to "Start"
        )

        /** Reads a saved mapping ("keyCode:action,…"); null when it is missing or damaged, so the default is used. */
        private fun parsePadMap(text: String?): Map<Int, String>? {
            if (text.isNullOrBlank()) return null
            val map = text.split(',').mapNotNull { pair ->
                val code = pair.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
                val action = pair.substringAfter(':', "").takeIf { it in padActions } ?: return@mapNotNull null
                code to action
            }.toMap()
            return map.takeIf { it.isNotEmpty() }
        }

        // On the real pad B sits up and to the left of the larger A.
        private val abButtons = listOf(
            PadButton(N64_A, "A", 0.66f, 0.68f, 0.27f, PadColors.Blue),
            PadButton(N64_B, "B", 0.27f, 0.34f, 0.21f, PadColors.Green)
        )
        private val cButtons = listOf(
            PadButton(C_UP, "C", 0.5f, 0.18f, 0.16f, PadColors.Yellow, icon = 1),
            PadButton(C_DOWN, "C", 0.5f, 0.82f, 0.16f, PadColors.Yellow, icon = 2),
            PadButton(C_LEFT, "C", 0.18f, 0.5f, 0.16f, PadColors.Yellow, icon = 3),
            PadButton(C_RIGHT, "C", 0.82f, 0.5f, 0.16f, PadColors.Yellow, icon = 4)
        )

        fun launch(context: Context, id: String, title: String, path: String) {
            context.startActivity(Intent(context, PlayerActivity::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_PATH, path)
                .apply { if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            // The library fades into the game instead of the system's slide.
            (context as? android.app.Activity)?.let { host ->
                if (Build.VERSION.SDK_INT >= 34) host.overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
                else @Suppress("DEPRECATION") host.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        /** The widescreen size that matches a 4:3 quality level. */
        private fun wideSize(size: String): String = when (size) {
            "320x240" -> "640x360"
            "960x720" -> "1280x720"
            else -> "960x540"
        }

        private fun shaderFor(key: String): ShaderConfig = when (key) {
            "sharp" -> ShaderConfig.Sharp
            "crt" -> ShaderConfig.CRT
            else -> ShaderConfig.Default
        }

        /** Writes to a temporary file, syncs it, renames it and checks the result, so a save is whole or not there at all. */
        private fun atomicWrite(file: File, bytes: ByteArray) {
            val pending = File(file.parentFile, file.name + ".tmp")
            pending.outputStream().use { output -> output.write(bytes); output.fd.sync() }
            check(pending.renameTo(file)) { "Save could not be written." }
            check(file.length() == bytes.size.toLong()) { "Save could not be checked." }
        }
    }
}
