package au.com.intheevent.unscribe.playback

import android.content.Context
import android.os.PowerManager
import au.com.intheevent.unscribe.data.BookEntity
import au.com.intheevent.unscribe.data.Library
import au.com.intheevent.unscribe.epub.EpubParser
import au.com.intheevent.unscribe.text.WordTiming
import au.com.intheevent.unscribe.tts.PcmPlayer
import au.com.intheevent.unscribe.tts.SherpaPiperEngine
import au.com.intheevent.unscribe.tts.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

data class NarratorState(
    val book: BookEntity? = null,
    val chapterTitle: String? = null,
    /** The sentence being heard (or that will be heard next, when paused). */
    val position: Position? = null,
    /** Estimated word being spoken, as a character range in the position's paragraph. Null when not speaking. */
    val word: IntRange? = null,
    /** The user wants audio; may still be [buffering]. */
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val ended: Boolean = false,
)

/**
 * Reads a book aloud. One coroutine synthesises sentences a little ahead of time on a
 * dedicated thread; another streams them to the audio track; a ticker maps the audio
 * playhead back to the sentence actually being heard, which is what gets saved and
 * where playback resumes. Main-thread API.
 */
class Narrator(context: Context, private val library: Library) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // The TTS engine isn't thread-safe; everything that touches it runs here.
    private val ttsDispatcher = Executors.newSingleThreadExecutor { Thread(it, "tts") }.asCoroutineDispatcher()
    private val engine = SherpaPiperEngine(context.applicationContext)
    private val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "unscribe:narrator")
        .apply { setReferenceCounted(false) }

    private val _state = MutableStateFlow(NarratorState())
    val state: StateFlow<NarratorState> = _state.asStateFlow()

    var voice: Voice = Voice.DEFAULT
        set(value) { field = value; restartIfPlaying() }
    var speed: Float = 1.0f
        set(value) { field = value; restartIfPlaying() }

    private var parser: EpubParser? = null
    private var cursor: BookCursor? = null
    private var session: Job? = null
    private var player: PcmPlayer? = null

    /** Loads [bookId] (if it isn't already) and moves to [start]. Keeps playing if it was. */
    suspend fun open(bookId: Long, start: Position) {
        val wasPlaying = state.value.playing
        stopSession()
        if (state.value.book?.id != bookId) {
            val book = library.get(bookId) ?: return
            val newParser = withContext(Dispatchers.IO) { EpubParser(File(book.filePath)) }
            parser?.close()
            parser = newParser
            cursor = BookCursor(newParser)
            _state.value = NarratorState(book = book)
        }
        val position = withContext(Dispatchers.IO) { cursor?.normalize(start) } ?: start
        moveTo(position)
        if (wasPlaying) play()
    }

    fun play() {
        val start = state.value.position ?: return
        if (session?.isActive == true) return
        // Play after the end of the book starts it again from the top.
        val restart = if (state.value.ended) Position(0, 0, 0) else start
        _state.update { it.copy(playing = true, buffering = true, ended = false, position = restart) }
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        session = scope.launch { runSession(restart) }
    }

    fun pause() {
        stopSession()
        _state.update { it.copy(playing = false, buffering = false, word = null) }
        wakeLock.release()
    }

    /** Moves [delta] sentences (±1) and keeps playing if it was. */
    fun skip(delta: Int) {
        val c = cursor ?: return
        val from = state.value.position ?: return
        scope.launch {
            val target = withContext(Dispatchers.IO) { if (delta > 0) c.next(from) else c.previous(from) } ?: return@launch
            val wasPlaying = state.value.playing
            stopSession()
            moveTo(target)
            if (wasPlaying) play()
        }
    }

    private fun restartIfPlaying() {
        if (!state.value.playing) return
        stopSession()
        play()
    }

    private fun stopSession() {
        player?.stop()
        session?.cancel()
        session = null
    }

    private suspend fun moveTo(position: Position) {
        val title = parser?.book?.chapters?.getOrNull(position.chapter)?.title
        _state.update { it.copy(position = position, chapterTitle = title, ended = false, word = null) }
        state.value.book?.let { library.savePosition(it.id, position.chapter, position.paragraph, position.sentence) }
    }

    private suspend fun runSession(start: Position) = coroutineScope {
        val c = cursor ?: return@coroutineScope
        withContext(ttsDispatcher) { engine.load() }
        val audio = PcmPlayer(engine.sampleRate).also { player = it }
        val markers = ArrayDeque<Marker>()
        val voice = voice
        val speed = speed

        val utterances = Channel<Utterance>(capacity = 2)
        launch(ttsDispatcher) {
            var pos = c.normalize(start)
            while (pos != null && isActive) {
                val text = c.text(pos)
                val samples = engine.synthesize(text, voice, speed)
                utterances.send(Utterance(pos, samples, WordTiming.words(text), c.sentenceStart(pos)))
                pos = c.next(pos)
            }
            utterances.close()
        }

        val ticker = launch {
            while (isActive) {
                val played = audio.framesPlayed
                val marker = synchronized(markers) { markers.lastOrNull { it.startFrame <= played } }
                if (marker != null) {
                    val heard = marker.utterance.position
                    if (heard != state.value.position) moveTo(heard)
                    val word = marker.utterance.wordAt((played - marker.startFrame).toFloat() / marker.utterance.samples.size)
                    _state.update { it.copy(buffering = false, word = word) }
                }
                delay(TICK_MS)
            }
        }

        try {
            withContext(Dispatchers.IO) {
                for (utterance in utterances) {
                    synchronized(markers) { markers.addLast(Marker(audio.framesWritten, utterance)) }
                    audio.play(utterance.samples)
                }
                // Let the tail of the last sentence finish.
                while (isActive && audio.framesPlayed < audio.framesWritten) delay(TICK_MS)
            }
            ticker.cancel()
            _state.update { it.copy(playing = false, buffering = false, ended = true, word = null) }
            wakeLock.release()
        } finally {
            ticker.cancel()
            if (player === audio) player = null
            audio.stop()
            audio.release()
        }
    }

    private class Utterance(
        val position: Position,
        val samples: FloatArray,
        val words: List<WordTiming.Word>,
        /** Offset of the sentence within its paragraph, to place [words] in the reader. */
        val sentenceStart: Int,
    ) {
        fun wordAt(fraction: Float): IntRange? {
            val word = words.getOrNull(WordTiming.indexAt(words, fraction)) ?: return null
            return (sentenceStart + word.start) until (sentenceStart + word.end)
        }
    }

    /** An utterance and the audio frame it starts at. */
    private class Marker(val startFrame: Long, val utterance: Utterance)

    private companion object {
        // Short enough for the word cursor to keep pace with speech.
        const val TICK_MS = 60L
        // Safety net in case a pause is somehow missed; re-acquired on every play.
        const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L
    }
}
