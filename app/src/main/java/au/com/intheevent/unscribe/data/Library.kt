package au.com.intheevent.unscribe.data

import android.content.Context
import android.net.Uri
import au.com.intheevent.unscribe.epub.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Imports and removes books. Each book gets its own copy of the epub and cover. */
class Library(private val context: Context) {
    private val dao = AppDatabase.get(context).books()
    private val booksDir = File(context.filesDir, "books")
    private val coversDir = File(context.filesDir, "covers")

    val books = dao.observeAll()

    /** Copies the picked file into app storage and adds it. Throws if it isn't a readable epub. */
    suspend fun import(uri: Uri): BookEntity = withContext(Dispatchers.IO) {
        booksDir.mkdirs()
        coversDir.mkdirs()
        val id = UUID.randomUUID().toString()
        val file = File(booksDir, "$id.epub")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open the file")
            input.use { src -> file.outputStream().use { src.copyTo(it) } }
            val (book, cover) = EpubParser(file).use { parser ->
                val cover = parser.book.coverPath?.let { path ->
                    parser.readBytes(path)?.let { bytes -> File(coversDir, id).apply { writeBytes(bytes) } }
                }
                parser.book to cover
            }
            val now = System.currentTimeMillis()
            val entity = BookEntity(
                title = book.title,
                author = book.author,
                filePath = file.absolutePath,
                coverPath = cover?.absolutePath,
                addedAt = now,
                lastOpenedAt = now,
            )
            entity.copy(id = dao.insert(entity))
        } catch (e: Exception) {
            file.delete()
            File(coversDir, id).delete()
            throw e
        }
    }

    suspend fun delete(book: BookEntity) = withContext(Dispatchers.IO) {
        dao.delete(book)
        File(book.filePath).delete()
        book.coverPath?.let { File(it).delete() }
    }

    suspend fun get(id: Long): BookEntity? = dao.get(id)

    suspend fun markOpened(id: Long) = dao.markOpened(id, System.currentTimeMillis())

    suspend fun savePosition(id: Long, chapter: Int, paragraph: Int, sentence: Int) =
        dao.savePosition(id, chapter, paragraph, sentence)
}
