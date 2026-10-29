package au.com.intheevent.unscribe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import au.com.intheevent.unscribe.data.BookEntity
import au.com.intheevent.unscribe.data.Library
import au.com.intheevent.unscribe.epub.EpubParser
import au.com.intheevent.unscribe.epub.TocEntry
import au.com.intheevent.unscribe.playback.Narrator
import au.com.intheevent.unscribe.playback.Position
import au.com.intheevent.unscribe.text.Sentence
import au.com.intheevent.unscribe.text.SentenceSplitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where Play will start: a paragraph and a sentence within it. */
data class StartPoint(val paragraph: Int, val sentence: Int)

private class OpenBook(val entity: BookEntity, val parser: EpubParser)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(library: Library, narrator: Narrator, bookId: Long, onBack: () -> Unit) {
    val open by produceState<OpenBook?>(null, bookId) {
        val entity = library.get(bookId) ?: return@produceState
        library.markOpened(bookId)
        val parser = withContext(Dispatchers.IO) { EpubParser(File(entity.filePath)) }
        value = OpenBook(entity, parser)
        awaitDispose { parser.close() }
    }
    val book = open ?: return Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
    val chapters = book.parser.book.chapters
    val scope = rememberCoroutineScope()
    val controller = rememberMediaController()
    val narration by narrator.state.collectAsState()
    val narrating = narration.book?.id == bookId
    val playingHere = narrating && narration.playing

    var chapter by remember { mutableIntStateOf(book.entity.chapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0))) }
    // Set when jumping somewhere; the list starts there and the marker shows it.
    var jumpTo by remember { mutableStateOf<StartPoint?>(StartPoint(book.entity.paragraphIndex, book.entity.sentenceIndex)) }
    var marker by remember { mutableStateOf(jumpTo) }
    var showContents by remember { mutableStateOf(false) }

    val paragraphs by produceState<List<String>?>(null, chapter) {
        value = null
        value = withContext(Dispatchers.IO) { if (chapters.isEmpty()) emptyList() else book.parser.paragraphs(chapter) }
    }
    // Not composed until the paragraphs load, so the initial index applies to the real list.
    val listState = remember(chapter) { LazyListState(firstVisibleItemIndex = jumpTo?.paragraph ?: 0) }

    fun goToChapter(index: Int) {
        chapter = index
        jumpTo = null
        marker = null
    }

    // Follow the narrator: the marker sits on the sentence being read (or where it
    // paused), and the reader turns chapters with it.
    LaunchedEffect(narration.position, narrating) {
        val pos = narration.position ?: return@LaunchedEffect
        if (!narrating) return@LaunchedEffect
        if (pos.chapter != chapter) {
            if (!narration.playing) return@LaunchedEffect
            chapter = pos.chapter
            jumpTo = StartPoint(pos.paragraph, pos.sentence)
        }
        marker = StartPoint(pos.paragraph, pos.sentence)
    }

    fun playPause() {
        if (playingHere) {
            controller?.pause() ?: narrator.pause()
            return
        }
        val count = paragraphs?.size ?: return
        val start = startPoint(listState, marker, count)
        scope.launch {
            narrator.open(bookId, Position(chapter, start.paragraph, start.sentence))
            controller?.play() ?: narrator.play()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(book.entity.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            chapterTitle(book.parser, chapter),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Library") }
                },
                actions = {
                    IconButton(onClick = { showContents = true }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Contents")
                    }
                },
            )
        },
        bottomBar = {
            PlayerBar(
                playing = playingHere,
                buffering = playingHere && narration.buffering,
                canSkip = narrating,
                onPlayPause = ::playPause,
                onBack = { narrator.skip(-1) },
                onForward = { narrator.skip(1) },
            )
        },
    ) { padding ->
        val list = paragraphs
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        LaunchedEffect(listState) {
            snapshotFlow { startPoint(listState, marker, list.size) }
                .distinctUntilChanged()
                .collectLatest { start ->
                    delay(400)
                    // While reading, the narrator saves the sentence actually heard.
                    if (narrator.state.value.let { it.playing && it.book?.id == bookId }) return@collectLatest
                    library.savePosition(book.entity.id, chapter, start.paragraph, start.sentence)
                }
        }

        // Turn the page when reading moves into a paragraph that's off screen. Only on
        // paragraph changes, so you can scroll around while listening.
        val followParagraph = narration.position?.takeIf { playingHere && it.chapter == chapter }?.paragraph
        LaunchedEffect(followParagraph) {
            followParagraph ?: return@LaunchedEffect
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == followParagraph }) {
                listState.animateScrollToItem(followParagraph)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 4.dp, end = 24.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(list) { index, text ->
                Paragraph(
                    text = text,
                    markedSentence = marker?.takeIf { it.paragraph == index }?.sentence,
                    onTapSentence = { sentence ->
                        val tapped = StartPoint(index, sentence)
                        if (playingHere) {
                            // Tapping while listening jumps the reading there.
                            marker = tapped
                            scope.launch { narrator.open(bookId, Position(chapter, index, sentence)) }
                        } else {
                            marker = if (marker == tapped) null else tapped
                        }
                    },
                )
            }
            item {
                ChapterFooter(
                    hasPrevious = chapter > 0,
                    hasNext = chapter < chapters.size - 1,
                    onPrevious = { goToChapter(chapter - 1) },
                    onNext = { goToChapter(chapter + 1) },
                )
            }
        }
    }

    if (showContents) {
        ModalBottomSheet(onDismissRequest = { showContents = false }) {
            Contents(book.parser, current = chapter) { index ->
                showContents = false
                goToChapter(index)
                if (playingHere) scope.launch { narrator.open(bookId, Position(index, 0, 0)) }
            }
        }
    }
}

