package io.github.marciano.psitlumocnik

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.IntentCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.marciano.psitlumocnik.ui.AppViewModel
import io.github.marciano.psitlumocnik.ui.UiEvent
import io.github.marciano.psitlumocnik.ui.screens.DogScreen
import io.github.marciano.psitlumocnik.ui.screens.DogsScreen
import io.github.marciano.psitlumocnik.ui.screens.RecordingScreen
import io.github.marciano.psitlumocnik.ui.screens.SettingsScreen
import io.github.marciano.psitlumocnik.ui.theme.PsiTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShare(intent)
        setContent {
            val settings by vm.settings.collectAsState()
            PsiTheme(settings.theme) {
                val nav = rememberNavController()
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    vm.events.collect { e ->
                        when (e) {
                            is UiEvent.Message -> snackbar.showSnackbar(e.text)
                            is UiEvent.OpenRecording -> nav.navigate("rec/${e.id}")
                            is UiEvent.OpenDog -> nav.navigate("dog/${e.id}") { popUpTo("dogs") }
                            is UiEvent.Share -> startActivity(e.intent)
                        }
                    }
                }
                NavHost(nav, startDestination = "dogs") {
                    composable("dogs") {
                        DogsScreen(vm, snackbar, onOpenDog = { nav.navigate("dog/$it") }, onSettings = { nav.navigate("settings") })
                    }
                    composable("dog/{id}", listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                        DogScreen(vm, entry.arguments?.getString("id")!!, snackbar,
                            onBack = { nav.popBackStack() }, onOpenRecording = { nav.navigate("rec/$it") })
                    }
                    composable("rec/{id}", listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                        RecordingScreen(vm, entry.arguments?.getString("id")!!, snackbar, onBack = { nav.popBackStack() })
                    }
                    composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { vm.pendingShared.value = it }
    }
}
