package com.appkraft.turbo64.ui

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appkraft.turbo64.BuildConfig
import com.appkraft.turbo64.LibraryViewModel
import com.appkraft.turbo64.Screen
import com.appkraft.turbo64.ads.AdUnits
import com.appkraft.turbo64.ads.Ads
import com.appkraft.turbo64.data.PlayStore
import kotlinx.coroutines.launch

/** Public pages on the Turbo64 website (privacy policy and GPL source code). */
const val WEBSITE = "https://turbonds.site"
const val PRIVACY_URL = "$WEBSITE/turbo64/privacy/"
const val SUPPORT_EMAIL = "digitalmizer@gmail.com"
const val SOURCE_URL = "https://github.com/AnkitVishwa114/turbo64"

@Composable
fun SettingsScreen(viewModel: LibraryViewModel, actions: AppActions) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val state = viewModel.state
    var cacheBytes by remember { mutableLongStateOf(viewModel.store.cacheBytes()) }

    val exportSaves = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching { viewModel.saves.exportAll(context, uri) }
                .onSuccess { viewModel.showNotice(if (it == 0) "There are no saves to back up yet" else "Backed up $it save files") }
                .onFailure { viewModel.showNotice("The backup could not be written. Try another location.") }
        }
    }
    val importSaves = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching { viewModel.saves.importAll(context, uri) }
                .onSuccess { viewModel.showNotice(if (it == 0) "No Turbo64 saves found in that file" else "Restored $it save files") }
                .onFailure { viewModel.showNotice("That file is not a Turbo64 backup.") }
        }
    }

    var saveBytes by remember { mutableLongStateOf(viewModel.saves.totalBytes()) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var backupLabel by remember { mutableStateOf(viewModel.saves.backupFolderLabel()) }
    val pickBackupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        if (tree != null) {
            runCatching { viewModel.saves.setBackupFolder(tree) }
                .onSuccess {
                    backupLabel = viewModel.saves.backupFolderLabel()
                    scope.launch { viewModel.showNotice(if (viewModel.saves.autoBackup()) "Automatic backup is on" else "Automatic backup is on. It starts after your next game.") }
                }
                .onFailure { viewModel.showNotice("That folder can't be used. Try another one.") }
        }
    }

    confirm?.let { question -> ConfirmDialog(question) { confirm = null } }

    Column(Modifier.fillMaxSize()) {
        TopBar("Settings")
        LazyColumn(contentPadding = PaddingValues(start = Dimens.Edge, end = Dimens.Edge, bottom = Dimens.Section), verticalArrangement = Arrangement.spacedBy(Dimens.Tight)) {
            item { GroupLabel("Your games") }
            item {
                SettingsGroup {
                    state.folders.forEachIndexed { index, folder ->
                        if (index > 0) GroupDivider()
                        SettingsRow(Icons.Rounded.Folder, viewModel.store.folderLabel(folder), "Tap the icon to stop using this folder",
                            trailing = {
                                Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Remove folder", tint = TextMuted,
                                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(50)).clickableTick { viewModel.removeFolder(folder) })
                            })
                    }
                    if (state.folders.isNotEmpty()) GroupDivider()
                    if (state.pickedCount > 0) {
                        SettingsRow(Icons.Rounded.Description, "${state.pickedCount} game${if (state.pickedCount == 1) "" else "s"} added one by one",
                            "Tap the icon to remove them from the library. Saves are kept.",
                            trailing = {
                                Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Remove picked files", tint = TextMuted,
                                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(50)).clickableTick {
                                        if (state.importedCount == 0) viewModel.removeAllFiles()
                                        else confirm = Confirm("Remove games added one by one?",
                                            "${state.importedCount} of them are kept inside Turbo64 and their copies are deleted. Pick them again to play. Saves are not touched.",
                                            "Remove", danger = true) { viewModel.removeAllFiles() }
                                    })
                            })
                        GroupDivider()
                    }
                    SettingsRow(Icons.Rounded.CreateNewFolder, "Add a folder", null, onClick = actions.addFolder)
                    GroupDivider()
                    SettingsRow(Icons.AutoMirrored.Rounded.NoteAdd, "Add game files", null, onClick = actions.addFiles)
                    GroupDivider()
                    SettingsRow(Icons.Rounded.Refresh, "Rescan library", if (state.scanning) "Scanning…" else "${state.games.size} games", onClick = { viewModel.rescan() })
                }
            }

            item { GroupLabel("Appearance") }
            item {
                SettingsGroup {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Theme", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                                PlatformPill(label, ThemePreference.mode == key) { ThemePreference.set(context, key) }
                            }
                        }
                    }
                    GroupDivider()
                    SettingsRow(Icons.Rounded.Vibration, "Touch vibration", null,
                        trailing = { ToggleSwitch(Haptics.enabled) { Haptics.setEnabled(context, it) } },
                        onClick = { Haptics.setEnabled(context, !Haptics.enabled) })
                }
            }

            item { GroupLabel("Saves") }
            item {
                SettingsGroup {
                    SettingsRow(Icons.Rounded.Folder, "Automatic backup",
                        backupLabel?.let { "After every game, to $it" } ?: "Off. Pick a folder to keep saves safe if the app is removed",
                        trailing = if (backupLabel == null) null else ({
                            Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Turn off automatic backup", tint = TextMuted,
                                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(50)).clickableTick {
                                    viewModel.saves.setBackupFolder(null)
                                    backupLabel = null
                                    viewModel.showNotice("Automatic backup is off")
                                })
                        }),
                        onClick = { pickBackupFolder.launch(null) })
                    GroupDivider()
                    SettingsRow(Icons.Rounded.Backup, "Back up saves now", "Save to Drive or move to a new phone",
                        onClick = { exportSaves.launch("Turbo64-saves.zip") })
                    GroupDivider()
                    SettingsRow(Icons.Rounded.Restore, "Restore saves", null,
                        onClick = { importSaves.launch(arrayOf("application/zip", "application/octet-stream")) })
                }
            }

            item { GroupLabel("Storage") }
            item {
                SettingsGroup {
                    SettingsRow(Icons.Rounded.CleaningServices, "Clear game copies",
                        "${Formatter.formatShortFileSize(context, cacheBytes)} used. Games open a little slower the next time. Saves are kept.",
                        onClick = {
                            viewModel.store.clearCache()
                            cacheBytes = viewModel.store.cacheBytes()
                            viewModel.showNotice("Game copies cleared")
                        })
                    if (state.importedCount > 0) {
                        GroupDivider()
                        SettingsRow(Icons.Rounded.SdStorage, "Games kept inside Turbo64",
                            "${state.importedCount} game${if (state.importedCount == 1) "" else "s"}, ${Formatter.formatShortFileSize(context, viewModel.store.importedBytes())}. " +
                                "Copied when picked, so they always open. Removed if you uninstall the app or remove the game.")
                    }
                    GroupDivider()
                    SettingsRow(Icons.Rounded.SportsEsports, "Reset controls and player settings",
                        "Button layout, sizes, picture options and controller buttons go back to how they started",
                        onClick = {
                            confirm = Confirm("Reset controls and player settings?", "Your games and saves are not touched.", "Reset", danger = false) {
                                viewModel.resetPlayerSettings()
                            }
                        })
                    GroupDivider()
                    SettingsRow(Icons.Rounded.FolderOff, "Remove all games from the library",
                        "Folders and picked files are forgotten. Your game files and saves stay.",
                        onClick = {
                            confirm = Confirm("Remove all games from the library?",
                                "Turbo64 forgets your folders and files, and you can add them again any time. Your game files and saves are not deleted." +
                                    if (state.importedCount > 0) " Games kept inside Turbo64 are deleted, so pick them again to play." else "",
                                "Remove all", danger = true) {
                                viewModel.clearLibrary()
                                cacheBytes = viewModel.store.cacheBytes()
                            }
                        })
                    GroupDivider()
                    SettingsRow(Icons.Rounded.DeleteForever, "Delete all saves",
                        "${Formatter.formatShortFileSize(context, saveBytes)} of saves, save states and screenshots",
                        onClick = {
                            confirm = Confirm("Delete all saves?",
                                "Every in-game save, save state and screenshot of every game is deleted. This can't be undone. Back up your saves first if you may want them later.",
                                "Delete all saves", danger = true) {
                                viewModel.deleteAllSaves()
                                saveBytes = viewModel.saves.totalBytes()
                                clearArtCache()
                            }
                        })
                }
            }

            if (!AdUnits.notConfigured) item { GroupLabel("Ads") }
            if (!AdUnits.notConfigured) item {
                SettingsGroup {
                    val adFree = Ads.adFreeUntil > System.currentTimeMillis()
                    SettingsRow(Icons.Rounded.HourglassTop, "Go ad-free for 1 hour",
                        if (adFree) "Ad-free until ${android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(Ads.adFreeUntil))}" else "Watch one short video",
                        onClick = {
                            activity?.let { host -> Ads.showAdFreeReward(host) { earned -> viewModel.showNotice(rewardNotice(host, earned)) } }
                        })
                    if (Ads.privacyOptionsRequired) {
                        GroupDivider()
                        SettingsRow(Icons.Rounded.Shield, "Ad privacy options", null, onClick = { activity?.let(Ads::showPrivacyOptions) })
                    }
                }
            }

            item { GroupLabel("Turbo64") }
            item {
                SettingsGroup {
                    SettingsRow(Icons.Rounded.StarRate, "Rate Turbo64", null, onClick = { PlayStore.openListing(context) })
                    GroupDivider()
                    SettingsRow(Icons.Rounded.MailOutline, "Contact support", SUPPORT_EMAIL, onClick = { contactSupport(context) })
                    GroupDivider()
                    SettingsRow(Icons.Rounded.PrivacyTip, "Privacy policy", null, onClick = { viewModel.navigate(Screen.PRIVACY) })
                    GroupDivider()
                    SettingsRow(Icons.Rounded.Code, "Source code and licences", null, onClick = { viewModel.navigate(Screen.ABOUT) })
                    GroupDivider()
                    SettingsRow(Icons.Rounded.Info, "Version", BuildConfig.VERSION_NAME)
                }
            }
            item {
                Text("No games or BIOS included. Nintendo 64 is a trademark of Nintendo; Turbo64 is not affiliated with Nintendo.",
                    style = MaterialTheme.typography.bodySmall, color = TextFaint, modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val notices = remember { runCatching { context.assets.open("licenses/NOTICE.txt").bufferedReader().readText() }.getOrDefault("") }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Source code and licences", onBack)
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppMark(52.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Turbo64 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text("by AppKraft Studio", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            Text("Turbo64 is open source under GPL-3.0.",
                style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            Text("No games or BIOS included. Nintendo 64 is a trademark of Nintendo; Turbo64 is not affiliated with or endorsed by Nintendo.",
                style = MaterialTheme.typography.bodySmall, color = TextFaint)
            PrimaryButton("Get the source code", { open(context, SOURCE_URL) }, icon = Icons.AutoMirrored.Rounded.OpenInNew, height = 44.dp, modifier = Modifier.fillMaxWidth())
            SettingsGroup {
                Text(notices, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(14.dp))
            }
        }
    }
}

/** Short sections of the privacy policy, the same text as the privacy page on the website. */
private val privacySections = listOf(
    "Summary" to "No account or login. Your games, saves and settings stay on your device. The app shows no ads, does not use your advertising ID and does not sell personal information.",
    "Your games and files" to "You pick folders or files with Android's picker; the app can read only what you pick. It reads game files to show names and regions and copies a game into private storage to play it. Nothing is uploaded.",
    "Saves and settings" to "In-game saves, save states, screenshots of your games, play time, favourites and settings are stored only on your device. Cloud backup is off for this data. You can export your saves to a file you choose, and you can turn on automatic backup to a folder you choose on your device.",
    "Google Play" to "Google Play shows the rating sheet and checks for updates. When a newer version is out, the app asks you to install it before you continue. The app never learns your rating.",
    "Permissions" to "Internet: rating and update checks only; games play offline. Vibration: optional touch feedback and rumble. No microphone, camera, contacts, location, calls or SMS.",
    "Children" to "The app is not directed to children under 13 and does not knowingly collect their information.",
    "Your choices" to "Clear the app's storage or uninstall it to remove all of its data. Game files and backups in your own folders are never deleted by the app.",
    "Contact" to "AppKraft Studio · digitalmizer@gmail.com"
)

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Privacy policy", onBack)
        Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Last updated 16 November 2026", style = MaterialTheme.typography.bodySmall, color = TextFaint)
            SettingsGroup {
                privacySections.forEachIndexed { index, (title, body) ->
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    }
                    if (index != privacySections.lastIndex) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(Stroke.copy(alpha = 0.5f)))
                }
            }
            SecondaryButton("Open on the website", { open(context, PRIVACY_URL) }, icon = Icons.AutoMirrored.Rounded.OpenInNew, height = 44.dp, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Opens the mail app with the version and phone model filled in, so replies can help quickly. */
private fun contactSupport(context: android.content.Context) {
    val details = "\n\n---\nTurbo64 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}"
    val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, "Turbo64 support")
        .putExtra(Intent.EXTRA_TEXT, details)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // No mail app: open a plain mailto link so the browser or another app can take it.
    runCatching { context.startActivity(mail) }.onFailure { open(context, "mailto:$SUPPORT_EMAIL") }
}

private fun open(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(50)).clickableTick(onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = TextMuted, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).border(1.dp, Stroke.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
        content = content
    )
}

@Composable
private fun GroupDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 54.dp).height(1.dp).background(Stroke.copy(alpha = 0.5f)))
}

/** A question shown before something that removes data. */
private class Confirm(val title: String, val body: String, val action: String, val danger: Boolean, val run: () -> Unit)

@Composable
private fun ConfirmDialog(confirm: Confirm, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(shape = RoundedCornerShape(22.dp), color = Surface, contentColor = TextPrimary) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(confirm.title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Text(confirm.body, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Cancel", onDismiss, height = 44.dp, modifier = Modifier.weight(1f))
                    PrimaryButton(confirm.action, { confirm.run(); onDismiss() }, height = 44.dp, modifier = Modifier.weight(1f),
                        container = if (confirm.danger) Coral else Accent, content = if (confirm.danger) Color.White else OnAccent)
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String?, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickableTick(onClick) else Modifier).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

@Composable
private fun ToggleSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(checked, onChange, colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = Accent,
        uncheckedThumbColor = TextMuted, uncheckedTrackColor = Raised, uncheckedBorderColor = Stroke))
}

@Suppress("unused")
private val unusedColor = Color.Unspecified
