package au.com.intheevent.unscribe.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): BookEntity?

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("UPDATE books SET lastOpenedAt = :at WHERE id = :id")
    suspend fun markOpened(id: Long, at: Long)

    @Query(
        "UPDATE books SET chapterIndex = :chapter, paragraphIndex = :paragraph, sentenceIndex = :sentence, " +
            "wordIndex = :word WHERE id = :id",
    )
    suspend fun savePosition(id: Long, chapter: Int, paragraph: Int, sentence: Int, word: Int)
}
