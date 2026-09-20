package dev.uint.qrserv.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.text.BreakIterator

/**
 * Truncates [text] from the middle ("start…end") rather than the end, so both the beginning and
 * end (e.g. a filename's extension) stay visible. Compose has no built-in middle-ellipsis overflow
 * mode, so this measures candidate strings directly via [widthOf] and picks the longest one that
 * fits [maxWidthPx] in a single binary-search pass -- unlike re-measuring via onTextLayout over
 * several recompositions, which renders each intermediate candidate as a real frame (visible as
 * the text flickering/shuffling while it converges).
 */
fun middleEllipsis(text: String, maxWidthPx: Int, widthOf: (String) -> Int): String {
    if (widthOf(text) <= maxWidthPx) return text

    val boundaries = graphemeBoundaries(text)
    val clusterCount = if (boundaries != null) boundaries.size - 1 else text.length

    // [keep] counts grapheme clusters rather than chars, so both cuts land on a boundary. A null
    // [boundaries] means cluster index and char index are the same thing (see below).
    fun offsetOf(cluster: Int): Int = boundaries?.get(cluster) ?: cluster

    fun candidate(keep: Int): String {
        if (keep <= 1) return "…"
        val prefixClusters = (keep + 1) / 2
        val suffixClusters = keep - prefixClusters
        var prefixEnd = offsetOf(prefixClusters)
        var suffixStart = offsetOf(clusterCount - suffixClusters)
        // What BreakIterator considers one cluster depends on the ICU version behind it, and the
        // one shipped with older Android predates the emoji ZWJ rule -- so a family emoji can
        // still be cut between its members. Rather than depend on that, drop a joiner left
        // dangling against the ellipsis and skip any mark stranded at the head of the suffix.
        // Both only ever shrink the result, so a candidate that fit still fits.
        while (prefixEnd > 0 && text[prefixEnd - 1] == '\u200D') prefixEnd--
        while (suffixStart < text.length && text[suffixStart].isStrandedMark()) suffixStart++
        return text.substring(0, prefixEnd) + "…" + text.substring(suffixStart)
    }

    var low = 0
    var high = clusterCount
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (widthOf(candidate(mid)) <= maxWidthPx) low = mid else high = mid - 1
    }
    return candidate(low)
}

/** A joiner, or a mark that renders onto whatever precedes it -- meaningless at the start of a cut. */
private fun Char.isStrandedMark(): Boolean = this == '\u200D' || when (Character.getType(this)) {
    Character.NON_SPACING_MARK.toInt(),
    Character.COMBINING_SPACING_MARK.toInt(),
    Character.ENCLOSING_MARK.toInt(),
    -> true
    else -> false
}

/**
 * Kept per thread rather than built per call: this runs for every visible row of the Direct Access
 * Mode browser, and constructing a BreakIterator clones ICU's compiled rule data each time.
 * BreakIterator isn't thread-safe, hence one instance per thread rather than a shared singleton.
 */
private val graphemeIterator = object : ThreadLocal<BreakIterator>() {
    override fun initialValue(): BreakIterator = BreakIterator.getCharacterInstance()
}

/**
 * Every grapheme-cluster boundary in [text], from 0 through its length -- or null when every char
 * is already a cluster of its own, letting the common case skip the ICU pass and the array both.
 *
 * Truncation has to land on a boundary: take()/takeLast() count UTF-16 chars, and one char is not
 * one character on screen. An emoji is a surrogate pair, so an odd cut leaves half of it behind as
 * a replacement glyph; a flag, a skin-toned emoji or a ZWJ sequence spans several code points; and
 * an accent written as a combining mark -- Russian NFD "й"/"ё", Japanese dakuten, Indic vowel signs
 * -- would be sheared off the letter it belongs to.
 */
private fun graphemeBoundaries(text: String): IntArray? {
    // Nothing below U+0300 combines and no surrogate comes close to it, so each such char stands
    // alone. CR is the one exception, since CR LF counts as a single cluster.
    if (text.all { it.code < 0x0300 && it != '\r' }) return null

    val iterator = graphemeIterator.get() ?: BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val boundaries = mutableListOf(0)
    while (true) {
        val next = iterator.next()
        if (next == BreakIterator.DONE) break
        boundaries.add(next)
    }
    return boundaries.toIntArray()
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

/** Correction rounds allowed before the text is shown regardless, so it can never stay hidden. */
private const val MaxOverflowCorrections = 6

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
    // upfront estimate. Keyed on the width too, since a correction earned at one width means
    // nothing at another (a rotation, or a row recycled into a different column).
    var overflowCorrectionPx by remember(text, maxWidthPx) { mutableIntStateOf(0) }
    var corrections by remember(text, maxWidthPx) { mutableIntStateOf(0) }
    // Nothing is painted until the string has stopped changing. The width isn't known on the first
    // pass and a correction can take another, so painting eagerly meant the name appeared at one
    // length and then visibly re-truncated itself. Read from the graphicsLayer block, i.e. in the
    // draw phase -- so flipping it costs a repaint, not a recomposition, and a value settled during
    // the layout phase is picked up by the very same frame rather than the next one.
    var settled by remember(text, maxWidthPx) { mutableStateOf(false) }

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
            if (maxWidthPx > 0) {
                when {
                    !result.didOverflowWidth -> settled = true
                    // Never leave it invisible because the two measurements can't be reconciled:
                    // a hair of clipping beats a name that never appears.
                    corrections >= MaxOverflowCorrections -> settled = true
                    else -> {
                        // Correct by however much it actually overflowed, rather than by a blind
                        // fraction of the width -- a one-pixel disagreement between TextMeasurer
                        // and real layout used to cost an 8% cut, which is what made the
                        // adjustment big enough to notice. The trailing term guarantees progress
                        // even if the overflow can't be read off the layout.
                        val overflowPx = if (result.lineCount > 0) {
                            (result.getLineRight(0) - maxWidthPx).toInt().coerceAtLeast(0)
                        } else {
                            0
                        }
                        overflowCorrectionPx += overflowPx + 1 + corrections
                        corrections++
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (settled) 1f else 0f }
            .onSizeChanged { maxWidthPx = it.width },
    )
}
