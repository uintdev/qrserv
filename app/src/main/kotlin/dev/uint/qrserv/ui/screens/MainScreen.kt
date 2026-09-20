package dev.uint.qrserv.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.uint.qrserv.BuildConfig
import dev.uint.qrserv.R
import dev.uint.qrserv.data.AddressGroup
import dev.uint.qrserv.data.AppUiState
import dev.uint.qrserv.data.ImportProgress
import dev.uint.qrserv.data.PageType
import dev.uint.qrserv.ui.components.MiddleEllipsisText
import dev.uint.qrserv.ui.components.QrCodeImage
import dev.uint.qrserv.ui.components.StatusCard
import dev.uint.qrserv.ui.components.middleEllipsis
import dev.uint.qrserv.ui.theme.BrandError
import dev.uint.qrserv.ui.theme.reducedBottomInsetContentWindowInsets
import dev.uint.qrserv.ui.theme.subtleContainerColor
import dev.uint.qrserv.ui.theme.transparentTopAppBarColors
import dev.uint.qrserv.util.FileSizeFormatter
import dev.uint.qrserv.util.iconForFileName
import dev.uint.qrserv.viewmodel.QRServViewModel
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: QRServViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val debugBuildToastMessage = stringResource(R.string.debug_build_toast)
    // Not a plain animateFloatAsState: the ViewModel can finish (server already running, etc.)
    // faster than the bar's own fill animation, cutting away mid-fill instead of visibly reaching
    // 100%. lastProgress keeps the bar/label on their last real values through that tail animation,
    // since uiState.importProgress resets to null as soon as the ViewModel is done.
    val progressAnim = remember { Animatable(0f) }
    var progressBarPending by remember { mutableStateOf(false) }
    var lastProgress by remember { mutableStateOf<ImportProgress?>(null) }
    // Keyed on Unit (not uiState.importProgress) so this coroutine survives for the composable's
    // whole lifetime -- the ViewModel resets importProgress to null immediately once it's done,
    // and keying on that value would cancel an in-flight animateTo(1f) at that exact moment,
    // permanently stranding progressBarPending at true before it ever gets to clear itself.
    LaunchedEffect(Unit) {
        snapshotFlow { uiState.importProgress }.filterNotNull().collect { progress ->
            lastProgress = progress
            progressBarPending = true
            // completedFiles/totalFiles (exact Ints) rather than a bytesCopied>=totalBytes float
            // comparison -- for a large file (e.g. 250MB), converting both to Float loses enough
            // precision that the computed fraction can land just under 1f even once the copy is
            // genuinely done, so a `fraction >= 1f` check never fires and strands progressBarPending.
            val isFinal = progress.completedFiles >= progress.totalFiles
            val fraction = if (progress.totalBytes > 0) {
                (progress.bytesCopied.toFloat() / progress.totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            // copyWithProgress throttles updates to ~100ms apart; a slightly longer linear tween
            // per step keeps consecutive steps visually overlapping instead of reading as snaps.
            progressAnim.animateTo(
                if (isFinal) 1f else fraction,
                animationSpec = tween(durationMillis = 150, easing = LinearEasing),
            )
            if (isFinal) progressBarPending = false
        }
    }
    var menuExpanded by remember { mutableStateOf(false) }
    // containerSize/containerDpSize reflects the actual hosting window, unlike
    // Configuration.screenWidthDp/screenHeightDp which can be stale or mismatched in
    // multi-window/embedded scenarios.
    val containerDpSize = LocalWindowInfo.current.containerDpSize
    // On a foldable opened flat on a table, the hinge runs horizontally and the screen is
    // effectively split top/bottom -- same reachability problem as a wide/short window, so both
    // trigger the same adaptive treatment below.
    val activity = LocalActivity.current
    val isTabletopPosture by produceState(initialValue = false, activity) {
        if (activity == null) return@produceState
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
            .map { layoutInfo ->
                layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().any {
                    it.state == FoldingFeature.State.HALF_OPENED && it.orientation == FoldingFeature.Orientation.HORIZONTAL
                }
            }
            .collect { value = it }
    }
    // A width breakpoint (not aspect ratio) -- this needs to stay true for a tablet in portrait
    // too, which is still comfortably wide despite being taller than it is wide. Covers a phone
    // rotated to landscape, a tablet in either orientation, and a foldable unfolded to its large
    // display, all alike.
    val windowSizeClass = remember(containerDpSize) {
        WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(
            widthDp = containerDpSize.width.value,
            heightDp = containerDpSize.height.value,
        )
    }
    val isWideScreen = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) || isTabletopPosture

    Scaffold(
        modifier = modifier,
        contentWindowInsets = reducedBottomInsetContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                },
                // The status bar inset above this can't be shrunk, but the bar's own content
                // height can -- a more compact bar overall, shrunk further in a short
                // landscape window where vertical space is especially scarce.
                expandedHeight = if (isWideScreen) 40.dp else 48.dp,
                colors = transparentTopAppBarColors(),
                modifier = Modifier.padding(top = 4.dp),
                actions = {
                    if (BuildConfig.DEBUG) {
                        FilledIconButton(
                            onClick = {
                                android.widget.Toast.makeText(context, debugBuildToastMessage, android.widget.Toast.LENGTH_LONG).show()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = subtleContainerColor()),
                        ) {
                            Icon(Icons.Filled.BugReport, contentDescription = "Debug build")
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Box {
                        FilledIconButton(
                            onClick = { menuExpanded = true },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = subtleContainerColor()),
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = subtleContainerColor(),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.about_title)) },
                                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                onClick = { menuExpanded = false; onOpenAbout() },
                                contentPadding = PaddingValues(horizontal = 20.dp),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_title)) },
                                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                onClick = { menuExpanded = false; onOpenSettings() },
                                contentPadding = PaddingValues(horizontal = 20.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    progressBarPending ||
                        (uiState.actionButtonLoading &&
                            (uiState.pageType != PageType.IMPORTED || uiState.importProgress != null)) -> {
                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            elevation = CardDefaults.cardElevation(1.dp),
                            colors = CardDefaults.cardColors(containerColor = subtleContainerColor()),
                        ) {
                            Box(modifier = Modifier.padding(20.dp)) {
                                val progress = lastProgress
                                if (progress != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .width(140.dp)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(progressAnim.value)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(MaterialTheme.colorScheme.primary),
                                            )
                                        }
                                        Spacer(Modifier.size(8.dp))
                                        val label = if (progress.totalFiles > 1) {
                                            "${progress.completedFiles}/${progress.totalFiles}"
                                        } else {
                                            "${(progressAnim.value * 100).toInt()}%"
                                        }
                                        Text(label, style = MaterialTheme.typography.bodySmall)
                                    }
                                } else {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                    uiState.pageType == PageType.IMPORTED -> ImportedContent(uiState, viewModel, isWideScreen)
                    uiState.pageType == PageType.UNHANDLED_ERROR -> UnhandledErrorContent(uiState.errorDetail)
                    else -> MessageForPageType(uiState.pageType)
                }
            }
            FabRow(uiState = uiState, viewModel = viewModel, isWideScreen = isWideScreen, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MessageForPageType(pageType: PageType) {
    val (icon, labelRes, msgRes) = when (pageType) {
        PageType.LANDING -> Triple(Icons.AutoMirrored.Filled.InsertDriveFile, R.string.page_landing_label, R.string.page_landing_msg)
        PageType.NO_CONNECTION -> Triple(Icons.Filled.SignalWifiOff, R.string.page_info_noconnection_label, R.string.page_info_noconnection_msg)
        PageType.SNAPSHOT_ERROR -> Triple(Icons.Filled.Error, R.string.page_info_snapshoterror_label, R.string.page_info_snapshoterror_msg)
        PageType.FILE_REMOVED -> Triple(Icons.Filled.Block, R.string.page_info_fileremoved_label, R.string.page_info_fileremoved_msg)
        PageType.FILE_MODIFIED -> Triple(Icons.Filled.Edit, R.string.page_info_filemodified_label, R.string.page_info_filemodified_msg)
        PageType.INSUFFICIENT_STORAGE -> Triple(Icons.Filled.Storage, R.string.page_info_insufficientstorage_label, R.string.page_info_insufficientstorage_msg)
        PageType.PORT_IN_USE -> Triple(Icons.Filled.Error, R.string.page_info_portinuse_label, R.string.page_info_portinuse_msg)
        PageType.IMPORTED, PageType.PERMISSION_DENIED, PageType.UNHANDLED_ERROR ->
            Triple(Icons.Filled.Error, R.string.page_info_unhandlederror_label, R.string.page_info_unhandlederror_msg)
    }
    StatusCard(
        icon = icon,
        label = stringResource(labelRes),
        message = stringResource(msgRes),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnhandledErrorContent(detail: String) {
    val context = LocalContext.current

    BoxWithConstraints {
        // The icon/message/hint/padding around the detail box are fixed height, so a short
        // window (landscape phone) can run out of room before reaching the hint below it --
        // shrink the box's cap with the available height instead of letting the hint get
        // pushed past the bottom of the screen.
        val detailMaxHeight = (maxHeight * 0.3f).coerceIn(56.dp, 160.dp)
        val cardPadding = if (maxHeight < 360.dp) 20.dp else 32.dp

        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(containerColor = subtleContainerColor()),
        ) {
            Column(
                modifier = Modifier.padding(cardPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = stringResource(R.string.page_info_unhandlederror_label),
                    modifier = Modifier.size(72.dp),
                )
                Spacer(Modifier.size(20.dp))
                Text(
                    text = stringResource(R.string.page_info_unhandlederror_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = detailMaxHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, detail)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, null))
                            },
                        )
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.page_info_unhandlederror_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Height reserved at the bottom of [ImportedContent]'s portrait layout so its scrollable content
 *  never ends up underneath the import FAB (and shutdown FAB), which are overlaid on top of this
 *  screen separately. */
private val FabClearance = 104.dp

/** Width reserved at both edges of the wide layout, where both FABs are stacked vertically along
 *  the trailing edge instead of along the bottom. Applied symmetrically (not just the trailing
 *  edge) so the content row stays centered on the true screen width rather than skewing toward
 *  the leading edge. */
private val WideFabClearance = 96.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ImportedContent(uiState: AppUiState, viewModel: QRServViewModel, isWideScreen: Boolean) {
    val context = LocalContext.current
    val hostFormatted = remember(uiState.selectedIp) {
        if (uiState.selectedIp.contains(':')) "[${uiState.selectedIp}]" else uiState.selectedIp
    }
    val url = remember(hostFormatted, uiState.port, uiState.fiuEnabled, uiState.fileInfo) {
        val filePathSegment = if (uiState.fiuEnabled) {
            java.net.URLEncoder.encode(uiState.fileInfo.name, "UTF-8").replace("+", "%20")
        } else {
            ""
        }
        "http://$hostFormatted:${uiState.port}/$filePathSegment"
    }
    val sizeHuman = remember(uiState.fileInfo) { FileSizeFormatter.humanReadable(uiState.fileInfo.length) }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipboardToastMessage = stringResource(R.string.page_imported_share_clipboard)
    val scope = rememberCoroutineScope()
    val urlTooltipState = rememberTooltipState(isPersistent = true)
    val scrollState = rememberScrollState()

    // Wide/short windows -- landscape phones, unfolded foldables, tablets -- put the QR code
    // and details side by side instead of stacked, since height is the scarce dimension there
    // and stacking them can push the details card off-screen. isWideScreen comes from the caller
    // so this agrees with the FABs/app bar's own layout decision instead of measuring separately.
    if (isWideScreen) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(vertical = 8.dp)
                .padding(horizontal = WideFabClearance),
        ) {
            QrCodeCard(url, context, clipboard, clipboardToastMessage, scope, urlTooltipState)
            ImportInfoCard(uiState, viewModel, url, sizeHuman, context)
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp)
                .padding(bottom = FabClearance),
        ) {
            QrCodeCard(url, context, clipboard, clipboardToastMessage, scope, urlTooltipState)
            Spacer(Modifier.size(36.dp))
            ImportInfoCard(uiState, viewModel, url, sizeHuman, context)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun QrCodeCard(
    url: String,
    context: Context,
    clipboard: ClipboardManager,
    clipboardToastMessage: String,
    scope: CoroutineScope,
    urlTooltipState: TooltipState,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(url) } },
        state = urlTooltipState,
        enableUserInput = false,
    ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.cardElevation(1.dp),
            // QrCodeImage paints its own opaque white background at these exact bounds, fully
            // covering the Card's container color -- transparent skips that hidden overdraw.
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            // The click-ripple clip lives here, on the content, rather than on Card's own modifier
            // above -- clipping Card itself would also clip off its drop shadow, which needs to
            // draw slightly outside the shape's bounds to be visible at all.
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .combinedClickable(
                        onClick = { scope.launch { urlTooltipState.show() } },
                        onLongClick = {
                            clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
                            android.widget.Toast.makeText(context, clipboardToastMessage, android.widget.Toast.LENGTH_SHORT).show()
                        },
                    ),
            ) {
                QrCodeImage(data = url, size = 176.dp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportInfoCard(
    uiState: AppUiState,
    viewModel: QRServViewModel,
    url: String,
    sizeHuman: String,
    context: Context,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = subtleContainerColor()),
        modifier = Modifier.widthIn(max = 300.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp)) {
            FileNameRow(uiState)
            Spacer(Modifier.size(12.dp))
            // BoxWithConstraints (used inside InterfaceDropdown) is a SubcomposeLayout under the hood,
            // which doesn't support the intrinsic-measurement pass IntrinsicSize.Min relies on --
            // that combination stack-overflows, so mirror the dropdown's measured height via state instead.
            var dropdownHeightPx by remember { mutableIntStateOf(0) }
            val density = LocalDensity.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                InterfaceDropdown(
                    uiState,
                    viewModel,
                    modifier = Modifier.weight(1f).onSizeChanged { dropdownHeightPx = it.height },
                )
                Spacer(Modifier.size(12.dp))
                val shareButtonSize = if (dropdownHeightPx > 0) with(density) { dropdownHeightPx.toDp() } else 56.dp
                Card(
                    shape = RoundedCornerShape(10.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.size(shareButtonSize),
                ) {
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, null))
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.page_imported_share_sheet_label),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            InfoRow(stringResource(R.string.page_imported_size), sizeHuman)
            Spacer(Modifier.size(4.dp))
            InfoRow(stringResource(R.string.page_imported_port), uiState.port.toString())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileNameRow(uiState: AppUiState) {
    val name = uiState.fileInfo.name
    val archived = uiState.fileInfo.archived
    val largestArchivedFiles = remember(archived) { archived.sortedByDescending { it.size }.take(5) }
    val remainingArchivedCount = archived.size - largestArchivedFiles.size
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Column {
                    Text(name)
                    largestArchivedFiles.forEach { entry ->
                        Text("${entry.name} (${FileSizeFormatter.humanReadable(entry.size)})")
                    }
                    if (remainingArchivedCount > 0) {
                        Text(stringResource(R.string.page_imported_archive_morefiles, remainingArchivedCount))
                    }
                }
            }
        },
        state = rememberTooltipState(isPersistent = true),
    ) {
        Card(shape = RoundedCornerShape(10.dp), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(iconForFileName(name), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(14.dp))
                MiddleEllipsisText(text = name, fontSize = 14.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

// The IP value is a fixed label, not editable text -- hide the selection toolbar/highlight so
// long-pressing it doesn't offer to select/copy like real text input would.
private val NoSelectionTextToolbar = object : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden
    override fun hide() {}
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {}
}

// Approximate horizontal space the filled TextField's own chrome (padding + trailing icon) eats
// into its width -- no public API gives the exact figure, so this errs generous (truncating a
// character early) rather than risk overflow into/behind the dropdown icon.
private val InterfaceDropdownChromeWidth = 72.dp

private fun addressGroupLabel(group: AddressGroup): Int = when (group) {
    AddressGroup.ROUTABLE -> R.string.page_imported_iface_group_network
    AddressGroup.HOSTED -> R.string.page_imported_iface_group_hotspot
    AddressGroup.LINK_LOCAL -> R.string.page_imported_iface_group_linklocal
    AddressGroup.LOOPBACK -> R.string.page_imported_iface_group_loopback
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InterfaceDropdown(uiState: AppUiState, viewModel: QRServViewModel, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()
    val textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 13.sp)
    val density = LocalDensity.current

    BoxWithConstraints(modifier) {
        val availableTextWidthPx = (constraints.maxWidth - with(density) { InterfaceDropdownChromeWidth.roundToPx() })
            .coerceAtLeast(0)
        // Same middle-ellipsis treatment as the file name, so a long IPv6 address truncates the
        // same way instead of being hard-clipped by the TextField with no visual indicator.
        val displayedIp = remember(uiState.selectedIp, availableTextWidthPx, textStyle) {
            middleEllipsis(uiState.selectedIp, availableTextWidthPx) { candidate ->
                textMeasurer.measure(AnnotatedString(candidate), style = textStyle, maxLines = 1, softWrap = false).size.width
            }
        }

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
            // TextField itself has no elevation/shadow support, unlike Card -- wrap it in one so it
            // reads at the same elevation as the file name row and share button next to it, with the
            // TextField's own container made transparent so the Card's background/shadow show through.
            Card(
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CompositionLocalProvider(
                    LocalTextToolbar provides NoSelectionTextToolbar,
                    LocalTextSelectionColors provides TextSelectionColors(
                        handleColor = Color.Transparent,
                        backgroundColor = Color.Transparent,
                    ),
                ) {
                    TextField(
                        value = displayedIp,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        textStyle = textStyle,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        shape = RoundedCornerShape(10.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                }
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = subtleContainerColor(),
            ) {
                AddressGroup.entries.forEach { group ->
                    val addresses = uiState.interfaces.filter { it.group == group }
                    // A heading with nothing under it says less than no heading at all.
                    if (addresses.isEmpty()) return@forEach
                    Text(
                        text = stringResource(addressGroupLabel(group)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp),
                    )
                    addresses.forEach { entry ->
                        DropdownMenuItem(
                            text = { Text(entry.address, overflow = TextOverflow.Ellipsis, maxLines = 2) },
                            onClick = {
                                viewModel.onIpSelected(entry.address)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FabRow(uiState: AppUiState, viewModel: QRServViewModel, isWideScreen: Boolean, modifier: Modifier = Modifier) {
    val importFab = @Composable {
        FloatingActionButton(onClick = { viewModel.onImportClicked() }) {
            if (uiState.actionButtonLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = stringResource(R.string.fab_selectfile_label))
            }
        }
    }
    val shutdownFab = @Composable {
        FloatingActionButton(
            onClick = { viewModel.onShutdownClicked() },
            containerColor = BrandError,
            contentColor = Color.White,
        ) {
            Icon(Icons.Filled.PowerSettingsNew, contentDescription = stringResource(R.string.fab_shutdownserver_label))
        }
    }

    Box(modifier = modifier) {
        if (isWideScreen) {
            // A wide window (landscape phones, tablets in either orientation, foldables unfolded
            // to their large display) puts the bottom edge out of comfortable thumb reach -- stack
            // both FABs vertically along the trailing edge instead, anchored toward the bottom
            // corner rather than centered, matching landscape-phone FAB placement. Alignment.BottomEnd
            // (not a hardcoded corner) keeps this correct in RTL layouts too.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 48.dp),
            ) {
                if (uiState.serverRunning) {
                    shutdownFab()
                }
                importFab()
            }
        } else {
            if (uiState.serverRunning) {
                Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 32.dp)) {
                    shutdownFab()
                }
            }
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 16.dp).padding(bottom = 16.dp)) {
                importFab()
            }
        }
    }
}
