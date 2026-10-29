package au.com.intheevent.unscribe.playback

import au.com.intheevent.unscribe.epub.EpubParser
import au.com.intheevent.unscribe.text.Sentence
import au.com.intheevent.unscribe.text.SentenceSplitter
import java.util.concurrent.ConcurrentHashMap

/** A word in a book: the [word]th word (see WordTiming) of a sentence. */
data class Position(val chapter: Int, val paragraph: Int, val sentence: Int, val word: Int = 0)

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
                if (s < paragraphs[p].size) {
                    val unchanged = c == position.chapter && p == position.paragraph && s == position.sentence
                    return if (unchanged) position else Position(c, p, s)
                }
                p++
                s = 0
            }
            c++
            p = 0
            s = 0
        }
        return null
    }

    /** Start of the next sentence. */
    fun next(position: Position): Position? =
        normalize(Position(position.chapter, position.paragraph, position.sentence + 1))

    /**
     * Start of the current sentence if [position] is partway through it, otherwise the
     * start of the previous one (or [position] itself at the very start of the book).
     */
    fun previous(position: Position): Position {
        if (position.word > 0) return position.copy(word = 0)
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
