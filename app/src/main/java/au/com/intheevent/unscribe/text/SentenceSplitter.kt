package au.com.intheevent.unscribe.text

/** A sentence's character range within its paragraph (end exclusive). */
data class Sentence(val start: Int, val end: Int) {
    fun text(paragraph: String): String = paragraph.substring(start, end)
}

/**
 * Splits a paragraph into sentences for speaking. Errs towards not splitting:
 * a missed boundary only means a longer chunk, a wrong one breaks the
 * intonation mid-sentence.
 */
object SentenceSplitter {
    /** Very long sentences are broken at clause punctuation so synthesis starts quickly. */
    const val MAX_CHARS = 300

    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "st", "mt", "jr", "sr", "prof", "rev", "hon", "capt", "col", "gen",
        "lt", "sgt", "messrs", "mme", "mlle", "vs", "etc", "e.g", "i.e", "cf", "no", "vol", "ch", "fig",
        "approx", "dept", "est", "inc", "ltd", "co", "ave", "rd",
    )
    private const val CLOSERS = "\"'”’)]»"
    private const val OPENERS = "\"'“‘([«"

    fun split(paragraph: String): List<Sentence> {
        val sentences = mutableListOf<Sentence>()
        var start = skipSpace(paragraph, 0)
        var i = start
        while (i < paragraph.length) {
            val c = paragraph[i]
            if (c == '.' || c == '!' || c == '?' || c == '…') {
                var end = i + 1
                while (end < paragraph.length && (paragraph[end] in ".!?…" || paragraph[end] in CLOSERS)) end++
                val next = skipSpace(paragraph, end)
                if (next > end && next < paragraph.length && startsSentence(paragraph, next) &&
                    !(c == '.' && isAbbreviation(paragraph, start, i))
                ) {
                    sentences += Sentence(start, end)
                    start = next
                    i = next
                    continue
                }
                i = end
                continue
            }
            i++
        }
        if (start < paragraph.length) sentences += Sentence(start, trimEnd(paragraph, paragraph.length))
        return sentences.flatMap { breakLong(paragraph, it) }
    }

    private fun startsSentence(text: String, at: Int): Boolean {
        var j = at
        while (j < text.length && text[j] in OPENERS) j++
        return j < text.length && (text[j].isUpperCase() || text[j].isDigit())
    }

    private fun isAbbreviation(text: String, sentenceStart: Int, dot: Int): Boolean {
        var wordStart = dot
        while (wordStart > sentenceStart && !text[wordStart - 1].isWhitespace() && text[wordStart - 1] !in OPENERS) wordStart--
        val word = text.substring(wordStart, dot)
        // Single initials: "J. R. R. Tolkien".
        if (word.length == 1 && word[0].isUpperCase()) return true
        return word.lowercase() in ABBREVIATIONS
    }

    private fun breakLong(text: String, sentence: Sentence): List<Sentence> {
        if (sentence.end - sentence.start <= MAX_CHARS) return listOf(sentence)
        val pieces = mutableListOf<Sentence>()
        var start = sentence.start
        while (sentence.end - start > MAX_CHARS) {
            val window = text.substring(start, start + MAX_CHARS)
            val cut = listOf(';', ':', ',', '—', '–')
                .map { window.lastIndexOf(it) }
                .firstOrNull { it > MAX_CHARS / 3 }
                ?: window.lastIndexOf(' ').takeIf { it > 0 }
                ?: break
            val end = start + cut + 1
            pieces += Sentence(start, trimEnd(text, end))
            start = skipSpace(text, end)
        }
        pieces += Sentence(start, sentence.end)
        return pieces
    }

    private fun skipSpace(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }

    private fun trimEnd(text: String, end: Int): Int {
        var e = end
        while (e > 0 && text[e - 1].isWhitespace()) e--
        return e
    }
}
