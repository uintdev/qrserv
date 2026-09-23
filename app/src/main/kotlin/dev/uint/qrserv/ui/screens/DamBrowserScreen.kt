package dev.uint.qrserv.ui.screens

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.uint.qrserv.R
import dev.uint.qrserv.ui.components.BackNavigationIcon
import dev.uint.qrserv.ui.components.MiddleEllipsisText
import dev.uint.qrserv.ui.components.StatusCard
import dev.uint.qrserv.ui.theme.reducedBottomInsetContentWindowInsets
import dev.uint.qrserv.ui.theme.subtleContainerColor
import dev.uint.qrserv.ui.theme.transparentTopAppBarColors
import dev.uint.qrserv.util.FileSizeFormatter
import dev.uint.qrserv.util.iconForFileName
import dev.uint.qrserv.viewmodel.QRServViewModel
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DamSortField(val labelRes: Int) {
    NAME(R.string.dam_browser_sort_name),
    SIZE(R.string.dam_browser_sort_size),
    TYPE(R.string.dam_browser_sort_type),
    CREATED(R.string.dam_browser_sort_created),
    MODIFIED(R.string.dam_browser_sort_modified),
}

private enum class DamSortOrder { ASCENDING, DESCENDING }

/**
 * ext4 (and Android generally) doesn't expose a true file-creation time through [File] --
 * st_ctime is the closest stand-in available without the API 26+ java.nio.file APIs this app's
 * minSdk doesn't support, even though it technically tracks inode metadata-change time rather
 * than creation specifically.
 */
private fun createdTimeMillis(file: File): Long = try {
    android.system.Os.stat(file.absolutePath).st_ctime * 1000L
} catch (_: Exception) {
    file.lastModified()
}

/** Null when the filesystem couldn't report a modified time at all (lastModified() returns 0L
 * in that case), rather than showing a misleading 1970/01/01. */
private fun formattedModifiedDate(file: File): String? {
    val modified = file.lastModified()
    if (modified <= 0L) return null
    val date = Instant.ofEpochMilli(modified).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))
}

/** Folders always sort before files, regardless of the chosen field/order -- only the ordering
 * within each of those two groups responds to [sortField]/[sortOrder]. */
private fun sortedDamEntries(entries: List<File>, sortField: DamSortField, sortOrder: DamSortOrder): List<File> {
    val fieldComparator: Comparator<File> = when (sortField) {
        DamSortField.NAME -> compareBy { it.name.lowercase() }
        DamSortField.SIZE -> compareBy { it.length() }
        DamSortField.TYPE -> compareBy { it.extension.lowercase() }
        DamSortField.CREATED -> compareBy { createdTimeMillis(it) }
        DamSortField.MODIFIED -> compareBy { it.lastModified() }
    }
    val orderedFieldComparator = if (sortOrder == DamSortOrder.DESCENDING) fieldComparator.reversed() else fieldComparator
    return entries.sortedWith(compareBy<File> { !it.isDirectory }.then(orderedFieldComparator))
}

/** A compact pill matching the height/shape of the buttons it temporarily replaces in
 * [DamSortControlsRow] -- a full-size Material [androidx.compose.material3.TextField] reads as a
 * completely different, much taller kind of control dropped into that row. */
@Composable
private fun CompactDamSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    Row(
        modifier = modifier
            .height(40.dp)
            .background(subtleContainerColor(), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.dam_browser_search_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.dam_browser_search_clear),
                modifier = Modifier.size(18.dp).clickable { onQueryChange("") },
            )
        }
    }
}

