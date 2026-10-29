package au.com.intheevent.unscribe.text

import org.junit.Assert.assertEquals
import org.junit.Test

class WordTimingTest {
    @Test
    fun findsWordRanges() {
        val text = "“Stop,” she said."
        val words = WordTiming.words(text)
        assertEquals(listOf("“Stop,”", "she", "said."), words.map { text.substring(it.start, it.end) })
    }

    @Test
    fun walksThroughWordsInOrder() {
        val words = WordTiming.words("The cat sat on the mat.")
        assertEquals(0, WordTiming.indexAt(words, 0f))
        assertEquals(words.lastIndex, WordTiming.indexAt(words, 1f))
        val seen = (0..100).map { WordTiming.indexAt(words, it / 100f) }
        assertEquals(seen.sorted(), seen)
        assertEquals(words.indices.toList(), seen.distinct())
    }

    @Test
    fun longWordsAndPausesTakeLonger() {
        val words = WordTiming.words("Extraordinarily, I.")
        // "Extraordinarily," (15 letters + comma pause) is most of the audio.
        assertEquals(0, WordTiming.indexAt(words, 0.7f))
        assertEquals(1, WordTiming.indexAt(words, 0.9f))
    }

    @Test
    fun emptySentenceHasNoWord() {
        assertEquals(-1, WordTiming.indexAt(WordTiming.words("   "), 0.5f))
    }
}
