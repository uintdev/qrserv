package dev.uint.qrserv.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PortableWifiOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiLock
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import dev.uint.qrserv.net.HotspotFailure
import dev.uint.qrserv.ui.components.HotspotButton
import dev.uint.qrserv.ui.components.HotspotOption
import dev.uint.qrserv.ui.components.DetailsCardMaxWidth
import dev.uint.qrserv.ui.components.DetailsFieldHeight
import dev.uint.qrserv.ui.components.MiddleEllipsisText
import dev.uint.qrserv.ui.components.hotspotNote
import dev.uint.qrserv.ui.components.QrCodeImage
import dev.uint.qrserv.ui.components.QrDetailsLayout
import dev.uint.qrserv.ui.components.StatusCard
import dev.uint.qrserv.ui.components.rememberIsWideScreen
import dev.uint.qrserv.ui.theme.BrandError
import dev.uint.qrserv.ui.theme.reducedBottomInsetContentWindowInsets
import dev.uint.qrserv.ui.theme.subtleContainerColor
import dev.uint.qrserv.ui.theme.transparentTopAppBarColors
import dev.uint.qrserv.util.FileSizeFormatter
import dev.uint.qrserv.util.iconForFileName
import dev.uint.qrserv.viewmodel.QRServViewModel
import dev.uint.qrserv.viewmodel.hotspotFailureMessageRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: QRServViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenHotspot: () -> Unit,
    onHotspotScreenDue: () -> Unit,
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
    var operationShowedProgress by remember { mutableStateOf(false) }
    val finishedProgress = remember { uiState.importProgress.takeIf { !uiState.actionButtonLoading } }
    LaunchedEffect(uiState.actionButtonLoading) {
        if (uiState.actionButtonLoading) operationShowedProgress = false
    }
    // Keyed on Unit (not uiState.importProgress) so this coroutine survives for the composable's
    // whole lifetime -- the ViewModel resets importProgress to null immediately once it's done,
    // and keying on that value would cancel an in-flight animateTo(1f) at that exact moment,
    // permanently stranding progressBarPending at true before it ever gets to clear itself.
    LaunchedEffect(Unit) {
        snapshotFlow { uiState.importProgress }.filterNotNull().filter { it !== finishedProgress }.collect { progress ->
            lastProgress = progress
            progressBarPending = true
            operationShowedProgress = true
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
    // Waits for the progress bar too: its fill to 100% outlives the loading state.
    LaunchedEffect(uiState.hotspotScreenPending, uiState.actionButtonLoading, progressBarPending) {
        if (uiState.hotspotScreenPending && !uiState.actionButtonLoading && !progressBarPending) {
            onHotspotScreenDue()
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }
    val isWideScreen = rememberIsWideScreen()

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
                    // Held at 100% so the hotspot screen replaces the card without the imported screen flashing up.
                    progressBarPending || (uiState.hotspotScreenPending && operationShowedProgress) ||
                        // Starting the hotspot shows on its own button instead, as the import FAB does.
                        (uiState.actionButtonLoading && !uiState.hotspotStarting &&
                            (uiState.pageType != PageType.IMPORTED || uiState.importProgress != null)) -> {
                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            elevation = CardDefaults.cardElevation(1.dp),
                            colors = CardDefaults.cardColors(containerColor = subtleContainerColor()),
                        ) {
                            Box(modifier = Modifier.padding(20.dp)) {
                                val progress = lastProgress.takeIf { operationShowedProgress }
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
                    uiState.pageType == PageType.IMPORTED -> ImportedContent(uiState, viewModel, isWideScreen, onOpenHotspot)
                    uiState.pageType == PageType.UNHANDLED_ERROR -> UnhandledErrorContent(uiState, viewModel, isWideScreen)
                    else -> MessageForPageType(uiState, viewModel)
                }
            }
            FabRow(uiState = uiState, viewModel = viewModel, isWideScreen = isWideScreen, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MessageForPageType(uiState: AppUiState, viewModel: QRServViewModel) {
    if (uiState.pageType == PageType.HOTSPOT_FAILED) {
        HotspotFailedContent(uiState, viewModel)
        return
    }
    val (icon, labelRes, msgRes) = when (uiState.pageType) {
        PageType.LANDING -> Triple(Icons.AutoMirrored.Filled.InsertDriveFile, R.string.page_landing_label, R.string.page_landing_msg)
        PageType.NO_CONNECTION -> Triple(Icons.Filled.SignalWifiOff, R.string.page_info_noconnection_label, R.string.page_info_noconnection_msg)
        PageType.INTERFACE_LOOKUP_ERROR -> Triple(Icons.Filled.Error, R.string.page_info_interfacelookuperror_label, R.string.page_info_interfacelookuperror_msg)
        PageType.FILE_REMOVED -> Triple(Icons.Filled.Block, R.string.page_info_fileremoved_label, R.string.page_info_fileremoved_msg)
        PageType.FILE_MODIFIED -> Triple(Icons.Filled.Edit, R.string.page_info_filemodified_label, R.string.page_info_filemodified_msg)
        PageType.INSUFFICIENT_STORAGE -> Triple(Icons.Filled.Storage, R.string.page_info_insufficientstorage_label, R.string.page_info_insufficientstorage_msg)
        PageType.PORT_IN_USE -> Triple(Icons.Filled.Error, R.string.page_info_portinuse_label, R.string.page_info_portinuse_msg)
        PageType.IMPORTED, PageType.UNHANDLED_ERROR, PageType.HOTSPOT_FAILED ->
            Triple(Icons.Filled.Error, R.string.page_info_unhandlederror_label, R.string.page_info_unhandlederror_msg)
    }
    StatusCard(
        icon = icon,
        label = stringResource(labelRes),
        message = stringResource(msgRes),
        footer = { HotspotOption(uiState.hotspotAvailability, uiState.hotspotStarting, onClick = viewModel::onHotspotClicked) },
    )
}

@Composable
private fun HotspotFailedContent(uiState: AppUiState, viewModel: QRServViewModel) {
    val reason = uiState.hotspotFailure ?: HotspotFailure.GENERIC
    StatusCard(
        icon = Icons.Filled.PortableWifiOff,
        label = stringResource(R.string.hotspot_failed_label),
        message = stringResource(hotspotFailureMessageRes(reason)),
        footer = if (reason == HotspotFailure.TETHERING_DISALLOWED) {
            null
        } else {
            {
                HotspotButton(
                    icon = Icons.Filled.Refresh,
                    text = stringResource(R.string.hotspot_try_again),
                    enabled = uiState.hotspotAvailability.unavailable == null,
                    loading = uiState.hotspotStarting,
                    onClick = viewModel::onHotspotClicked,
                )
            }
        },
    )
}

@Composable
private fun UnhandledErrorContent(uiState: AppUiState, viewModel: QRServViewModel, isWideScreen: Boolean) {
    BoxWithConstraints {
        val cardPadding = if (maxHeight < 360.dp) 20.dp else 32.dp

        if (isWideScreen) {
            // Measured rather than IntrinsicSize, which stack-overflows inside BoxWithConstraints.
            var leftHeightPx by remember { mutableIntStateOf(0) }
            val density = LocalDensity.current
            Card(
                modifier = Modifier.widthIn(max = 560.dp),
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.cardColors(containerColor = subtleContainerColor()),
            ) {
                Row(modifier = Modifier.padding(cardPadding), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).onSizeChanged { leftHeightPx = it.height },
                    ) {
                        UnhandledErrorHeader()
                        Spacer(Modifier.size(16.dp))
                        HotspotOption(uiState.hotspotAvailability, uiState.hotspotStarting, onClick = viewModel::onHotspotClicked)
                    }
                    Spacer(Modifier.width(24.dp))
                    Column(modifier = Modifier.weight(1.2f)) {
                        val boxHeight = with(density) { leftHeightPx.toDp() } - UnhandledErrorHintAllowance
                        UnhandledErrorDetail(uiState.errorDetail, Modifier.height(boxHeight.coerceAtLeast(56.dp)))
                        Spacer(Modifier.size(8.dp))
                        UnhandledErrorHint()
                    }
                }
            }
        } else {
            // The icon/message/hint/padding around the detail box are fixed height, so a short
            // window (landscape phone) can run out of room before reaching the hint below it --
            // shrink the box's cap with the available height instead of letting the hint get
            // pushed past the bottom of the screen.
            val detailMaxHeight = (maxHeight * 0.3f).coerceIn(56.dp, 160.dp)
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
                    UnhandledErrorHeader()
                    Spacer(Modifier.size(16.dp))
                    UnhandledErrorDetail(uiState.errorDetail, Modifier.heightIn(max = detailMaxHeight))
                    Spacer(Modifier.size(8.dp))
                    UnhandledErrorHint()
                    Spacer(Modifier.size(16.dp))
                    HotspotOption(uiState.hotspotAvailability, uiState.hotspotStarting, onClick = viewModel::onHotspotClicked)
                }
            }
        }
    }
}

