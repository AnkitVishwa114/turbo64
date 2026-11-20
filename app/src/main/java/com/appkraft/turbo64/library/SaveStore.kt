package com.appkraft.turbo64.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Per-game saves in private storage, keyed by the game's stable id (game code + header CRC), so
 * an exported save matches the same game on another phone. Layout of `saves/<id>/`:
 * - `battery.sav` — the in-game save, with `battery.sav.bak1..3` kept from earlier sessions
 * - `autosave.state` — written when leaving or backgrounding a game; "Continue" resumes it
 * - `slot1.state` … `slot10.state` — manual save states, each with a `slotN.jpg` screenshot
 * - `cover.jpg` — the last screen of the game, shown on its library card
 */
class SaveStore(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "saves").apply { mkdirs() }
    private val settings = app.getSharedPreferences("saves_settings", Context.MODE_PRIVATE)

    // ---- Automatic backup to a folder the user picked (survives an uninstall or a new phone) -------

    /** The folder that receives a fresh backup after every game, or null when automatic backup is off. */
    fun backupFolder(): Uri? = settings.getString("backupFolder", null)?.let(Uri::parse)

    fun backupFolderLabel(): String? = backupFolder()?.let { tree ->
        runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfter(':').ifBlank { "Internal storage" } }.getOrDefault("Chosen folder")
    }

    /** Turns automatic backup on for [tree], or off when null. Read and write access is kept across restarts. */
    fun setBackupFolder(tree: Uri?) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        backupFolder()?.let { old -> runCatching { app.contentResolver.releasePersistableUriPermission(old, flags) } }
        if (tree != null) app.contentResolver.takePersistableUriPermission(tree, flags)
        settings.edit().putString("backupFolder", tree?.toString()).apply()
    }

    fun lastAutoBackup(): Long = settings.getLong("lastAutoBackup", 0)

    /**
     * Writes all saves as one .zip into the backup folder, replacing the previous automatic
     * backup. Returns false when automatic backup is off or the folder can no longer be written.
     */
    suspend fun autoBackup(): Boolean = withContext(Dispatchers.IO) {
        val tree = backupFolder() ?: return@withContext false
        if (root.walkTopDown().none { it.isFile }) return@withContext false
        runCatching {
            val resolver = app.contentResolver
            val parentId = DocumentsContract.getTreeDocumentId(tree)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            var existing: Uri? = null
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == AUTO_BACKUP_NAME) existing = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                }
            }
            val target = existing ?: DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, parentId), "application/zip", AUTO_BACKUP_NAME)
                ?: error("The backup file could not be created.")
            exportAll(app, target)
            settings.edit().putLong("lastAutoBackup", System.currentTimeMillis()).apply()
        }.isSuccess
    }

    /** Space used by every save, save state and screenshot. */
    fun totalBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Deletes the saves, save states, screenshots and cheats of every game. Cannot be undone. */
    fun deleteEverything() {
        root.listFiles().orEmpty().forEach { it.deleteRecursively() }
    }

    fun folder(id: String): File = File(root, safe(id)).apply { mkdirs() }

    fun hasContinue(id: String): Boolean = File(File(root, safe(id)), AUTOSAVE).isFile

    fun hasAnySave(id: String): Boolean = File(root, safe(id)).listFiles().orEmpty().isNotEmpty()

    /** The last screen of the game (written when the player pauses or closes), or null before the first play. */
    fun cover(id: String): File? = File(File(root, safe(id)), COVER).takeIf { it.isFile && it.length() > 0 }

    /**
     * Brings in a save made by another N64 emulator. `.srm` files (RetroArch, Mupen64Plus-Next)
     * replace the whole in-game save; `.eep`, `.mpk`, `.sra` and `.fla` files are placed in their
     * part of it. The current save is kept as `battery.sav.before-import`.
     */
    fun importForeignSave(id: String, name: String, bytes: ByteArray) {
        val folder = folder(id)
        val current = File(folder, BATTERY)
        val merged = mergeForeignSave(current.takeIf { it.isFile }?.readBytes(), name, bytes)
        if (current.isFile) current.copyTo(File(folder, "$BATTERY.before-import"), overwrite = true)
        val pending = File(folder, "$BATTERY.tmp")
        pending.writeBytes(merged)
        check(pending.renameTo(current)) { "The save could not be imported." }
        File(folder, AUTOSAVE).delete()
    }

    /** Drops the resume point so the game boots fresh (the in-game save is kept). */
    fun clearContinue(id: String) {
        File(File(root, safe(id)), AUTOSAVE).delete()
    }

    /** Deletes every save of this game, including in-game progress. */
    fun deleteAll(id: String) {
        File(root, safe(id)).deleteRecursively()
    }

    /** Keeps the last three in-game saves: called once each time a game starts. */
    fun rotateBackups(id: String) {
        val folder = folder(id)
        val current = File(folder, BATTERY)
        if (!current.isFile || current.length() == 0L) return
        val newest = File(folder, "$BATTERY.bak1")
        if (newest.isFile && newest.readBytes().contentEquals(current.readBytes())) return
        File(folder, "$BATTERY.bak3").delete()
        File(folder, "$BATTERY.bak2").takeIf { it.isFile }?.renameTo(File(folder, "$BATTERY.bak3"))
        newest.takeIf { it.isFile }?.renameTo(File(folder, "$BATTERY.bak2"))
        current.copyTo(newest, overwrite = true)
    }

    /**
     * Earlier copies of the in-game save, newest first: the last three sessions, the one kept
     * before a state was loaded and the one kept before a backup import.
     */
    fun backups(id: String): List<File> {
        val folder = File(root, safe(id))
        val current = File(folder, BATTERY).takeIf { it.isFile }?.readBytes()
        return BACKUP_NAMES.map { File(folder, it) }
            .filter { it.isFile && it.length() > 0 }
            .distinctBy { it.readBytes().contentHashCode() }
            .filter { current == null || !it.readBytes().contentEquals(current) }
            .sortedByDescending { it.lastModified() }
    }

    /**
     * Makes [backup] the in-game save again. The current save is kept as `battery.sav.before-restore`
     * and the resume point is dropped, so the next start really uses the restored save.
     */
    fun restore(id: String, backup: File) {
        val folder = folder(id)
        require(backup.parentFile == folder && backup.isFile) { "That save is no longer there." }
        val current = File(folder, BATTERY)
        if (current.isFile) current.copyTo(File(folder, "$BATTERY.before-restore"), overwrite = true)
        val pending = File(folder, "$BATTERY.tmp")
        backup.copyTo(pending, overwrite = true)
        check(pending.renameTo(current)) { "The save could not be restored." }
        File(folder, AUTOSAVE).delete()
    }

    /** Copies everything into one .zip at [destination] (a document the user picked). */
    suspend fun exportAll(context: Context, destination: Uri): Int = withContext(Dispatchers.IO) {
        var files = 0
        context.contentResolver.openOutputStream(destination, "wt")?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { file ->
                    zip.putNextEntry(ZipEntry("Turbo64-saves/" + file.relativeTo(root).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    files++
                }
            }
        } ?: error("The backup file could not be created.")
        files
    }

    /**
     * Restores a backup made by [exportAll]. Existing saves of the same game are kept as
     * `*.before-import` so an import can never silently destroy progress.
     */
    suspend fun importAll(context: Context, source: Uri): Int = withContext(Dispatchers.IO) {
        var files = 0
        context.contentResolver.openInputStream(source)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val path = entry.name.removePrefix("Turbo64-saves/")
                    if (entry.isDirectory || path.contains("..") || path.count { it == '/' } != 1) continue
                    val (game, name) = path.split('/')
                    if (name.isBlank() || name.endsWith(".tmp")) continue
                    val target = File(folder(game), name)
                    if (target.isFile) target.copyTo(File(target.parentFile, "$name.before-import"), overwrite = true)
                    target.outputStream().use { zip.copyTo(it) }
                    files++
                }
            }
        } ?: error("The backup file could not be opened.")
        files
    }

    companion object {
        const val BATTERY = "battery.sav"
        const val AUTOSAVE = "autosave.state"
        const val COVER = "cover.jpg"
        const val AUTO_BACKUP_NAME = "Turbo64-saves-auto.zip"
        const val SLOTS = 10

        // The engine keeps every kind of N64 save in one block, in this order.
        const val EEPROM_SIZE = 0x800
        const val MEMPAK_SIZE = 0x8000 * 4
        const val SRAM_SIZE = 0x8000
        const val FLASH_SIZE = 0x20000
        const val SAVE_SIZE = EEPROM_SIZE + MEMPAK_SIZE + SRAM_SIZE + FLASH_SIZE

        /** The in-game save with [bytes] from another emulator's file placed in it. */
        fun mergeForeignSave(current: ByteArray?, name: String, bytes: ByteArray): ByteArray {
            val extension = name.substringAfterLast('.', "").lowercase()
            if (extension == "srm") {
                require(bytes.size == SAVE_SIZE) { "That .srm file is not an N64 save." }
                return bytes.copyOf()
            }
            val (offset, limit) = when (extension) {
                "eep" -> 0 to EEPROM_SIZE
                "mpk" -> EEPROM_SIZE to MEMPAK_SIZE
                "sra" -> EEPROM_SIZE + MEMPAK_SIZE to SRAM_SIZE
                "fla" -> EEPROM_SIZE + MEMPAK_SIZE + SRAM_SIZE to FLASH_SIZE
                else -> throw IllegalArgumentException("Pick a .srm, .eep, .sra, .fla or .mpk save file.")
            }
            require(bytes.isNotEmpty() && bytes.size <= limit) { "That save file has the wrong size." }
            val base = current?.takeIf { it.size == SAVE_SIZE }?.copyOf() ?: blankSave()
            bytes.copyInto(base, offset)
            return base
        }

        /** An empty save: erased chips read as 0xFF, the Controller Paks as zero until the game formats them. */
        fun blankSave(): ByteArray = ByteArray(SAVE_SIZE) { 0xFF.toByte() }.also { it.fill(0, EEPROM_SIZE, EEPROM_SIZE + MEMPAK_SIZE) }
        private val BACKUP_NAMES = listOf("bak1", "bak2", "bak3", "before-load", "before-import", "before-restore").map { "$BATTERY.$it" }

        /** Folder-safe id (ids are letters, digits and dashes already; this is a guard). */
        fun safe(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }
}
