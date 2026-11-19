package com.mohdrozaimi.statuspro4k

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mohdrozaimi.statuspro4k.engine.ExportMode
import com.mohdrozaimi.statuspro4k.engine.JobState
import com.mohdrozaimi.statuspro4k.engine.ProcessingManager
import com.mohdrozaimi.statuspro4k.ui.AppViewModel
import com.mohdrozaimi.statuspro4k.ui.screens.CompareScreen
import com.mohdrozaimi.statuspro4k.ui.screens.CompleteScreen
import com.mohdrozaimi.statuspro4k.ui.screens.EditorScreen
import com.mohdrozaimi.statuspro4k.ui.screens.ExportScreen
import com.mohdrozaimi.statuspro4k.ui.screens.HomeScreen
import com.mohdrozaimi.statuspro4k.ui.screens.PreviewScreen
import com.mohdrozaimi.statuspro4k.ui.screens.ProcessingScreen
import com.mohdrozaimi.statuspro4k.ui.theme.Pro
import com.mohdrozaimi.statuspro4k.ui.theme.StatusProTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private var sharedUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            StatusProTheme {
                val nav = rememberNavController()
                var pendingMode by remember { mutableStateOf(ExportMode.WHATSAPP_OPTIMIZED) }

                val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) vm.load(uri, pendingMode) { nav.navigate("editor") }
                }
                val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

                LaunchedEffect(sharedUri) {
                    sharedUri?.let { u -> sharedUri = null; vm.load(u, ExportMode.WHATSAPP_OPTIMIZED) { nav.navigate("editor") } }
                }
                // If an export is running (e.g. app reopened from the notification), show it.
                LaunchedEffect(Unit) {
                    if (ProcessingManager.state.value is JobState.Running) nav.navigate("processing")
                }

                NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize().background(Pro.Black)) {
                    composable("home") {
                        HomeScreen(vm, onPick = { mode ->
                            pendingMode = mode
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        }, onOpenRecent = { nav.navigate("complete") })
                    }
                    composable("editor") { EditorScreen(vm, onBack = { nav.popBackStack() }, onNext = { nav.navigate("export") }) }
                    composable("export") {
                        ExportScreen(vm, onBack = { nav.popBackStack() }, onStart = {
                            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                            nav.navigate("processing")
                        })
                    }
                    composable("processing") {
                        ProcessingScreen(vm,
                            onDone = { nav.navigate("complete") { popUpTo("home") } },
                            onBack = { nav.popBackStack("home", false) })
                    }
                    composable("complete") {
                        CompleteScreen(vm,
                            onHome = { ProcessingManager.reset(); vm.refreshRecents(); nav.popBackStack("home", false) },
                            onPreview = { nav.navigate("preview") },
                            onCompare = { nav.navigate("compare") })
                    }
                    composable("preview") { PreviewScreen(vm) { nav.popBackStack() } }
                    composable("compare") { CompareScreen(vm) { nav.popBackStack() } }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)?.let { sharedUri = it }
        }
    }
}
