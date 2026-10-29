package au.com.intheevent.unscribe.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun fixture(name: String): File =
        File(checkNotNull(javaClass.classLoader?.getResource(name)) { "missing fixture $name" }.toURI())

    @Test
    fun parsesGutenbergEpub3() {
        EpubParser(fixture("alice.epub")).use { parser ->
            val book = parser.book
            assertEquals("Alice's Adventures in Wonderland", book.title)
            assertEquals("Lewis Carroll", book.author)
            assertTrue(book.chapters.size >= 12)
            assertNotNull(book.coverPath)
            assertNotNull(parser.readBytes(book.coverPath!!))

            val rabbitHole = book.toc.first { it.title.contains("Rabbit-Hole") }
            val paragraphs = parser.paragraphs(rabbitHole.chapterIndex)
            assertTrue(paragraphs.any { it.startsWith("Alice was beginning to get very tired") })
            assertTrue(paragraphs.none { it.isBlank() })
            // TOC entries are in reading order.
            assertEquals(book.toc.map { it.chapterIndex }.sorted(), book.toc.map { it.chapterIndex })
        }
    }

    @Test
    fun parsesEpub2WithNcxAndCleansText() {
        val file = buildEpub(
            "META-INF/container.xml" to """<?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>""",
            "OPS/book.opf" to """<?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Book</dc:title><dc:creator>A. Writer</dc:creator>
                    <meta name="cover" content="cov"/>
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="cov" href="images/cover.png" media-type="image/png"/>
                    <item id="c1" href="text/chapter%201.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="notes" href="text/notes.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine toc="ncx"><itemref idref="c1"/><itemref idref="c2"/><itemref idref="notes" linear="no"/></spine>
                </package>""",
            "OPS/toc.ncx" to """<?xml version="1.0"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/"><navMap>
                  <navPoint id="n1"><navLabel><text>Chapter One</text></navLabel><content src="text/chapter%201.xhtml"/>
                    <navPoint id="n1a"><navLabel><text>Part A</text></navLabel><content src="text/chapter%201.xhtml#a"/></navPoint>
                  </navPoint>
                  <navPoint id="n2"><navLabel><text>Chapter Two</text></navLabel><content src="text/ch2.xhtml"/></navPoint>
                </navMap></ncx>""",
            "OPS/text/chapter 1.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                <head><title>x</title><style>p{}</style></head><body>
                  <h1>Chapter One</h1>
                  <p>It was a <em>dark</em> night.<sup><a href="notes.xhtml#n1">1</a></sup> The end<a epub:type="noteref" href="#n2">*</a>.</p>
                  <div>Loose text in a div <span>with a span</span>
                    <p>and a nested paragraph.</p>
                  </div>
                  <p><img src="x.png" alt="A picture"/></p>
                  <span epub:type="pagebreak" title="12"></span>
                  <blockquote><p>Quoted line one<br/>line two</p></blockquote>
                </body></html>""",
            "OPS/text/ch2.xhtml" to "<html><body><p>Second.</p></body></html>",
            "OPS/text/notes.xhtml" to "<html><body><p>Note.</p></body></html>",
        )
        EpubParser(file).use { parser ->
            val book = parser.book
            assertEquals("Test Book", book.title)
            assertEquals("A. Writer", book.author)
            assertEquals(listOf("OPS/text/chapter 1.xhtml", "OPS/text/ch2.xhtml"), book.chapters.map { it.path })
            assertEquals("OPS/images/cover.png", book.coverPath)
            assertEquals(
                listOf(TocEntry("Chapter One", 0, 0), TocEntry("Part A", 0, 1), TocEntry("Chapter Two", 1, 0)),
                book.toc,
            )
            assertEquals("Chapter One", book.chapters[0].title)

            val paragraphs = parser.paragraphs(0)
            assertEquals(
                listOf(
                    "Chapter One",
                    "It was a dark night. The end.",
                    "Loose text in a div with a span",
                    "and a nested paragraph.",
                    "Quoted line one line two",
                ),
                paragraphs,
            )
            assertFalse(paragraphs.any { it.contains("picture") })
        }
    }

    @Test
    fun resolvesRelativePaths() {
        assertEquals("OPS/a/b.xhtml", EpubParser.resolve("OPS/text", "../a/b.xhtml"))
        assertEquals("b.xhtml#x", EpubParser.resolve("", "./b.xhtml#x"))
        assertEquals("OPS/my file.xhtml", EpubParser.resolve("OPS", "my%20file.xhtml"))
    }

    private fun buildEpub(vararg entries: Pair<String, String>): File {
        val file = tmp.newFile("test.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.trimIndent().toByteArray())
            }
        }
        return file
    }
}
