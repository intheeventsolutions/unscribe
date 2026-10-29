package au.com.intheevent.unscribe.playback

import au.com.intheevent.unscribe.epub.EpubParser
import au.com.intheevent.unscribe.text.Sentence
import au.com.intheevent.unscribe.text.SentenceSplitter
import java.util.concurrent.ConcurrentHashMap

/** A sentence in a book. */
data class Position(val chapter: Int, val paragraph: Int, val sentence: Int)

/** Walks a book sentence by sentence, across paragraph and chapter boundaries. Thread-safe. */
class BookCursor(private val parser: EpubParser) {
    private val chapterCount = parser.book.chapters.size

    private class Paragraph(val text: String, val sentences: List<Sentence>) {
        val size get() = sentences.size
    }

    private val cache = ConcurrentHashMap<Int, List<Paragraph>>()

    private fun chapter(index: Int): List<Paragraph> = cache.getOrPut(index) {
        parser.paragraphs(index).map { Paragraph(it, SentenceSplitter.split(it)) }
    }

    fun text(position: Position): String = sentence(position).let { (paragraph, range) -> range.text(paragraph.text) }

    /** Where the sentence starts within its paragraph's text. */
    fun sentenceStart(position: Position): Int = sentence(position).second.start

    private fun sentence(position: Position): Pair<Paragraph, Sentence> =
        chapter(position.chapter)[position.paragraph].let { it to it.sentences[position.sentence] }

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
            val sentences = chapter(c)[p].size
            if (sentences > 0) return Position(c, p, sentences - 1)
        }
    }

    private fun first(): Position? = normalize(Position(0, 0, 0))
}
