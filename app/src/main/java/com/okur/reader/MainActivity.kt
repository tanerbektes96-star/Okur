package com.okur.reader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.okur.reader.data.AppPrefs
import com.okur.reader.reader.ReaderKeys
import com.okur.reader.ui.AppRoot
import com.okur.reader.ui.theme.OkurTheme

class MainActivity : ComponentActivity() {

    private var incoming by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        val container = (application as OkurApp).container
        setContent {
            val prefs by container.settings.appPrefs.collectAsStateWithLifecycle(initialValue = AppPrefs())
            OkurTheme(mode = prefs.themeMode, dynamicColor = prefs.dynamicColor) {
                AppRoot(container, incoming, onIncomingHandled = { incoming = emptyList() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uris = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM),
            )
            else -> emptyList()
        }
        if (uris.isNotEmpty()) incoming = uris
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val handler = ReaderKeys.handler
            if (handler != null) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (handler(code)) return true
                } else if (event.action == KeyEvent.ACTION_UP) {
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
