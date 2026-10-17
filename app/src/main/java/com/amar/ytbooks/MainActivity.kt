package com.amar.ytbooks

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.amar.ytbooks.ui.AppRoot
import com.amar.ytbooks.ui.MainViewModel
import com.amar.ytbooks.ui.theme.YtBooksTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            YtBooksTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** A link shared from the YouTube app (Share → YT Books) is added straight to the library. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        Graph.library.addFromText(text)
    }

    override fun onStop() {
        super.onStop()
        // YouTube can't keep playing behind the lock screen / other apps – pause and save the spot.
        vm.onAppBackground()
    }
}
