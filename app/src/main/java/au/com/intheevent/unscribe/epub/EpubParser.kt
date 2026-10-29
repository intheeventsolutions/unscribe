package au.com.intheevent.unscribe.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.Closeable
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

/**
 * Minimal epub 2/3 reader: metadata, reading order, table of contents, and
 * chapter text as plain paragraphs. No layout, no styling.
 */
class EpubParser(file: File) : Closeable {
    private val zip = ZipFile(file)

    val book: EpubBook = parsePackage()

    override fun close() = zip.close()

    fun readBytes(path: String): ByteArray? =
        zip.getEntry(path)?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }

    /** The chapter's text as speakable paragraphs, in order. Headings count as paragraphs. */
    fun paragraphs(chapterIndex: Int): List<String> {
        val html = readText(book.chapters[chapterIndex].path) ?: return emptyList()
        return extractParagraphs(Jsoup.parse(html))
    }

    private fun readText(path: String): String? = readBytes(path)?.toString(Charsets.UTF_8)

    private fun readXml(path: String): Document? = readText(path)?.let { Jsoup.parse(it, "", Parser.xmlParser()) }

    private fun parsePackage(): EpubBook {
        val container = readXml("META-INF/container.xml") ?: throw InvalidEpubException("Missing META-INF/container.xml")
        val opfPath = container.selectFirst("rootfile")?.attr("full-path")?.takeIf { it.isNotBlank() }
            ?: throw InvalidEpubException("No rootfile in container.xml")
        val opf = readXml(opfPath) ?: throw InvalidEpubException("Missing package file $opfPath")
        val opfDir = opfPath.substringBeforeLast('/', "")

        val manifest = opf.select("manifest > item").associate { item ->
            item.attr("id") to ManifestItem(
                path = resolve(opfDir, item.attr("href")),
                mediaType = item.attr("media-type"),
                properties = item.attr("properties").split(' ').toSet(),
            )
        }

        val spinePaths = opf.select("spine > itemref")
            .filter { it.attr("linear") != "no" }
            .mapNotNull { manifest[it.attr("idref")] }
            .filter { it.mediaType.contains("html") }
            .map { it.path }

        val toc = parseNav(manifest.values.firstOrNull { "nav" in it.properties }?.path, spinePaths)
            ?: parseNcx(opf.selectFirst("spine")?.attr("toc")?.let { manifest[it]?.path }, spinePaths)
            ?: emptyList()

        val chapterTitles = mutableMapOf<Int, String>()
        for (entry in toc) chapterTitles.putIfAbsent(entry.chapterIndex, entry.title)

        return EpubBook(
            title = opf.selectFirst("metadata > dc|title")?.text()?.trim().orEmpty().ifBlank { "Untitled" },
            author = opf.selectFirst("metadata > dc|creator")?.text()?.trim()?.ifBlank { null },
            chapters = spinePaths.mapIndexed { i, path -> Chapter(path, chapterTitles[i]) },
            toc = toc,
            coverPath = findCover(opf, manifest),
        )
    }

    private fun findCover(opf: Document, manifest: Map<String, ManifestItem>): String? {
        manifest.values.firstOrNull { "cover-image" in it.properties }?.let { return it.path }
        opf.selectFirst("metadata > meta[name=cover]")?.attr("content")?.let { id -> manifest[id]?.let { return it.path } }
        return manifest.entries.firstOrNull { (id, item) ->
            id.contains("cover", ignoreCase = true) && item.mediaType.startsWith("image/")
        }?.value?.path
    }

    /** epub 3 navigation document. */
    private fun parseNav(navPath: String?, spine: List<String>): List<TocEntry>? {
        navPath ?: return null
        val doc = Jsoup.parse(readText(navPath) ?: return null)
        val nav = doc.select("nav").firstOrNull { it.attr("epub:type") == "toc" } ?: doc.selectFirst("nav") ?: return null
        val navDir = navPath.substringBeforeLast('/', "")
        val entries = mutableListOf<TocEntry>()
        fun walk(list: Element, depth: Int) {
            for (li in list.children().filter { it.tagName() == "li" }) {
                val link = li.children().firstOrNull { it.tagName() == "a" }
                if (link != null) addEntry(entries, link.text(), resolve(navDir, link.attr("href")), depth, spine)
                li.children().filter { it.tagName() == "ol" || it.tagName() == "ul" }.forEach { walk(it, depth + 1) }
            }
        }
        nav.children().filter { it.tagName() == "ol" || it.tagName() == "ul" }.forEach { walk(it, 0) }
        return entries.ifEmpty { null }
    }

    /** epub 2 NCX table of contents. */
    private fun parseNcx(ncxPath: String?, spine: List<String>): List<TocEntry>? {
        ncxPath ?: return null
        val doc = readXml(ncxPath) ?: return null
        val ncxDir = ncxPath.substringBeforeLast('/', "")
        val entries = mutableListOf<TocEntry>()
        fun walk(parent: Element, depth: Int) {
            for (point in parent.children().filter { it.tagName() == "navPoint" }) {
                val label = point.selectFirst("navLabel > text")?.text().orEmpty()
                val src = point.selectFirst("content")?.attr("src").orEmpty()
                addEntry(entries, label, resolve(ncxDir, src), depth, spine)
                walk(point, depth + 1)
            }
        }
        doc.selectFirst("navMap")?.let { walk(it, 0) }
        return entries.ifEmpty { null }
    }

    private fun addEntry(entries: MutableList<TocEntry>, title: String, path: String, depth: Int, spine: List<String>) {
        val index = spine.indexOf(path.substringBefore('#'))
        val clean = title.replace(WHITESPACE, " ").trim()
        if (index >= 0 && clean.isNotEmpty()) entries += TocEntry(clean, index, depth)
    }

    private data class ManifestItem(val path: String, val mediaType: String, val properties: Set<String>)

    companion object {
        private val WHITESPACE = Regex("\\s+")

        private val BLOCK_TAGS = setOf(
            "address", "article", "aside", "blockquote", "body", "dd", "div", "dl", "dt", "figcaption",
            "figure", "footer", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "li", "main", "nav",
            "ol", "p", "pre", "section", "table", "tbody", "td", "tfoot", "th", "thead", "tr", "ul",
        )
        private val SKIP_TAGS = setOf("script", "style", "img", "svg", "audio", "video", "math", "head", "rt", "rp")

        /** Resolves an href relative to [dir] into a normalised zip path. */
        internal fun resolve(dir: String, href: String): String {
            val decoded = URLDecoder.decode(href.replace("+", "%2B"), "UTF-8")
            val (pathPart, fragment) = decoded.split('#', limit = 2).let { it[0] to it.getOrNull(1) }
            val segments = ArrayDeque<String>()
            val joined = if (pathPart.startsWith("/")) pathPart.drop(1) else listOf(dir, pathPart).filter { it.isNotEmpty() }.joinToString("/")
            for (segment in joined.split('/')) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> segments.removeLastOrNull()
                    else -> segments.addLast(segment)
                }
            }
            val path = segments.joinToString("/")
            return if (fragment != null) "$path#$fragment" else path
        }

        internal fun extractParagraphs(doc: Document): List<String> {
            // Footnote markers ("¹", "[12]") read badly aloud.
            doc.select("[epub:type~=noteref], [role=doc-noteref], sup:has(a[href])").remove()
            doc.select("[hidden], [epub:type~=pagebreak], [role=doc-pagebreak]").remove()
            val out = mutableListOf<String>()
            collect(doc.body(), out)
            return out
        }

        private fun collect(element: Element, out: MutableList<String>) {
            val run = StringBuilder()
            fun flush() {
                val text = run.toString().replace(WHITESPACE, " ").trim()
                if (text.any { it.isLetterOrDigit() }) out += text
                run.clear()
            }
            for (node in element.childNodes()) {
                when {
                    node is TextNode -> run.append(node.wholeText)
                    node !is Element -> Unit
                    node.tagName() in SKIP_TAGS -> Unit
                    node.tagName() == "br" -> run.append(' ')
                    node.tagName() in BLOCK_TAGS || node.children().any { it.tagName() in BLOCK_TAGS } -> {
                        flush()
                        collect(node, out)
                    }
                    else -> run.append(inlineText(node))
                }
            }
            flush()
        }

        private fun inlineText(element: Element): String = buildString {
            for (node in element.childNodes()) {
                when {
                    node is TextNode -> append(node.wholeText)
                    node is Element && node.tagName() in SKIP_TAGS -> Unit
                    node is Element && node.tagName() == "br" -> append(' ')
                    node is Element -> append(inlineText(node))
                }
            }
        }
    }
}

class InvalidEpubException(message: String) : Exception(message)
