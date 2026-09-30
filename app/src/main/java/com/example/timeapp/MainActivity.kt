package com.example.timeapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timeapp.data.AppSettingsDataStore
import com.example.timeapp.ui.AppViewModel
import com.example.timeapp.ui.TimeApp
import com.example.timeapp.ui.theme.TimeTheme

// FragmentActivity (not a bare ComponentActivity) is required for androidx.biometric's
// BiometricPrompt, which needs a FragmentActivity/Fragment host to attach its dialog.
class MainActivity : FragmentActivity() {
    // Survives the share intent being consumed once, so rotating the phone doesn't
    // re-open the Add-link form with the same URL again.
    private val sharedText = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val openRoute = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedText.value = extractSharedText(intent)
        openRoute.value = extractRoute(intent)
        enableEdgeToEdge()
        val database = (application as TimeApplication).database
        val settingsStore = AppSettingsDataStore(applicationContext)
        setContent {
            val appViewModel: AppViewModel = viewModel(factory = AppViewModel.Factory(settingsStore))
            val settings by appViewModel.uiState.collectAsState()
            TimeTheme(
                colorway = settings.colorway, themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor, pureBlack = settings.pureBlack,
                reduceCelebrations = settings.reduceCelebrations,
            ) {
                TimeApp(
                    appViewModel = appViewModel,
                    database = database,
                    sharedText = sharedText.value,
                    onSharedTextConsumed = { sharedText.value = null },
                    openRoute = openRoute.value,
                    onOpenRouteConsumed = { openRoute.value = null },
                )
            }
        }
    }

    /** A share can arrive while the app is already open, too. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractSharedText(intent)?.let { sharedText.value = it }
        extractRoute(intent)?.let { openRoute.value = it }
    }

    /** A notification's destination. Only in-app routes from our own notifications are taken —
     * this activity is exported, so anything else is ignored. */
    private fun extractRoute(intent: Intent?): String? =
        intent?.getStringExtra(EXTRA_OPEN_ROUTE)?.takeIf { r -> ALLOWED_ROUTES.any { r == it || r.startsWith("$it/") } }

    companion object {
        const val EXTRA_OPEN_ROUTE = "com.example.timeapp.OPEN_ROUTE"
        private val ALLOWED_ROUTES = listOf("plan/task", "plan/month", "money", "habits", "focus", "settings/data-backup")
    }

    private fun extractSharedText(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
}
