package com.appkraft.turbo64

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.appkraft.turbo64.ads.Ads
import com.appkraft.turbo64.data.Engagement
import com.appkraft.turbo64.data.PlayStore
import com.appkraft.turbo64.library.GameEntry
import com.appkraft.turbo64.library.GameFileProblem
import com.appkraft.turbo64.library.GameStore
import com.appkraft.turbo64.library.PickOutcome
import com.appkraft.turbo64.library.PickRules
import com.appkraft.turbo64.library.PlaySessions
import com.appkraft.turbo64.library.SaveStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

enum class Screen { HOME, LIBRARY, SETTINGS, ABOUT, PRIVACY }
enum class LibraryTab { ALL, RECENT, FAVORITES }
enum class SortOrder { RECENT, NAME, PLAY_TIME }

data class LibraryState(
    val screen: Screen = Screen.HOME,
    val games: List<GameEntry> = emptyList(),
    val folders: List<Uri> = emptyList(),
    /** Game files added one by one and linked where they are. */
    val files: List<Uri> = emptyList(),
    /** Games added one by one that were copied into the app. */
    val importedCount: Int = 0,
    /** Picked files being added right now (null when idle). */
    val importing: Importing? = null,
    val scanning: Boolean = false,
    val scanCount: Int = 0,
    val query: String = "",
    val tab: LibraryTab = LibraryTab.ALL,
    val sort: SortOrder = SortOrder.RECENT,
    /** Game being copied/extracted before it starts, with progress (null = indeterminate). */
    val preparing: GameEntry? = null,
    val prepareProgress: Float? = null,
    /** Ready to open in the player from the visible screen. */
    val pendingLaunch: GameEntry? = null,
    val notice: String? = null,
    val askReview: Boolean = false,
    val adBreak: Boolean = false,
    val updateVersion: Int? = null
) {
    /** The game to resume from the home screen: last played with a resume point. */
    fun continueGame(saves: SaveStore): GameEntry? =
        games.filter { it.lastPlayed > 0 && saves.hasContinue(it.id) }.maxByOrNull { it.lastPlayed }

    /** True once the user gave the app at least one folder or file. */
    val hasSources: Boolean get() = folders.isNotEmpty() || files.isNotEmpty() || importedCount > 0

    /** Everything added one by one, linked or copied. */
    val pickedCount: Int get() = files.size + importedCount

    val totalPlayTimeMs: Long get() = games.sumOf { it.playTimeMs }

    val recentlyPlayed: List<GameEntry> get() = games.filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }
    val favorites: List<GameEntry> get() = games.filter { it.favorite }.sortedBy { it.title.lowercase() }
    val recentlyAdded: List<GameEntry> get() = games.sortedByDescending { it.addedAt }

    val visibleGames: List<GameEntry>
        get() {
            val base = when (tab) {
                LibraryTab.ALL -> games
                LibraryTab.RECENT -> games.filter { it.lastPlayed > 0 }
                LibraryTab.FAVORITES -> games.filter { it.favorite }
            }
            val filtered = if (query.isBlank()) base else base.filter {
                it.title.contains(query.trim(), ignoreCase = true) || it.fileName.contains(query.trim(), ignoreCase = true)
            }
            return when (sort) {
                SortOrder.RECENT -> filtered.sortedWith(compareByDescending<GameEntry> { it.lastPlayed }.thenByDescending { it.addedAt }.thenBy { it.title.lowercase() })
                SortOrder.NAME -> filtered.sortedBy { it.title.lowercase() }
                SortOrder.PLAY_TIME -> filtered.sortedByDescending { it.playTimeMs }
            }
        }
}

