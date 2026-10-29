package au.com.intheevent.unscribe.playback

import au.com.intheevent.unscribe.epub.EpubParser
import au.com.intheevent.unscribe.text.SentenceSplitter
import java.util.concurrent.ConcurrentHashMap

/** A sentence in a book. */
data class Position(val chapter: Int, val paragraph: Int, val sentence: Int)

/** Walks a book sentence by sentence, across paragraph and chapter boundaries. Thread-safe. */
class BookCursor(private val parser: EpubParser) {
    private val chapterCount = parser.book.chapters.size

    /** chapter -> paragraphs -> sentence texts. */
    private val cache = ConcurrentHashMap<Int, List<List<String>>>()

    private fun chapter(index: Int): List<List<String>> = cache.getOrPut(index) {
        parser.paragraphs(index).map { paragraph -> SentenceSplitter.split(paragraph).map { it.text(paragraph) } }
    }

    fun text(position: Position): String = chapter(position.chapter)[position.paragraph][position.sentence]

    /** [position] if it points at a sentence, otherwise the next one that does; null past the end. */
    fun normalize(position: Position): Position? {
        var (c, p, s) = position
        if (c < 0) return first()
        while (c < chapterCount) {
            val paragraphs = chapter(c)
            while (p < paragraphs.size) {
                if (s < paragraphs[p].size) return Position(c, p, s)
                p++
                s = 0
            }
            c++
            p = 0
            s = 0
        }
        return null
    }

    fun next(position: Position): Position? =
        normalize(position.copy(sentence = position.sentence + 1))

    /** The sentence before [position], or [position] itself at the very start. */
    fun previous(position: Position): Position {
        var (c, p, s) = position
        if (s > 0) return Position(c, p, s - 1)
        while (true) {
            p--
            while (p < 0) {
                c--
                if (c < 0) return position
                p = chapter(c).size - 1
            }
            val sentences = chapter(c)[p]
            if (sentences.isNotEmpty()) return Position(c, p, sentences.size - 1)
        }
    }

    private fun first(): Position? = normalize(Position(0, 0, 0))
}