private val UnhandledErrorHintAllowance = 28.dp

@Composable
private fun UnhandledErrorHeader() {
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnhandledErrorDetail(detail: String, modifier: Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
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
}

@Composable
private fun UnhandledErrorHint() {
    Text(
        text = stringResource(R.string.page_info_unhandlederror_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
private fun ImportedContent(
    uiState: AppUiState,
    viewModel: QRServViewModel,
    isWideScreen: Boolean,
    onOpenHotspot: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
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
    val locale = LocalConfiguration.current.locales[0]
    val sizeHuman = remember(uiState.fileInfo, locale) { FileSizeFormatter.humanReadable(uiState.fileInfo.length, locale) }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipboardToastMessage = stringResource(R.string.page_imported_share_clipboard)
    val scope = rememberCoroutineScope()
    val urlTooltipState = rememberTooltipState(isPersistent = true)

    // Wide/short windows -- landscape phones, unfolded foldables, tablets -- put the QR code
    // and details side by side instead of stacked, since height is the scarce dimension there
    // and stacking them can push the details card off-screen. isWideScreen comes from the caller
    // so this agrees with the FABs/app bar's own layout decision instead of measuring separately.
    QrDetailsLayout(
        isWideScreen = isWideScreen,
        wideSideClearance = WideFabClearance,
        bottomClearance = FabClearance,
        gap = 36.dp,
        qr = { QrCodeCard(url, context, clipboard, clipboardToastMessage, scope, urlTooltipState) },
        details = {
            ImportInfoCard(
                uiState,
                viewModel,
                url,
                sizeHuman,
                context,
                onOpenHotspot = onOpenHotspot,
            )
        },
    )
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
    onOpenHotspot: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = subtleContainerColor()),
        modifier = Modifier.widthIn(max = DetailsCardMaxWidth),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            FileNameRow(uiState)
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val hotspot = uiState.hotspot
                if (hotspot != null) {
                    HotspotNetworkButton(ssid = hotspot.ssid, onClick = onOpenHotspot, modifier = Modifier.weight(1f))
                } else {
                    InterfaceDropdown(uiState, viewModel, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.size(12.dp))
                Card(
                    shape = RoundedCornerShape(10.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.size(DetailsFieldHeight),
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
            if (uiState.vpnLockdown) {
                Spacer(Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.page_imported_vpn_lockdown),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileNameRow(uiState: AppUiState) {
    val name = uiState.fileInfo.name
    val archived = uiState.fileInfo.archived
    val locale = LocalConfiguration.current.locales[0]
    val largestArchivedFiles = remember(archived) { archived.sortedByDescending { it.size }.take(5) }
    val remainingArchivedCount = archived.size - largestArchivedFiles.size
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Column {
                    Text(name)
                    largestArchivedFiles.forEach { entry ->
                        Text("${entry.name} (${FileSizeFormatter.humanReadable(entry.size, locale)})")
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
                modifier = Modifier.heightIn(min = DetailsFieldHeight).padding(horizontal = 14.dp),
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
private fun HotspotNetworkButton(ssid: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.hotspot_open_details)
    Card(
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DetailsFieldHeight)
                .clickable(onClickLabel = description, onClick = onClick)
                .padding(start = 14.dp, end = 8.dp),
        ) {
            Icon(Icons.Filled.WifiLock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
            MiddleEllipsisText(text = ssid, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
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

/** Menu margin and padding around the items, with slack; too small scrolls the switch entry away. */
private val DropdownMenuChrome = 76.dp

private val PinnedListMinHeight = 144.dp

private fun addressGroupLabel(group: AddressGroup): Int = when (group) {
    AddressGroup.ROUTABLE -> R.string.page_imported_iface_group_network
    AddressGroup.HOSTED -> R.string.page_imported_iface_group_tethering
    AddressGroup.LINK_LOCAL -> R.string.page_imported_iface_group_linklocal
    AddressGroup.LOOPBACK -> R.string.page_imported_iface_group_loopback
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InterfaceDropdown(uiState: AppUiState, viewModel: QRServViewModel, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }
    var switchEntryHeightPx by remember { mutableIntStateOf(0) }
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val barsTopPx = WindowInsets.systemBars.getTop(density)
    val barsBottomPx = WindowInsets.systemBars.getBottom(density)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.onGloballyPositioned { anchorBounds = it.boundsInWindow() },
    ) {
        Card(
            shape = RoundedCornerShape(10.dp),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DetailsFieldHeight)
                    .padding(start = 14.dp, end = 8.dp),
            ) {
                MiddleEllipsisText(text = uiState.selectedIp, fontSize = 13.sp, modifier = Modifier.weight(1f))
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = subtleContainerColor(),
        ) {
            // Pinned only while the addresses keep about three rows; otherwise it's the last item in one scroll.
            val listMaxHeight = anchorBounds?.let { bounds ->
                val space = maxOf(bounds.top - barsTopPx, windowHeightPx - barsBottomPx - bounds.bottom)
                with(density) { (space - switchEntryHeightPx).toDp() } - DropdownMenuChrome
            }
            val pinSwitchEntry = listMaxHeight != null && listMaxHeight >= PinnedListMinHeight
            Column(
                modifier = if (pinSwitchEntry && listMaxHeight != null) {
                    Modifier.heightIn(max = listMaxHeight).verticalScroll(rememberScrollState())
                } else {
                    Modifier
                },
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
            val note = hotspotNote(uiState.hotspotAvailability)
            Column(modifier = Modifier.onSizeChanged { switchEntryHeightPx = it.height }) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(R.string.hotspot_switch))
                            if (note != null) {
                                Text(
                                    note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    leadingIcon = { Icon(Icons.Filled.WifiLock, contentDescription = null) },
                    enabled = uiState.hotspotAvailability.unavailable == null,
                    onClick = {
                        expanded = false
                        viewModel.onSwitchToHotspotClicked()
                    },
                )
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
