package au.com.intheevent.unscribe.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String?,
    /** The book's own copy of the epub, inside app storage. */
    val filePath: String,
    val coverPath: String?,
    val addedAt: Long,
    val lastOpenedAt: Long,
    /** Reading position: where Play starts next time. */
    val chapterIndex: Int = 0,
    val paragraphIndex: Int = 0,
    val sentenceIndex: Int = 0,
)