/**
 * Play starts at the tapped sentence while it's on screen; otherwise at the top of
 * the page (the first paragraph that's mostly visible).
 */
private fun startPoint(state: LazyListState, marker: StartPoint?, paragraphCount: Int): StartPoint {
    val visible = state.layoutInfo.visibleItemsInfo.filter { it.index < paragraphCount }
    if (marker != null && visible.any { it.index == marker.paragraph }) return marker
    val top = visible.firstOrNull { it.offset >= -it.size / 2 } ?: visible.firstOrNull()
    return StartPoint(top?.index ?: state.firstVisibleItemIndex.coerceAtMost((paragraphCount - 1).coerceAtLeast(0)), 0)
}

@Composable
private fun Paragraph(text: String, markedSentence: Int?, onTapSentence: (Int) -> Unit) {
    val sentences: List<Sentence> = remember(text) { SentenceSplitter.split(text) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current

    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.width(20.dp)) {
            val result = layout
            val sentence = markedSentence?.let { sentences.getOrNull(it) }
            if (result != null && sentence != null) {
                val line = result.getLineForOffset(sentence.start)
                val top = with(density) { result.getLineTop(line).toDp() }
                val height = with(density) { (result.getLineBottom(line) - result.getLineTop(line)).toDp() }
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .offset(y = top + height * 0.15f)
                        .width(4.dp)
                        .height(height * 0.7f)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, fontSize = 19.sp, lineHeight = 30.sp),
            onTextLayout = { layout = it },
            modifier = Modifier.weight(1f).pointerInput(text) {
                detectTapGestures { position ->
                    val offset = layout?.getOffsetForPosition(position) ?: return@detectTapGestures
                    val index = sentences.indexOfLast { it.start <= offset }.coerceAtLeast(0)
                    onTapSentence(index)
                }
            },
        )
    }
}

@Composable
private fun ChapterFooter(hasPrevious: Boolean, hasNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, top = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (hasPrevious) OutlinedButton(onClick = onPrevious) { Text("Previous chapter") } else Box(Modifier)
        if (hasNext) OutlinedButton(onClick = onNext) { Text("Next chapter") }
    }
}

@Composable
private fun Contents(parser: EpubParser, current: Int, onSelect: (Int) -> Unit) {
    val toc = parser.book.toc
    val entries = toc.ifEmpty { parser.book.chapters.indices.map { TocEntry(chapterTitle(parser, it), it, 0) } }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Text("Contents", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
        itemsIndexed(entries) { _, entry ->
            val selected = entry.chapterIndex == current
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(entry.chapterIndex) }
                    .padding(start = 24.dp + 16.dp * entry.depth, end = 24.dp, top = 12.dp, bottom = 12.dp),
            )
        }
    }
}

private fun chapterTitle(parser: EpubParser, index: Int): String =
    parser.book.chapters.getOrNull(index)?.title ?: "Section ${index + 1}"
