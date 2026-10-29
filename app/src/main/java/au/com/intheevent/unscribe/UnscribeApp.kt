package au.com.intheevent.unscribe

import android.app.Application
import au.com.intheevent.unscribe.data.Library
import au.com.intheevent.unscribe.playback.Narrator

/** Holds the app-wide library and narrator, shared by the UI and the playback service. */
class UnscribeApp : Application() {
    val library by lazy { Library(this) }
    val narrator by lazy { Narrator(this, library) }
}
