package com.appkraft.turbo64.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/** One game the user owns, found in a folder they chose. */
data class GameEntry(
    val id: String,
    val uri: String,
    val fileName: String,
    val size: Long,
    val title: String,
    val subtitle: String = "",
    val gameCode: String = "",
    val hasIcon: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayed: Long = 0,
    val playTimeMs: Long = 0,
    val favorite: Boolean = false,
    /** A file picked one by one that can no longer be read (moved, deleted or access lost). */
    val missing: Boolean = false,
    /** The user renamed this game; its name is never replaced automatically. */
    val customTitle: Boolean = false
) {
    val extension: String get() = fileName.substringAfterLast('.', "").lowercase()
    val isArchive: Boolean get() = extension in N64Header.archiveExtensions

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("uri", uri).put("fileName", fileName).put("size", size)
        .put("title", title).put("subtitle", subtitle).put("gameCode", gameCode).put("hasIcon", hasIcon)
        .put("addedAt", addedAt).put("lastPlayed", lastPlayed).put("playTimeMs", playTimeMs).put("favorite", favorite).put("missing", missing).put("customTitle", customTitle)

    companion object {
        fun fromJson(json: JSONObject) = GameEntry(
            id = json.getString("id"), uri = json.getString("uri"), fileName = json.getString("fileName"),
            size = json.optLong("size"), title = json.optString("title"), subtitle = json.optString("subtitle"),
            gameCode = json.optString("gameCode"), hasIcon = json.optBoolean("hasIcon"),
            addedAt = json.optLong("addedAt"), lastPlayed = json.optLong("lastPlayed"),
            playTimeMs = json.optLong("playTimeMs"), favorite = json.optBoolean("favorite"),
            missing = json.optBoolean("missing"), customTitle = json.optBoolean("customTitle")
        )
    }
}

/** A game file that could not be opened, with a reason the user can act on. */
class GameFileProblem(message: String) : Exception(message)

/**
 * The library: folders the user granted (Storage Access Framework, no storage permission),
 * the games found in them, their icons, and the private copy of a game the player loads.
 * Saves live apart from the library and are never touched by a rescan or removal.
 */
class GameStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val indexFile = File(app.filesDir, "library.json")
    private val iconDir = File(app.filesDir, "icons").apply { mkdirs() }
    private val romDir = File(app.filesDir, "roms").apply { mkdirs() }
    /**
     * Games picked one by one from a place that gives no lasting access (most phone makers' file
     * apps, Google Files categories, Drive). They are copied here when picked and played from here.
     * Never trimmed or cleared with the cache; only removing the game deletes its copy.
     */
    private val importDir = File(app.filesDir, "imported").apply { mkdirs() }

    init {
        // A copy cut short when the app was closed mid-way; nothing can be using it at start-up.
        importDir.listFiles { file -> file.extension == "part" }.orEmpty().forEach { it.delete() }
    }

    fun folders(): List<Uri> = prefs.getStringSet("folders", emptySet()).orEmpty().sorted().map(Uri::parse)

    /** Single game files the user picked one by one (works anywhere, also in Download). */
    fun files(): List<Uri> = prefs.getStringSet("files", emptySet()).orEmpty().sorted().map(Uri::parse)

    /**
     * Adds one picked file to the library, right away, while the picker's access is still valid.
     *
     * - If Android lets the app keep access, the file stays where it is and is linked (no copy).
     * - Otherwise (or when the file has no usable name) the game is copied into the app, so it
     *   keeps working after a restart, wherever it came from.
     *
     * The game is identified by its header, so a game already in the library is not added twice,
     * and its saves, play time and favourite carry over. Must not run while [scan] runs.
     */
    suspend fun addFile(uri: Uri, progress: (Float?) -> Unit): PickOutcome = withContext(Dispatchers.IO) {
        val (rawName, size) = queryMeta(uri) ?: ("" to -1L)
        val name = rawName.trim()
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension in N64Header.allExtensions && keepAccess(uri)) {
            return@withContext addLink(uri, name, size)
        }
        importCopy(uri, name, size, progress)
    }

    private fun keepAccess(uri: Uri): Boolean =
        runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess

    private fun releaseAccess(uri: Uri) {
        // Only when no folder or other entry relies on it.
        if (uri.toString() in prefs.getStringSet("files", emptySet()).orEmpty()) return
        runCatching { app.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun queryMeta(uri: Uri): Pair<String, Long>? = runCatching {
        app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameColumn = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            val name = if (nameColumn >= 0 && !cursor.isNull(nameColumn)) cursor.getString(nameColumn).orEmpty() else ""
            val size = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn) else -1L
            name to size
        }
    }.getOrNull()

    private fun addLink(uri: Uri, name: String, size: Long): PickOutcome {
        val library = load()
        val fresh = runCatching { describe(uri, name, size) }.getOrNull()
        if (fresh == null) {
            releaseAccess(uri)
            return PickOutcome(PickResult.NOT_A_GAME, N64Header.titleFromFileName(name))
        }
        val existing = library.firstOrNull { it.id == fresh.id }
        if (existing != null && !existing.missing) {
            if (existing.uri != uri.toString()) releaseAccess(uri)
            return PickOutcome(PickResult.ALREADY_THERE, existing.title)
        }
        // A game whose old file went missing is replaced by the new one, keeping its history.
        existing?.let { old -> forgetFile(old.uri) }
        val entry = existing?.let { old -> fresh.copy(lastPlayed = old.lastPlayed, playTimeMs = old.playTimeMs, favorite = old.favorite, addedAt = old.addedAt) } ?: fresh
        prefs.edit().putStringSet("files", prefs.getStringSet("files", emptySet()).orEmpty() + uri.toString()).apply()
        save(library.filterNot { it.id == entry.id } + entry)
        return PickOutcome(PickResult.ADDED, entry.title)
    }

    /** Copies (or unpacks) a picked game into [importDir] and adds it. Nothing is left behind on failure. */
    private suspend fun importCopy(uri: Uri, name: String, size: Long, progress: (Float?) -> Unit): PickOutcome {
        val first = runCatching { app.contentResolver.openInputStream(uri)?.use { it.readUpTo(PickRules.SNIFF_SIZE) } }
            .getOrElse { error -> return PickOutcome(PickResult.FAILED, reason = readProblem(error)) }
            ?: return PickOutcome(PickResult.FAILED, reason = "That file could not be opened. Please pick it again.")
        val kind = PickRules.sniff(first, name)
        if (kind == SourceKind.UNKNOWN) return PickOutcome(PickResult.NOT_A_GAME)
        val pending = File(importDir, "pick-${System.nanoTime()}.part")
        var archive: File? = null
        try {
            when (kind) {
                SourceKind.ZIP -> extractZip(uri, size, pending, progress)
                SourceKind.SEVEN_Z -> {
                    // Copy the archive first: 7z needs to jump around in the file, and many sources can't.
                    val local = File(importDir, "pick-${System.nanoTime()}.7z.part").also { archive = it }
                    requireSpace(if (size > 0) size * 3 else 0)
                    copyStream(app.contentResolver.openInputStream(uri) ?: throw GameFileProblem("That file could not be opened. Please pick it again."),
                        local, size, progress = { p -> progress(p?.let { it * 0.5f }) })
                    extract7z(Uri.fromFile(local), pending) { p -> progress(p?.let { 0.5f + it * 0.5f }) }
                }
                else -> {
                    requireSpace(size.coerceAtLeast(0))
                    copyStream(app.contentResolver.openInputStream(uri) ?: throw GameFileProblem("That file could not be opened. Please pick it again."),
                        pending, size, progress)
                }
            }
            val header = pending.inputStream().use { it.readExactly(N64Header.HEADER_SIZE) }
            val info = header?.let(N64Header::parseHeader) ?: return PickOutcome(PickResult.NOT_A_GAME)
            N64Header.problem(header, pending.length())?.let { return PickOutcome(PickResult.FAILED, reason = it) }

            val library = load()
            val existing = library.firstOrNull { it.id == info.stableId }
            if (existing != null && !existing.missing) return PickOutcome(PickResult.ALREADY_THERE, existing.title)

            val target = File(importDir, "${info.stableId}.z64")
            target.delete()
            if (!pending.renameTo(target)) return PickOutcome(PickResult.FAILED, reason = "The game could not be saved on this phone. Please try again.")
            File(romDir, "${info.stableId}.z64").delete() // an older play copy of the same game is not needed any more
            existing?.let { old -> forgetFile(old.uri) }
            val title = GameNames.title(app, info, name)
            val fresh = GameEntry(
                id = info.stableId, uri = Uri.fromFile(target).toString(), fileName = "$title.z64", size = target.length(),
                title = title, subtitle = info.region, gameCode = info.gameCode
            )
            val entry = existing?.let { old -> fresh.copy(lastPlayed = old.lastPlayed, playTimeMs = old.playTimeMs, favorite = old.favorite, addedAt = old.addedAt) } ?: fresh
            save(library.filterNot { it.id == entry.id } + entry)
            return PickOutcome(PickResult.ADDED, entry.title)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (problem: GameFileProblem) {
            val message = problem.message.orEmpty()
            return if (message.contains("no N64 game inside")) PickOutcome(PickResult.NOT_A_GAME) else PickOutcome(PickResult.FAILED, reason = message)
        } catch (error: Exception) {
            return PickOutcome(PickResult.FAILED, reason = readProblem(error))
        } finally {
            pending.delete()
            archive?.delete()
        }
    }

    private fun readProblem(error: Throwable): String = when (error) {
        is SecurityException -> "Turbo64 was not allowed to read that file. Please pick it again."
        is java.io.FileNotFoundException -> "That file could not be found. Please pick it again."
        is java.io.IOException -> "That file could not be read. If it is in the cloud, check the connection and try again."
        else -> "That file could not be added. Please try again."
    }

    /** Games copied into the app. */
    fun importedCount(): Int = importDir.listFiles { file -> file.extension == "z64" }.orEmpty().size

    fun importedBytes(): Long = importDir.listFiles { file -> file.extension == "z64" }.orEmpty().sumOf { it.length() }

    private fun importedFile(id: String): File = File(importDir, "$id.z64")

    private fun forgetFile(uri: String) {
        if (uri !in prefs.getStringSet("files", emptySet()).orEmpty()) return
        runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs.edit().putStringSet("files", prefs.getStringSet("files", emptySet()).orEmpty() - uri).apply()
    }

    /**
     * Removes the games that were added one by one: links are forgotten and copies made by the app
     * are deleted. Folders and their games stay. Saves are kept.
     */
    fun removeAllFiles() {
        files().forEach { uri -> runCatching { app.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        prefs.edit().remove("files").apply()
        importDir.listFiles().orEmpty().forEach { it.delete() }
    }

    /**
     * Empties the library: every folder and picked file is forgotten and the private game copies
     * are removed. The user's own game files and all saves are left alone.
     */
    fun clearLibrary() {
        (folders() + files()).forEach { uri -> runCatching { app.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        prefs.edit().remove("folders").remove("files").apply()
        save(emptyList())
        clearCache()
        importDir.listFiles().orEmpty().forEach { it.delete() }
    }

    fun addFolder(tree: Uri) {
        app.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit().putStringSet("folders", prefs.getStringSet("folders", emptySet()).orEmpty() + tree.toString()).apply()
    }

    /** Stops watching a folder; its games leave the library on the next scan. Saves are kept. */
    fun removeFolder(tree: Uri) {
        runCatching { app.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs.edit().putStringSet("folders", prefs.getStringSet("folders", emptySet()).orEmpty() - tree.toString()).apply()
    }

    fun folderLabel(tree: Uri): String =
        runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfter(':').ifBlank { "Internal storage" } }.getOrDefault(tree.toString())

    // ---- Index -----------------------------------------------------------------------------

    @Synchronized
    fun load(): List<GameEntry> = runCatching {
        if (!indexFile.isFile) return emptyList()
        val array = JSONArray(indexFile.readText())
        List(array.length()) { GameEntry.fromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun save(entries: List<GameEntry>) {
        val array = JSONArray().apply { entries.forEach { put(it.toJson()) } }
        val pending = File(indexFile.parentFile, indexFile.name + ".tmp")
        pending.writeText(array.toString())
        pending.renameTo(indexFile)
    }

    fun update(id: String, change: (GameEntry) -> GameEntry): List<GameEntry> {
        val updated = load().map { if (it.id == id) change(it) else it }
        save(updated)
        return updated
    }

    fun remove(id: String): List<GameEntry> {
        load().firstOrNull { it.id == id }?.let { forgetFile(it.uri) }
        val updated = load().filterNot { it.id == id }
        save(updated)
        File(romDir, "$id.z64").delete()
        importedFile(id).delete()
        File(iconDir, "$id.png").delete()
        return updated
    }

    fun iconFile(id: String): File = File(iconDir, "$id.png")

    // ---- Scanning --------------------------------------------------------------------------

    /**
     * Walks every granted folder (a few levels deep) and refreshes the library. Known files are
     * kept as they are (with play time and favourites); new ones are read once for their icon.
     */
    suspend fun scan(onFound: (Int) -> Unit = {}): List<GameEntry> = withContext(Dispatchers.IO) {
        val previous = load()
        val byUri = previous.associateBy { it.uri }
        val found = linkedMapOf<String, GameEntry>()
        var count = 0
        for (tree in folders()) {
            val root = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: continue
            walk(tree, root, depth = 0) { uri, name, size ->
                coroutineContext.ensureActive()
                val known = byUri[uri.toString()]
                val entry = if (known != null && known.size == size) known else describe(uri, name, size)?.let { fresh ->
                    // Keep play history if the same game moved or was renamed.
                    previous.firstOrNull { it.id == fresh.id }?.let { old ->
                        fresh.copy(lastPlayed = old.lastPlayed, playTimeMs = old.playTimeMs, favorite = old.favorite, addedAt = old.addedAt)
                    } ?: fresh
                }
                if (entry != null && entry.id !in found) {
                    found[entry.id] = entry
                    onFound(++count)
                }
            }
        }
        // Picked files that can't be read any more stay listed as missing (with their saves and
        // history) unless the same game is found elsewhere; they are added last for that reason.
        val missing = mutableListOf<GameEntry>()
        for (file in files()) {
            coroutineContext.ensureActive()
            val known = byUri[file.toString()]
            // The query fails once the file is deleted or access is lost. (Opening the file to
            // check would download every cloud file on each scan.)
            val meta = queryMeta(file)
            if (meta == null) {
                known?.let { missing += it.copy(missing = true) }
                continue
            }
            val name = meta.first.ifBlank { known?.fileName.orEmpty() }
            val size = meta.second
            if (name.substringAfterLast('.', "").lowercase() !in N64Header.allExtensions) continue
            val entry = if (known != null && (known.size == size || size < 0)) known.copy(missing = false) else describe(file, name, size)?.let { fresh ->
                previous.firstOrNull { it.id == fresh.id }?.let { old ->
                    fresh.copy(lastPlayed = old.lastPlayed, playTimeMs = old.playTimeMs, favorite = old.favorite, addedAt = old.addedAt)
                } ?: fresh
            }
            if (entry != null && entry.id !in found) {
                found[entry.id] = entry
                onFound(++count)
            }
        }
        // Games copied into the app when they were picked.
        for (copy in importDir.listFiles { file -> file.extension == "z64" }.orEmpty().sortedBy { it.name }) {
            coroutineContext.ensureActive()
            val id = copy.nameWithoutExtension
            if (id in found) continue
            val entry = byUri[Uri.fromFile(copy).toString()]?.copy(missing = false) ?: run {
                // Not in the index (it was lost): read the game's own name from its header.
                val info = runCatching { copy.inputStream().use { it.readExactly(N64Header.HEADER_SIZE) }?.let(N64Header::parseHeader) }.getOrNull()
                    ?: return@run null
                val title = GameNames.title(app, info, "")
                GameEntry(id = id, uri = Uri.fromFile(copy).toString(), fileName = "$title.z64", size = copy.length(),
                    title = title, subtitle = info.region, gameCode = info.gameCode)
            } ?: continue
            found[id] = entry
            onFound(++count)
        }
        missing.forEach { entry -> if (entry.id !in found) found[entry.id] = entry }
        // Play time, a favourite or a name may have changed while the scan ran; keep the newest.
        val latest = load().associateBy { it.id }
        val result = found.values.map { entry ->
            val merged = latest[entry.id]?.let { now ->
                entry.copy(lastPlayed = maxOf(entry.lastPlayed, now.lastPlayed), playTimeMs = maxOf(entry.playTimeMs, now.playTimeMs), favorite = now.favorite,
                    title = if (now.customTitle) now.title else entry.title, customTitle = now.customTitle)
            } ?: entry
            withBestTitle(merged)
        }
        save(result)
        result
    }

    private suspend fun walk(tree: Uri, documentId: String, depth: Int, visit: suspend (Uri, String, Long) -> Unit) {
        if (depth > 5) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val rows = mutableListOf<Triple<String, String, Pair<String, Long>>>()
        runCatching {
            app.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    rows += Triple(cursor.getString(0), cursor.getString(1) ?: "", (cursor.getString(2) ?: "") to cursor.getLong(3))
                }
            }
        }
        for ((id, name, meta) in rows) {
            val (mime, size) = meta
            when {
                mime == DocumentsContract.Document.MIME_TYPE_DIR -> if (!name.startsWith('.')) walk(tree, id, depth + 1, visit)
                name.startsWith('.') -> Unit
                name.substringAfterLast('.', "").lowercase() in N64Header.allExtensions ->
                    visit(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, size)
            }
        }
    }

    /** Reads the header of a game file (also inside a .zip) to identify it and its region. */
    private fun describe(uri: Uri, name: String, size: Long): GameEntry? {
        val extension = name.substringAfterLast('.', "").lowercase()
        val info: N64Info? = runCatching {
            when (extension) {
                in N64Header.gameExtensions -> readFromFile(uri)
                "zip" -> readFromZip(uri)
                else -> null // .7z headers need the whole archive decoded; the file name is used instead.
            }
        }.getOrNull()
        // A .bin or .rom that is not an N64 game, or a .zip holding something else, is not listed.
        if (info == null && extension != "7z") return null
        val id = info?.stableId ?: "F-" + sha1("$name:$size").take(12)
        return GameEntry(
            id = id, uri = uri.toString(), fileName = name, size = size,
            title = GameNames.title(app, info, name),
            subtitle = info?.region.orEmpty(), gameCode = info?.gameCode.orEmpty(), hasIcon = false
        )
    }

    private fun readFromFile(uri: Uri): N64Info? =
        app.contentResolver.openInputStream(uri)?.use { input -> input.readExactly(N64Header.HEADER_SIZE)?.let(N64Header::parseHeader) }

    private fun readFromZip(uri: Uri): N64Info? =
        app.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: return null
                    val inner = entry.name.substringAfterLast('/')
                    if (entry.isDirectory || inner.startsWith('.') || inner.substringAfterLast('.', "").lowercase() !in N64Header.gameExtensions) continue
                    val info = zip.readExactly(N64Header.HEADER_SIZE)?.let(N64Header::parseHeader)
                    if (info != null) return info
                }
                @Suppress("UNREACHABLE_CODE") null
            }
        }

    /**
     * Games added before names came from the game list keep their old (file) name; give them
     * the official one. A name the user chose is left alone.
     */
    private fun withBestTitle(entry: GameEntry): GameEntry {
        if (entry.customTitle) return entry
        val official = GameNames.crcFromId(entry.id)?.let { GameNames.official(app, it) } ?: return entry
        return if (official == entry.title) entry else entry.copy(title = official)
    }

    /** The user's own name for a game; blank puts the automatic name back. */
    fun rename(id: String, title: String): List<GameEntry> {
        val clean = title.trim().take(80)
        return update(id) { entry ->
            if (clean.isEmpty()) {
                val automatic = GameNames.crcFromId(entry.id)?.let { GameNames.official(app, it) }
                    ?: GameNames.choose(null, "", entry.fileName)
                entry.copy(title = automatic, customTitle = false)
            } else entry.copy(title = clean, customTitle = true)
        }
    }

    // ---- Preparing a game for the player ----------------------------------------------------

    /** The private copy the core loads (cores need a real file path, not a content URI). */
    fun preparedFile(id: String): File = importedFile(id).takeIf { it.isFile } ?: File(romDir, "$id.z64")

    /**
     * Copies (or extracts) the game into private storage once; later plays start instantly.
     * Older copies are trimmed so the cache stays within [CACHE_LIMIT] bytes.
     */
    suspend fun prepare(entry: GameEntry, progress: (Float?) -> Unit): File = withContext(Dispatchers.IO) {
        // A game copied in when it was picked is played from that copy, nothing more to do.
        importedFile(entry.id).takeIf { it.isFile && it.length() > N64Header.HEADER_SIZE }?.let { return@withContext it }
        val target = File(romDir, "${entry.id}.z64")
        if (target.isFile && target.length() > N64Header.HEADER_SIZE) {
            target.setLastModified(System.currentTimeMillis())
            return@withContext target
        }
        if (entry.missing) throw GameFileProblem("The file of this game was moved or deleted. Add it again with Add games. Your saves are kept.")
        val uri = Uri.parse(entry.uri)
        val pending = File(romDir, "${entry.id}.part")
        pending.delete()
        try {
            when (entry.extension) {
                "zip" -> extractZip(uri, entry.size, pending, progress)
                "7z" -> extract7z(uri, pending, progress)
                else -> {
                    requireSpace(entry.size)
                    copyStream(app.contentResolver.openInputStream(uri) ?: throw GameFileProblem("The game file could not be opened. Check that it is still in its folder."),
                        pending, entry.size, progress)
                }
            }
            val header = pending.inputStream().use { it.readExactly(N64Header.HEADER_SIZE) }
            N64Header.problem(header, pending.length())?.let { throw GameFileProblem(it) }
            check(pending.renameTo(target)) { "The game could not be saved on this phone." }
            trimCache(keep = entry.id)
            target
        } catch (problem: Exception) {
            pending.delete()
            throw when (problem) {
                is GameFileProblem -> problem
                is SecurityException -> GameFileProblem("Turbo64 can no longer read this folder. Add the folder again in Settings › Game folders.")
                is java.io.FileNotFoundException -> GameFileProblem("The game file was moved or deleted. Add it again, or rescan your folders.")
                else -> GameFileProblem(problem.message ?: "The game could not be prepared. Please try again.")
            }
        }
    }

    private suspend fun extractZip(uri: Uri, archiveSize: Long, output: File, progress: (Float?) -> Unit) {
        app.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: throw GameFileProblem("This .zip has no N64 game inside.")
                    val inner = entry.name.substringAfterLast('/')
                    if (entry.isDirectory || inner.startsWith('.') || inner.substringAfterLast('.', "").lowercase() !in N64Header.gameExtensions) continue
                    val expected = entry.size.takeIf { it > 0 } ?: (archiveSize * 2)
                    requireSpace(expected)
                    copyStream(zip, output, expected, progress, closeInput = false)
                    return
                }
            }
        } ?: throw GameFileProblem("The game file could not be opened.")
    }

    private suspend fun extract7z(uri: Uri, output: File, progress: (Float?) -> Unit) {
        val descriptor = app.contentResolver.openFileDescriptor(uri, "r") ?: throw GameFileProblem("The game file could not be opened.")
        descriptor.use {
            val channel = FileInputStream(it.fileDescriptor).channel
            try {
                SevenZFile.builder().setSeekableByteChannel(channel).setMaxMemoryLimitKb(SEVEN_Z_MEMORY_KB).get().use { archive ->
                    while (true) {
                        val entry = archive.nextEntry ?: throw GameFileProblem("This .7z has no N64 game inside.")
                        val inner = entry.name.substringAfterLast('/')
                        if (entry.isDirectory || inner.startsWith('.') || inner.substringAfterLast('.', "").lowercase() !in N64Header.gameExtensions) continue
                        requireSpace(entry.size)
                        copyStream(archive.getInputStream(entry), output, entry.size, progress, closeInput = false)
                        return
                    }
                }
            } catch (_: org.apache.commons.compress.MemoryLimitException) {
                throw GameFileProblem("This .7z needs more memory than this phone can safely use. Extract it on a computer and add the game file.")
            }
        }
    }

    private suspend fun copyStream(input: InputStream, output: File, expected: Long, progress: (Float?) -> Unit, closeInput: Boolean = true) {
        val buffer = ByteArray(1 shl 16)
        var copied = 0L
        var lastReport = 0L
        try {
            output.outputStream().use { out ->
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    copied += read
                    if (copied - lastReport > 1 shl 20) {
                        lastReport = copied
                        progress(if (expected > 0) (copied.toFloat() / expected).coerceIn(0f, 1f) else null)
                    }
                }
                out.fd.sync()
            }
        } finally {
            if (closeInput) input.close()
        }
        progress(1f)
    }

    private fun requireSpace(bytes: Long) {
        if (app.filesDir.usableSpace < bytes + 150L * 1024 * 1024) {
            trimCache(keep = null)
            if (app.filesDir.usableSpace < bytes + 150L * 1024 * 1024) {
                throw GameFileProblem("Not enough free space on this phone. Free up ${(bytes / (1024 * 1024)) + 150} MB and try again.")
            }
        }
    }

    /** Keeps the most recently played copies within the limit; saves are never touched. */
    private fun trimCache(keep: String?) {
        val copies = romDir.listFiles { file -> file.extension == "z64" }.orEmpty().sortedByDescending { it.lastModified() }
        var total = 0L
        copies.forEach { file ->
            total += file.length()
            if (total > CACHE_LIMIT && file.nameWithoutExtension != keep) file.delete()
        }
    }

    fun cacheBytes(): Long = romDir.listFiles().orEmpty().sumOf { it.length() }

    fun clearCache() {
        romDir.listFiles().orEmpty().forEach { it.delete() }
    }

    companion object {
        private const val CACHE_LIMIT = 3L * 1024 * 1024 * 1024
        private const val SEVEN_Z_MEMORY_KB = 256 * 1024

        private fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** Reads exactly [count] bytes, or null if the stream ends first. */
internal fun InputStream.readExactly(count: Int): ByteArray? {
    val bytes = ByteArray(count)
    var offset = 0
    while (offset < count) {
        val read = read(bytes, offset, count - offset)
        if (read < 0) return null
        offset += read
    }
    return bytes
}

/** Reads up to [count] bytes; fewer only when the stream is shorter. */
internal fun InputStream.readUpTo(count: Int): ByteArray {
    val bytes = ByteArray(count)
    var offset = 0
    while (offset < count) {
        val read = read(bytes, offset, count - offset)
        if (read < 0) break
        offset += read
    }
    return bytes.copyOf(offset)
}

/** Skips exactly [count] bytes (decompressing streams may skip less per call). */
internal fun InputStream.skipFully(count: Long): Boolean {
    var left = count
    val scratch = ByteArray(1 shl 16)
    while (left > 0) {
        val skipped = skip(left)
        if (skipped > 0) { left -= skipped; continue }
        val read = read(scratch, 0, minOf(scratch.size.toLong(), left).toInt())
        if (read < 0) return false
        left -= read
    }
    return true
}