/** Progress of adding picked files: [index] counts from 0; [progress] null = no size known. */
data class Importing(val index: Int, val total: Int, val progress: Float? = null)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    val store = GameStore(application)
    val saves = SaveStore(application)
    private var scanJob: Job? = null
    private var prepareJob: Job? = null
    private var importJob: Job? = null
    /** A scan was asked for while picked files were being added; it runs right after. */
    private var rescanAfterImport = false
    private var updateCheck: Job? = null

    var state by mutableStateOf(LibraryState(games = store.load(), folders = store.folders(), files = store.files(), importedCount = store.importedCount()))
        private set

    private val updatePrefs = application.getSharedPreferences("updates", android.content.Context.MODE_PRIVATE)

    init {
        // A newer version seen earlier stays required, even offline, until it is installed.
        updatePrefs.getInt("required", 0).takeIf { it > BuildConfig.VERSION_CODE }?.let { state = state.copy(updateVersion = it) }
        if (state.hasSources) rescan(quiet = true)
    }

    // ---- Navigation and filters ---------------------------------------------------------------

    fun navigate(screen: Screen) { state = state.copy(screen = screen) }
    fun setQuery(value: String) { state = state.copy(query = value.take(60)) }
    fun setTab(tab: LibraryTab) { state = state.copy(tab = tab) }
    fun setSort(sort: SortOrder) { state = state.copy(sort = sort) }
    fun showNotice(message: String) { state = state.copy(notice = message) }
    fun clearNotice() { state = state.copy(notice = null) }

    // ---- Folders and scanning --------------------------------------------------------------

    fun addFolder(tree: Uri) {
        runCatching { store.addFolder(tree) }
            .onFailure { showNotice("That folder can't be used. Try another one.") }
            .onSuccess {
                state = state.copy(folders = store.folders())
                rescan(quiet = false, force = true)
            }
    }

    /**
     * Single files picked with the system file picker. Each one is read at once, while the picker's
     * access is valid: linked if Android lets the app keep access, copied into the app otherwise.
     * A running scan is stopped first (both write the library) and run again afterwards.
     */
    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty() || importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            val scanWasRunning = scanJob?.isActive == true
            scanJob?.cancelAndJoin()
            state = state.copy(scanning = false, importing = Importing(0, uris.size))
            val outcomes = mutableListOf<PickOutcome>()
            try {
                uris.forEachIndexed { index, uri ->
                    state = state.copy(importing = Importing(index, uris.size))
                    outcomes += store.addFile(uri) { progress -> state = state.copy(importing = Importing(index, uris.size, progress)) }
                }
                showNotice(PickRules.summary(outcomes))
            } catch (cancelled: CancellationException) {
                showNotice(if (outcomes.isEmpty()) "Stopped. Nothing was added" else "Stopped. ${PickRules.summary(outcomes)}")
                throw cancelled
            } finally {
                state = state.copy(importing = null, games = store.load(), files = store.files(), importedCount = store.importedCount())
                if (scanWasRunning || rescanAfterImport) {
                    rescanAfterImport = false
                    rescan(quiet = true)
                }
            }
        }
    }

    fun cancelImport() { importJob?.cancel() }

    fun removeFolder(tree: Uri) {
        store.removeFolder(tree)
        state = state.copy(folders = store.folders())
        rescan(quiet = true, force = true)
    }

    /** Forgets the files added one by one; games from folders stay. */
    fun removeAllFiles() {
        if (importJob?.isActive == true) return
        scanJob?.cancel()
        store.removeAllFiles()
        state = state.copy(files = store.files(), importedCount = store.importedCount())
        rescan(quiet = true, force = true)
        showNotice("Games added one by one are removed. Saves are kept.")
    }

    /** Empties the library. The user's game files and every save stay where they are. */
    fun clearLibrary() {
        if (importJob?.isActive == true) return
        scanJob?.cancel()
        store.clearLibrary()
        state = state.copy(games = emptyList(), folders = emptyList(), files = emptyList(), importedCount = 0, scanning = false, query = "", screen = Screen.HOME)
    }

    fun deleteAllSaves() {
        saves.deleteEverything()
        showNotice("All saves deleted")
    }

    /** Puts every player setting back: control layout, sizes, picture options and the gamepad mapping. */
    fun resetPlayerSettings() {
        getApplication<Application>().getSharedPreferences("player", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        showNotice("Controls and player settings reset")
    }

    fun rescan(quiet: Boolean = false, force: Boolean = false) {
        // Adding picked files writes the library too; the scan runs as soon as that is done.
        if (state.importing != null) { rescanAfterImport = true; return }
        if (scanJob?.isActive == true && !force) return
        val previous = scanJob
        scanJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            state = state.copy(scanning = true, scanCount = 0)
            val before = state.games.size
            runCatching { store.scan { count -> state = state.copy(scanCount = count) } }
                .onSuccess { games ->
                    state = state.copy(games = games, scanning = false)
                    if (!quiet) {
                        val added = games.size - before
                        showNotice(when {
                            games.isEmpty() -> "No N64 games found there"
                            added > 0 -> "$added new game${if (added == 1) "" else "s"} added"
                            else -> "Library is up to date"
                        })
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    state = state.copy(scanning = false)
                    showNotice("Scan stopped. Try again.")
                }
        }
    }

    // ---- Games -------------------------------------------------------------------------------

    fun toggleFavorite(game: GameEntry) {
        state = state.copy(games = store.update(game.id) { it.copy(favorite = !it.favorite) })
    }

    /** A name of the user's own; an empty name brings the game's own name back. */
    fun renameGame(game: GameEntry, title: String) {
        state = state.copy(games = store.rename(game.id, title))
        showNotice(if (title.isBlank()) "Name reset" else "Renamed")
    }

    fun removeFromLibrary(game: GameEntry) {
        if (importJob?.isActive == true) return
        state = state.copy(games = store.remove(game.id), files = store.files(), importedCount = store.importedCount())
        showNotice("Removed. File and saves kept.")
    }

    fun startFresh(game: GameEntry) {
        saves.clearContinue(game.id)
        showNotice("Next start uses the in-game save")
    }

    fun restoreSave(game: GameEntry, backup: java.io.File) {
        runCatching { saves.restore(game.id, backup) }
            .onSuccess { showNotice("Older save restored") }
            .onFailure { showNotice(it.message ?: "The save could not be restored") }
    }

    fun deleteSaves(game: GameEntry) {
        saves.deleteAll(game.id)
        showNotice("All saves of ${game.title} deleted")
    }

    /** Copies the game into private storage if needed, then asks the screen to open the player. */
    fun play(game: GameEntry) {
        if (prepareJob?.isActive == true) return
        prepareJob = viewModelScope.launch {
            state = state.copy(preparing = game, prepareProgress = null)
            try {
                store.prepare(game) { progress -> state = state.copy(prepareProgress = progress) }
                state = state.copy(preparing = null, pendingLaunch = game)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (problem: GameFileProblem) {
                showNotice(problem.message ?: "The game could not be opened.")
            } catch (problem: Exception) {
                showNotice("The game could not be opened. Please try again.")
            } finally {
                state = state.copy(preparing = null, prepareProgress = null)
            }
        }
    }

    fun cancelPrepare() { prepareJob?.cancel() }

    fun consumeLaunch() { state = state.copy(pendingLaunch = null) }

    /**
     * Called when the library is shown again: takes the play time the player wrote (see
     * [PlaySessions]) into the library. Returns true when a game was played.
     */
    fun applyPlaySessions(): Boolean {
        val sessions = PlaySessions.drain(getApplication())
        if (sessions.isEmpty()) return false
        var games = state.games
        sessions.forEach { (id, session) ->
            val (at, ms) = session
            games = store.update(id) { it.copy(lastPlayed = maxOf(it.lastPlayed, at), playTimeMs = it.playTimeMs + ms) }
        }
        state = state.copy(games = games)
        // A fresh copy of every save goes to the user's backup folder after each game, if they picked one.
        viewModelScope.launch {
            if (saves.backupFolder() != null && !saves.autoBackup()) showNotice("Automatic backup failed. Pick the backup folder again in Settings.")
        }
        return true
    }

    // ---- Review, ad breaks, updates (same rules as the other AppKraft apps) -------------------

    fun reviewMoment() {
        if (!state.askReview && Engagement.reviewDue()) state = state.copy(askReview = true)
    }

    fun reviewHandled(launched: Boolean) {
        if (launched) {
            Engagement.reviewAsked()
            Ads.quietSession = true
        }
        state = state.copy(askReview = false)
    }

    fun adBreak() { if (!state.askReview) state = state.copy(adBreak = true) }
    fun adBreakHandled() { state = state.copy(adBreak = false) }

    fun checkForUpdate() {
        if (state.updateVersion != null || updateCheck?.isActive == true) return
        updateCheck = viewModelScope.launch {
            val version = PlayStore.availableUpdate(getApplication())?.takeIf { it > BuildConfig.VERSION_CODE } ?: return@launch
            updatePrefs.edit().putInt("required", version).apply()
            state = state.copy(updateVersion = version)
        }
    }

    fun openUpdate() { PlayStore.openListing(getApplication()) }
}
