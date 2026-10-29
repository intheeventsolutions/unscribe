package au.com.intheevent.unscribe.epub

data class EpubBook(
    val title: String,
    val author: String?,
    /** Spine items in reading order. */
    val chapters: List<Chapter>,
    /** Table of contents, flattened; entries point at chapter indexes. */
    val toc: List<TocEntry>,
    /** Zip path of the cover image, if the book declares one. */
    val coverPath: String?,
)

data class Chapter(
    /** Zip path of the chapter's XHTML file. */
    val path: String,
    val title: String?,
)

data class TocEntry(
    val title: String,
    val chapterIndex: Int,
    val depth: Int,
)
