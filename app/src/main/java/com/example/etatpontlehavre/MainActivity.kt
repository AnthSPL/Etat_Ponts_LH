package com.example.etatpontlehavre

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.etatpontlehavre.notifications.BridgeAlertWorker
import com.example.etatpontlehavre.ui.screens.BridgeDetailScreen
import com.example.etatpontlehavre.ui.screens.HomeScreen
import com.example.etatpontlehavre.ui.theme.EtatPontLeHavreTheme
import com.example.etatpontlehavre.ui.viewmodel.BridgeUiState
import com.example.etatpontlehavre.ui.viewmodel.BridgeViewModel
import org.osmdroid.config.Configuration

/**
 * Activité principale et point d'entrée unique de l'application "État des Ponts du Havre".
 */
class MainActivity : ComponentActivity() {

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // CONFIGURATION OSMDROID GLOBALE
        val prefs = applicationContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        Configuration.getInstance().load(applicationContext, prefs)
        Configuration.getInstance().userAgentValue = packageName

        // Demande dynamique des permissions nécessaires (Localisation & Notifications)
        checkAndRequestPermissions()

        // Planification de la vérification en arrière-plan avec WorkManager
        BridgeAlertWorker.schedulePeriodicCheck(this)

        setContent {
            EtatPontLeHavreTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EtatPontLeHavreApp()
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}

/**
 * Composable racine orchestrant la navigation entre [HomeScreen] et [BridgeDetailScreen].
 */
@Composable
fun EtatPontLeHavreApp(viewModel: BridgeViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    var selectedBridgeId by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Force le rafraîchissement des données à chaque retour au premier plan
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadBridges(isPullToRefresh = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onBridgeClick = { bridge ->
                    selectedBridgeId = bridge.id
                    navController.navigate("detail")
                }
            )
        }

        composable("detail") {
            val bridgeList = when (val state = uiState) {
                is BridgeUiState.Success -> state.bridges
                else -> emptyList()
            }
            val bridge = bridgeList.find { it.id == selectedBridgeId }

            if (bridge != null) {
                BridgeDetailScreen(
                    bridge = bridge,
                    onBackClick = { navController.popBackStack() },
                    onFavoriteToggle = { viewModel.toggleFavorite(it) }
                )
            }
        }
    }
}
