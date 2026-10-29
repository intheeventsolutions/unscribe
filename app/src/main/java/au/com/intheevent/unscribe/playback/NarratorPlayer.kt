package au.com.intheevent.unscribe.playback

import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * Exposes the [Narrator] as a Media3 player, which gives us the notification,
 * lock screen, headset and Bluetooth controls for free.
 *
 * Media3 only offers previous/next when there are neighbouring items, so the book
 * is presented as the middle of a three-item playlist; seeking to either
 * neighbour steps one sentence back or forward.
 */
@UnstableApi
class NarratorPlayer(private val narrator: Narrator) : SimpleBasePlayer(Looper.getMainLooper()) {
    private val scope = MainScope()

    init {
        // The word cursor changes several times a second; media controls don't care.
        scope.launch { narrator.state.map { it.copy(word = null) }.distinctUntilChanged().collect { invalidateState() } }
    }

    override fun getState(): State {
        val s = narrator.state.value
        val builder = State.Builder()
            .setAvailableCommands(COMMANDS)
            .setPlayWhenReady(s.playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        val book = s.book ?: return builder.setPlaybackState(Player.STATE_IDLE).build()

        val metadata = MediaMetadata.Builder()
            .setTitle(s.chapterTitle ?: book.title)
            .setArtist(book.title)
            .setAlbumTitle(book.author)
            .setArtworkUri(book.coverPath?.let { Uri.fromFile(File(it)) })
            .build()
        val items = listOf("previous", "book-${book.id}", "next").map { uid ->
            MediaItemData.Builder(uid)
                .setMediaItem(MediaItem.Builder().setMediaId(uid).build())
                .setMediaMetadata(metadata)
                .build()
        }
        return builder
            .setPlaylist(items)
            .setCurrentMediaItemIndex(1)
            .setPlaybackState(
                when {
                    s.ended -> Player.STATE_ENDED
                    s.playing && s.buffering -> Player.STATE_BUFFERING
                    else -> Player.STATE_READY
                },
            )
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) narrator.play() else narrator.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        narrator.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when {
            mediaItemIndex == 0 || seekCommand == Player.COMMAND_SEEK_TO_PREVIOUS -> narrator.skip(-1)
            mediaItemIndex == 2 || seekCommand == Player.COMMAND_SEEK_TO_NEXT -> narrator.skip(1)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        narrator.pause()
        scope.cancel()
        return Futures.immediateVoidFuture()
    }

    private companion object {
        val COMMANDS: Player.Commands = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_RELEASE,
        ).build()
    }
}
