package au.com.intheevent.unscribe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import au.com.intheevent.unscribe.ui.HomeScreen
import au.com.intheevent.unscribe.ui.UnscribeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnscribeTheme {
                HomeScreen()
            }
        }
    }
}