@Composable
private fun DamIconButton(onClick: () -> Unit, icon: ImageVector, contentDescription: String?) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = subtleContainerColor()),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun DamSortControlsRow(
    sortField: DamSortField,
    onSortFieldSelected: (DamSortField) -> Unit,
    sortOrder: DamSortOrder,
    onToggleSortOrder: () -> Unit,
    onRefresh: () -> Unit,
    isSearchVisible: Boolean,
    onToggleSearch: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var fieldMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSearchVisible) {
            CompactDamSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            DamIconButton(onToggleSearch, Icons.Filled.Close, stringResource(R.string.dam_browser_search_clear))
        } else {
            Box {
                TextButton(
                    onClick = { fieldMenuExpanded = true },
                    colors = ButtonDefaults.textButtonColors(containerColor = subtleContainerColor()),
                    // ArrowDropDown's glyph doesn't fill its bounding box -- it's inset on the trailing
                    // side, so equal start/end padding reads as a bigger gap after the icon than before
                    // the text. Trimmed end padding compensates.
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                ) {
                    Text(stringResource(sortField.labelRes))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = fieldMenuExpanded,
                    onDismissRequest = { fieldMenuExpanded = false },
                    containerColor = subtleContainerColor(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    DamSortField.entries.forEach { field ->
                        DropdownMenuItem(
                            text = { Text(stringResource(field.labelRes)) },
                            onClick = {
                                fieldMenuExpanded = false
                                onSortFieldSelected(field)
                            },
                            contentPadding = PaddingValues(horizontal = 20.dp),
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f))
            DamIconButton(onToggleSearch, Icons.Filled.Search, stringResource(R.string.dam_browser_search_placeholder))
            Spacer(Modifier.width(8.dp))
            DamIconButton(onRefresh, Icons.Filled.Refresh, stringResource(R.string.dam_browser_refresh))
            Spacer(Modifier.width(8.dp))
            DamIconButton(
                onToggleSortOrder,
                if (sortOrder == DamSortOrder.ASCENDING) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                stringResource(
                    if (sortOrder == DamSortOrder.ASCENDING) {
                        R.string.dam_browser_sort_order_ascending
                    } else {
                        R.string.dam_browser_sort_order_descending
                    },
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DamBrowserScreen(
    viewModel: QRServViewModel,
    modifier: Modifier = Modifier,
    onFileChosen: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val repo = viewModel.directoryLister()
    val root = remember { repo.externalStorageRoot() }
    // rememberSaveable, not remember -- a locale change (this app isn't declared to handle it in
    // configChanges, so the system recreates the Activity to pick up the new resources) would
    // otherwise silently reset this screen back to the root folder, as if it had just been reopened.
    var currentPath by rememberSaveable { mutableStateOf(root) }

    var entries by remember { mutableStateOf<List<File>?>(null) }
    var showLoadingSpinner by remember { mutableStateOf(false) }
    // Bumped by the refresh button to force LaunchedEffect below to re-run for the same path.
    var refreshTrigger by remember { mutableIntStateOf(0) }
    // Hoisted above currentPath so sort/search persists across folder navigation within this
    // screen's lifetime but resets next time it's opened. Saveable for the same locale-recreate
    // reason as currentPath above.
    var sortField by rememberSaveable { mutableStateOf(DamSortField.MODIFIED) }
    var sortOrder by rememberSaveable { mutableStateOf(DamSortOrder.DESCENDING) }
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // Filters purely over what's already been listed -- typing a query never re-reads the
    // folder, same as changing the sort never does.
    val filteredSortedEntries = remember(entries, sortField, sortOrder, searchQuery) {
        entries?.let { list ->
            val filtered = if (searchQuery.isBlank()) list else list.filter { it.name.contains(searchQuery, ignoreCase = true) }
            sortedDamEntries(filtered, sortField, sortOrder)
        }
    }

    // True (after notifying + exiting) if permission was revoked and the caller should bail.
    fun cancelIfPermissionRevoked(): Boolean {
        if (viewModel.hasDirectAccessPermission()) return false
        viewModel.onDamPermissionRevoked()
        onCancel()
        return true
    }

    LaunchedEffect(currentPath, refreshTrigger) {
        if (cancelIfPermissionRevoked()) return@LaunchedEffect
        entries = null
        showLoadingSpinner = false
        // Listing a folder is normally near-instant, so a spinner shown unconditionally would
        // just flash for a frame -- only surface it once the listing has actually taken long
        // enough for a spinner to be worth showing at all.
        val spinnerDelay = launch {
            delay(150.milliseconds)
            showLoadingSpinner = true
        }
        entries = withContext(Dispatchers.IO) { repo.listDirectory(currentPath) }
        spinnerDelay.cancel()
    }

    fun goUpOrCancel() {
        val parent = File(currentPath).parent
        if (currentPath != root && parent != null) {
            currentPath = parent
        } else {
            onCancel()
        }
    }

    // Takes priority over the app-level PredictiveBackHandler while inside a subfolder
    // (registered later in composition, so it shadows the outer one) -- swiping back goes
    // up one folder here, and only falls through to exit the browser once at the root.
    PredictiveBackHandler(enabled = currentPath != root) { progress ->
        try {
            progress.collect { }
            currentPath = File(currentPath).parent ?: root
        } catch (_: CancellationException) {
            // Gesture canceled -- stay in the current folder.
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = reducedBottomInsetContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = {
                    val pathText = "." + currentPath.removePrefix(root).ifEmpty { "/" }
                    val pathScrollState = rememberScrollState()
                    // Scrolled to the end (rather than the start) so the folder the user is
                    // actually in -- the last path segment -- stays visible instead of getting
                    // clipped off when the full path is wider than the title can show.
                    LaunchedEffect(pathText) {
                        pathScrollState.scrollTo(pathScrollState.maxValue)
                    }
                    Text(
                        pathText,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        // Matches BackNavigationIcon's own trailing 12.dp spacer on the left, so
                        // the path gets the same breathing room on both sides.
                        modifier = Modifier.padding(end = 12.dp).horizontalScroll(pathScrollState),
                    )
                },
                navigationIcon = { BackNavigationIcon(onClick = ::goUpOrCancel) },
                colors = transparentTopAppBarColors(),
                modifier = Modifier.padding(top = 4.dp),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DamSortControlsRow(
                sortField = sortField,
                onSortFieldSelected = { sortField = it },
                sortOrder = sortOrder,
                onToggleSortOrder = {
                    sortOrder = if (sortOrder == DamSortOrder.ASCENDING) DamSortOrder.DESCENDING else DamSortOrder.ASCENDING
                },
                onRefresh = { refreshTrigger++ },
                isSearchVisible = isSearchVisible,
                onToggleSearch = {
                    isSearchVisible = !isSearchVisible
                    if (!isSearchVisible) searchQuery = ""
                },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
            )
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (filteredSortedEntries == null) {
                    if (showLoadingSpinner) CircularProgressIndicator()
                } else if (filteredSortedEntries.isEmpty()) {
                    if (searchQuery.isNotBlank()) {
                        StatusCard(
                            icon = Icons.Filled.SearchOff,
                            label = stringResource(R.string.dam_browser_search_noresults_label),
                            message = stringResource(R.string.dam_browser_search_noresults_msg),
                        )
                    } else {
                        StatusCard(
                            icon = Icons.Filled.FolderOff,
                            label = stringResource(R.string.dam_browser_empty_folder_label),
                            message = stringResource(R.string.dam_browser_empty_folder_msg),
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = subtleContainerColor(),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(filteredSortedEntries, key = { _, entry -> entry.path }) { index, entry ->
                                if (index > 0) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                                // Captured once per row so the icon/type shown and the tap behavior below
                                // (including which "no longer exists" wording to use) always agree, even if
                                // the entry is deleted out from under a stale render.
                                val isDirectory = entry.isDirectory
                                ListItem(
                                    headlineContent = {
                                        MiddleEllipsisText(
                                            text = entry.name,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    },
                                    supportingContent = {
                                        if (!isDirectory) {
                                            val sizeText = FileSizeFormatter.humanReadable(entry.length(), LocalConfiguration.current.locales[0])
                                            val modifiedText = formattedModifiedDate(entry)
                                            Text(if (modifiedText != null) "$sizeText · $modifiedText" else sizeText)
                                        }
                                    },
                                    leadingContent = {
                                        Icon(
                                            if (isDirectory) Icons.Filled.Folder else iconForFileName(entry.name),
                                            contentDescription = null,
                                            tint = if (isDirectory) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                LocalContentColor.current
                                            },
                                            modifier = Modifier.size(24.dp),
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.clickable {
                                        if (!cancelIfPermissionRevoked()) {
                                            if (!entry.exists()) {
                                                val messageRes = if (isDirectory) {
                                                    R.string.dam_browser_folder_gone
                                                } else {
                                                    R.string.dam_browser_file_gone
                                                }
                                                android.widget.Toast.makeText(context, messageRes, android.widget.Toast.LENGTH_SHORT).show()
                                            } else if (isDirectory) {
                                                currentPath = entry.path
                                            } else {
                                                onFileChosen(entry.path)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
