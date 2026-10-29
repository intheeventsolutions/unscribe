package au.com.intheevent.unscribe.playback

import au.com.intheevent.unscribe.epub.EpubParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookCursorTest {
    private val parser = EpubParser(File(checkNotNull(javaClass.classLoader?.getResource("alice.epub")).toURI()))
    private val cursor = BookCursor(parser)

    @After
    fun close() = parser.close()

    @Test
    fun walksForwardAcrossParagraphsAndChapters() {
        val chapterOne = parser.book.toc.first { it.title.contains("Rabbit-Hole") }.chapterIndex
        var pos: Position? = cursor.normalize(Position(chapterOne, 0, 0))
        val visited = mutableListOf<Position>()
        while (pos != null && pos.chapter == chapterOne) {
            visited += pos
            pos = cursor.next(pos)
        }
        assertTrue(visited.size > 50)
        assertEquals(visited.distinct(), visited)
        // Landed on the first sentence of the next chapter.
        assertEquals(Position(chapterOne + 1, 0, 0), pos)
    }

    @Test
    fun previousUndoesNext() {
        val start = cursor.normalize(Position(2, 3, 0))!!
        var pos = start
        repeat(40) { pos = cursor.next(pos)!! }
        repeat(40) { pos = cursor.previous(pos) }
        assertEquals(start, pos)
    }

    @Test
    fun previousStopsAtTheStart() {
        val first = cursor.normalize(Position(0, 0, 0))!!
        assertEquals(first, cursor.previous(first))
    }

    @Test
    fun normalizeSkipsPastOutOfRangePositions() {
        val pos = cursor.normalize(Position(1, 10_000, 0))
        assertEquals(Position(2, 0, 0), pos)
        assertNull(cursor.normalize(Position(parser.book.chapters.size, 0, 0)))
        assertTrue(cursor.text(pos!!).isNotBlank())
    }
}
