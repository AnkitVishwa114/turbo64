package com.appkraft.turbo64.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.appkraft.turbo64.LibraryTab
import com.appkraft.turbo64.LibraryViewModel
import com.appkraft.turbo64.Screen
import com.appkraft.turbo64.library.GameEntry

private val Gutter = Dimens.Edge

@Composable
fun HomeScreen(viewModel: LibraryViewModel, actions: AppActions) {
    val state = viewModel.state
    val resume = remember(state.games) { state.continueGame(viewModel.saves) }
    val hero = resume ?: state.recentlyPlayed.firstOrNull() ?: state.recentlyAdded.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        TopBar(null) { AddGamesButton(actions.chooseHowToAdd) }
        LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = Dimens.Section), verticalArrangement = Arrangement.spacedBy(Dimens.Section)) {
            if (state.scanning && state.games.isEmpty()) item { HomeSkeleton() }
            else if (state.games.isEmpty()) item { EmptyLibrary(actions, onRescan = { viewModel.rescan() }) }
            hero?.let { game ->
                item(key = "hero") {
                    HeroCard(game, viewModel, resuming = game == resume, onPlay = { actions.play(game) }, onDetails = { actions.openGame(game) })
                }
            }
            shelf("Recently played", state.recentlyPlayed.filter { it != hero }.take(12), viewModel, actions) {
                viewModel.setTab(LibraryTab.RECENT); viewModel.navigate(Screen.LIBRARY)
            }
            shelf("Favourites", state.favorites.take(12), viewModel, actions) {
                viewModel.setTab(LibraryTab.FAVORITES); viewModel.navigate(Screen.LIBRARY)
            }
            if (state.games.isNotEmpty()) item(key = "native-ad") { NativeAdCard(slot = 0, modifier = Modifier.padding(horizontal = Gutter)) }
            shelf("Recently added", state.recentlyAdded.take(12), viewModel, actions) {
                viewModel.setTab(LibraryTab.ALL); viewModel.navigate(Screen.LIBRARY)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.shelf(
    title: String, games: List<GameEntry>, viewModel: LibraryViewModel, actions: AppActions, onSeeAll: () -> Unit
) {
    if (games.isEmpty()) return
    item(key = "shelf-$title") {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Item)) {
            SectionTitle(title, onSeeAll)
            LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(Dimens.Item)) {
                itemsIndexed(games, key = { _, it -> "$title-${it.id}" }) { index, game -> ShelfCard(game, viewModel, actions, Modifier.animateItem().riseIn("$title-${game.id}", index)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, onSeeAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = Gutter, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        if (onSeeAll != null) {
            Text("See all", style = MaterialTheme.typography.labelMedium, color = TextMuted,
                modifier = Modifier.clip(CircleShape).clickableTick(onSeeAll).padding(horizontal = 10.dp, vertical = 8.dp))
        }
    }
}

/** The big card at the top: the game to jump back into, with its last screen edge to edge. */
@Composable
private fun HeroCard(game: GameEntry, viewModel: LibraryViewModel, resuming: Boolean, onPlay: () -> Unit, onDetails: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = { Haptics.tick(); onDetails() }, interactionSource = interaction,
        modifier = Modifier.padding(horizontal = Gutter).fillMaxWidth().pressScale(interaction, 0.99f),
        shape = RoundedCornerShape(Dimens.CardRadius), color = Surface, contentColor = TextPrimary,
        shadowElevation = if (AppColors.current.isLight) 3.dp else 0.dp
    ) {
        Column {
            GameArt(game, viewModel.store, Modifier.fillMaxWidth().aspectRatio(16f / 9f), corner = 0.dp, iconScale = 0.5f)
            Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // Two lines, so a long name is read in full next to the button instead of cut short.
                    Text(game.title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(heroInfo(game), style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                PrimaryButton(if (resuming) "Continue" else "Play", onPlay, icon = Icons.Rounded.PlayArrow, height = 42.dp)
            }
        }
    }
}

/** One quiet line under the hero title: play time and when, or the region for a game not played yet. */
private fun heroInfo(game: GameEntry): String = when {
    game.missing -> "File missing. Add it again to play"
    game.lastPlayed > 0 -> "${formatPlayTime(game.playTimeMs)} · ${playedWhen(game.lastPlayed)}"
    game.subtitle.isNotBlank() -> "${game.subtitle} · Not played yet"
    else -> "Not played yet"
}

/** Placeholder layout while the first scan runs: hero and a shelf, shimmering. */
@Composable
private fun HomeSkeleton() {
    Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(Dimens.Section)) {
        LoadingPlaceholder(Modifier.fillMaxWidth().aspectRatio(1.25f).clip(RoundedCornerShape(Dimens.CardRadius + 2.dp)))
        LoadingPlaceholder(Modifier.width(150.dp).height(18.dp).clip(RoundedCornerShape(6.dp)))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Item)) {
            repeat(3) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoadingPlaceholder(Modifier.size(152.dp, 114.dp).clip(RoundedCornerShape(Dimens.TileRadius)))
                    LoadingPlaceholder(Modifier.width(90.dp).height(12.dp).clip(RoundedCornerShape(5.dp)))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfCard(game: GameEntry, viewModel: LibraryViewModel, actions: AppActions, modifier: Modifier = Modifier) {
    GameCard(game, viewModel, modifier.width(152.dp), info = if (game.playTimeMs > 0) formatPlayTime(game.playTimeMs) else game.subtitle.ifBlank { "Not played yet" },
        onClick = { actions.play(game) }, onLongClick = { actions.openGame(game) })
}

/**
 * One game: its picture edge to edge with clean rounded corners, then the title and one line of
 * detail lined up with the picture's left edge. Used on the home shelves and in the library.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GameCard(
    game: GameEntry, viewModel: LibraryViewModel, modifier: Modifier = Modifier, info: String,
    onClick: () -> Unit, onLongClick: () -> Unit, onMore: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.pressScale(interaction, 0.96f)
            .combinedClickable(interactionSource = interaction, indication = null,
                onClick = { Haptics.tick(); onClick() }, onLongClick = { Haptics.heavy(); onLongClick() })
    ) {
        Box {
            GameArt(game, viewModel.store, Modifier.fillMaxWidth().aspectRatio(4f / 3f), corner = Dimens.TileRadius)
            if (game.favorite) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Favorite, contentDescription = "Favourite", tint = Color.White, modifier = Modifier.size(13.dp))
            }
            if (onMore != null) Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                .clickableTick(onMore), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = "Game options", tint = Color.White, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(game.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(info, style = MaterialTheme.typography.bodySmall, color = TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp))
    }
}

/** When a game was played, short enough for tiles and card lines: "Just now", "5 min ago"… */
internal fun playedWhen(at: Long): String = ShortTime.ago(at)

/** Play time, short: "< 1 min", "12 min", "2 h 5 min". */
internal fun formatPlayTime(ms: Long): String = ShortTime.played(ms)

// ---- First launch, empty states, adding games -----------------------------------------------

@Composable
fun Onboarding(actions: AppActions) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        CubeBuild(92.dp)
        Spacer(Modifier.height(18.dp))
        Text("Play your N64 games", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("Add the games you own to get started.",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        Spacer(Modifier.height(22.dp))
        AddOptions(actions, Modifier.widthIn(max = 460.dp))
        Spacer(Modifier.height(22.dp))
        Text("Turbo64 has no games in it. It plays the ones you own.", style = MaterialTheme.typography.bodySmall, color = TextFaint, textAlign = TextAlign.Center)
    }
}

/** The two ways to add games, side by side with their trade-offs. */
@Composable
fun AddOptions(actions: AppActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OptionCard(
            icon = Icons.Rounded.CreateNewFolder, title = "Choose a folder",
            body = "New games in it show up on their own",
            onClick = actions.addFolder
        )
        OptionCard(
            icon = Icons.AutoMirrored.Rounded.NoteAdd, title = "Pick game files",
            body = ".z64, .n64, .v64, .zip or .7z",
            onClick = actions.addFiles
        )
        Text("Tip: Download itself can't be a folder. Use a folder inside it.",
            style = MaterialTheme.typography.bodySmall, color = TextFaint, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun OptionCard(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = { Haptics.tick(); onClick() }, interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().pressScale(interaction, 0.98f),
        shape = RoundedCornerShape(Dimens.TileRadius), color = Surface, contentColor = TextPrimary
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text(body, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = TextFaint, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGamesSheet(actions: AppActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Background) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
            Text("Add games", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            AddOptions(actions)
        }
    }
}

@Composable
internal fun ScanningPanel(count: Int) {
    Column(Modifier.fillMaxWidth().padding(vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Finding your games…", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(if (count > 0) "$count found so far" else "Looking through what you added", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Spacer(Modifier.height(14.dp))
        ProgressTrack(null, Modifier.width(180.dp))
    }
}

@Composable
internal fun EmptyLibrary(actions: AppActions, onRescan: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = TextFaint, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(10.dp))
        Text("No N64 games found yet", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Add .z64, .n64, .v64, .zip or .7z files, then rescan.",
            style = MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Rescan", onRescan, height = 42.dp)
            PrimaryButton("Add games", actions.chooseHowToAdd, height = 42.dp)
        }
    }
}

@Composable
internal fun PreparingDialog(game: GameEntry, progress: Float?, viewModel: LibraryViewModel, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(24.dp), color = Surface, contentColor = TextPrimary, border = BorderStroke(1.dp, Stroke)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameArt(game, viewModel.store, Modifier.size(52.dp), corner = 14.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (game.isArchive) "Unpacking game" else "Getting ready", style = MaterialTheme.typography.titleLarge)
                        Text(game.title, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                ProgressTrack(progress)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("First time only", style = MaterialTheme.typography.bodySmall, color = TextFaint, modifier = Modifier.weight(1f))
                    if (progress != null) Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Accent)
                }
                SecondaryButton("Cancel", onCancel, height = 42.dp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Shown while picked files are read and, where needed, copied into the app. */
@Composable
internal fun ImportingDialog(importing: com.appkraft.turbo64.Importing, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(24.dp), color = Surface, contentColor = TextPrimary, border = BorderStroke(1.dp, Stroke)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandSpinner(44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (importing.total > 1) "Adding games" else "Adding game", style = MaterialTheme.typography.titleLarge)
                        Text(if (importing.total > 1) "${importing.index + 1} of ${importing.total}" else "Checking the file",
                            style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                ProgressTrack(importing.progress)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Keep the app open", style = MaterialTheme.typography.bodySmall, color = TextFaint, modifier = Modifier.weight(1f))
                    importing.progress?.let { Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Accent) }
                }
                SecondaryButton("Cancel", onCancel, height = 42.dp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Suppress("unused")
private val keepColor = Color.Unspecified
