package com.nbtaad.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nbtaad.editor.experimental.ExperimentalController
import com.nbtaad.editor.experimental.MdPicture
import com.nbtaad.editor.experimental.Pictures
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =========================================================================
// Text files (logs, notes) in a window
// =========================================================================

/**
 * A log, note or text in a small window in the middle of the screen (at most 70% of its width and height): small
 * monospace text that scrolls, and COPY at the top right to paste it somewhere else (into an AI chat, say).
 * The file is read into memory only; nothing is saved on the phone.
 */
@Composable
internal fun TextFileDialog(doc: ExperimentalController.TextDoc, onClose: () -> Unit) {
    val cfg = LocalConfiguration.current
    val maxW = (cfg.screenWidthDp * 0.7f).dp
    val maxH = (cfg.screenHeightDp * 0.7f).dp
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf("") }
    // shown in pieces of 40 lines, so a long log scrolls smoothly; a very long single line is cut for the display only
    val chunks = remember(doc) {
        doc.text.lines().map { if (it.length > 4000) it.take(4000) + "…" else it }.chunked(40).map { it.joinToString("\n") }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.width(maxW).heightIn(max = maxH),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 6.dp,
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        doc.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            copied = try {
                                clip.setText(AnnotatedString(doc.text))
                                "COPIED"
                            } catch (e: Exception) {
                                "TOO BIG"
                            }
                            scope.launch {
                                delay(1800)
                                copied = ""
                            }
                        },
                    ) { Text(copied.ifEmpty { "COPY" }, fontSize = 12.sp) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                LazyColumn(
                    Modifier.weight(1f, fill = false).padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    if (chunks.isEmpty() || doc.text.isBlank()) {
                        item { Text("(the file is empty)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(chunks) { c ->
                        Text(c, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp)
                    }
                    if (doc.cut) {
                        item {
                            Text(
                                "Only the first 800 KB are shown and copied. Open the file in the browser for the rest.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// Pictures: a grid under the post, and a gallery
// =========================================================================

/** A picture from a page, loaded into memory at the size that is needed. */
@Composable
internal fun PictureImage(url: String, maxSide: Int, modifier: Modifier, contentScale: ContentScale) {
    val ctx = LocalContext.current.applicationContext
    var bmp by remember(url, maxSide) { mutableStateOf<ImageBitmap?>(Pictures.cached(url, maxSide)?.asImageBitmap()) }
    var failed by remember(url, maxSide) { mutableStateOf(false) }
    LaunchedEffect(url, maxSide) {
        if (bmp == null) {
            val b = Pictures.load(ctx, url, maxSide)
            if (b != null) bmp = b.asImageBitmap() else failed = true
        }
    }
    val img = bmp
    when {
        img != null -> Image(bitmap = img, contentDescription = null, modifier = modifier, contentScale = contentScale)
        failed -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Cannot show this picture here",
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(6.dp),
            )
        }
        else -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
}

/** The pictures of one post as small squares, three in a row; tapping one opens the gallery of this post. */
@Composable
internal fun PictureGrid(pictures: List<MdPicture>, modifier: Modifier = Modifier, onOpen: (Int) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val border = MaterialTheme.colorScheme.outline
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (pictures.size == 1) "1 picture" else "${pictures.size} pictures",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        pictures.chunked(3).forEachIndexed { row, group ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((col, pic) in group.withIndex()) {
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(shape).border(1.dp, border, shape)
                            .clickable { onOpen(row * 3 + col) },
                    ) {
                        PictureImage(pic.url, 360, Modifier.fillMaxSize(), ContentScale.Crop)
                    }
                }
                for (k in group.size until 3) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * The pictures of ONE post, full screen like a phone's gallery: swipe left and right for the previous and next one of
 * this post, pinch or double-tap to zoom (drag to move around while zoomed). The top bar says which post they belong to.
 */
@Composable
internal fun GalleryDialog(g: ExperimentalController.Gallery, onClose: () -> Unit) {
    val uri = LocalUriHandler.current
    val pager = rememberPagerState(initialPage = g.index) { g.pictures.size }
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) { zoomed = false }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black, contentColor = Color.White) {
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), userScrollEnabled = !zoomed) { page ->
                    ZoomablePicture(g.pictures[page].url, active = pager.currentPage == page) { z ->
                        if (pager.currentPage == page) zoomed = z
                    }
                }
                val current = g.pictures[pager.currentPage.coerceIn(0, g.pictures.size - 1)]
                Column(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))
                        .safeDrawingPadding().padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${pager.currentPage + 1} / ${g.pictures.size}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { openLink(uri, current.url) }) {
                            Text("OPEN IN BROWSER", fontSize = 11.sp, color = Color(0xFFE9A33A))
                        }
                        TextButton(onClick = onClose) { Text("CLOSE", fontSize = 12.sp, color = Color(0xFFE9A33A)) }
                    }
                    Text(g.title, fontSize = 12.sp, color = Color(0xFFD0D0D0))
                    if (current.alt.isNotBlank()) Text(current.alt, fontSize = 11.sp, color = Color(0xFF9E9E9E), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** One picture of the gallery: pinch to zoom, double-tap to zoom in or back, drag to move while zoomed. */
@Composable
private fun ZoomablePicture(url: String, active: Boolean, onZoomed: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }
    LaunchedEffect(scale) { onZoomed(scale > 1.01f) }
    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                // two fingers always zoom; one finger moves the picture only while it is zoomed in, so at normal size
                // a swipe goes on to the pager (previous / next picture)
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f || pan != Offset.Zero) {
                                val newScale = (scale * zoom).coerceIn(1f, 6f)
                                val c = event.calculateCentroid(useCurrent = false) - Offset(box.width / 2f, box.height / 2f)
                                val ratio = newScale / scale
                                val maxX = box.width * (newScale - 1f) / 2f
                                val maxY = box.height * (newScale - 1f) / 2f
                                val raw = c + pan - (c - offset) * ratio
                                offset = if (newScale <= 1.01f) Offset.Zero else Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
                                scale = newScale
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        PictureImage(
            url, 3072,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
            ContentScale.Fit,
        )
    }
}
