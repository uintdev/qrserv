package dev.uint.qrserv.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * Truncates [text] from the middle ("start…end") rather than the end, so both the beginning and
 * end (e.g. a filename's extension) stay visible. Compose has no built-in middle-ellipsis overflow
 * mode, so this measures candidate strings directly via [widthOf] and picks the longest one that
 * fits [maxWidthPx] in a single binary-search pass -- unlike re-measuring via onTextLayout over
 * several recompositions, which renders each intermediate candidate as a real frame (visible as
 * the text flickering/shuffling while it converges).
 */
fun middleEllipsis(text: String, maxWidthPx: Int, widthOf: (String) -> Int): String {
    fun candidate(keep: Int): String {
        if (keep <= 1) return "…"
        val prefixLen = (keep + 1) / 2
        return text.take(prefixLen) + "…" + text.takeLast(keep - prefixLen)
    }

    if (widthOf(text) <= maxWidthPx) return text

    var low = 0
    var high = text.length
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (widthOf(candidate(mid)) <= maxWidthPx) low = mid else high = mid - 1
    }
    return candidate(low)
}

/**
 * Like [middleEllipsis], but keeps a filename's extension (from the last '.') fully intact rather
 * than treating it as just more trailing characters up for grabs -- otherwise the binary search
 * has no notion of "extension" and can just as easily land mid-suffix (".md5" clipped to ".m").
 * Only the base name before the extension gets shortened.
 */
fun middleEllipsisFilename(name: String, maxWidthPx: Int, widthOf: (String) -> Int): String {
    val dotIndex = name.lastIndexOf('.')
    // No extension to carve out (or a leading dot with nothing before it, e.g. ".gitignore") --
    // fall back to plain middle-ellipsis over the whole name.
    if (dotIndex <= 0) return middleEllipsis(name, maxWidthPx, widthOf)

    val base = name.substring(0, dotIndex)
    val extension = name.substring(dotIndex)
    val availableForBase = maxWidthPx - widthOf(extension)
    // The extension alone doesn't leave any sensible room for the base name -- nothing left to
    // carve intelligently, so fall back rather than return just the bare extension.
    if (availableForBase <= 0) return middleEllipsis(name, maxWidthPx, widthOf)

    return middleEllipsis(base, availableForBase, widthOf) + extension
}

@Composable
fun MiddleEllipsisText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign = TextAlign.Center,
) {
    val textMeasurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.copy(fontSize = fontSize)

    // Measured via onSizeChanged (the real, final laid-out width), not a BoxWithConstraints read of
    // incoming constraints -- inside some parents (e.g. Material3's ListItem) that bound doesn't
    // match this Text's actual width, letting the untruncated tail spill out and get hard-clipped
    // by real layout instead of our own ellipsis. Starts at 0 until the first size callback arrives.
    var maxWidthPx by remember { mutableIntStateOf(0) }
    // Bumped whenever the real Text below reports overflow at the current budget -- TextMeasurer's
    // standalone measurement doesn't always match the real laid-out width (observed with this app's
    // variable font), so this self-corrects from the real layout's verdict instead of trusting the
    // upfront estimate. Reset per source text so a stale correction doesn't linger.
    var overflowCorrectionPx by remember(text) { mutableIntStateOf(0) }

    val displayText = remember(text, maxWidthPx, overflowCorrectionPx, style) {
        val budget = maxWidthPx - overflowCorrectionPx
        if (budget <= 0) {
            text
        } else {
            middleEllipsisFilename(text, budget) { candidate ->
                textMeasurer.measure(AnnotatedString(candidate), style = style, maxLines = 1, softWrap = false).size.width
            }
        }
    }
    Text(
        text = displayText,
        fontSize = fontSize,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        textAlign = textAlign,
        onTextLayout = { result ->
            if (result.didOverflowWidth && maxWidthPx > 0) {
                overflowCorrectionPx += (maxWidthPx * 0.08f).toInt().coerceAtLeast(8)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { maxWidthPx = it.width },
    )
}
