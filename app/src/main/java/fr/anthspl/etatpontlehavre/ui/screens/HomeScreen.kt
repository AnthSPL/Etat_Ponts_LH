package fr.anthspl.etatpontlehavre.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import fr.anthspl.etatpontlehavre.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.anthspl.etatpontlehavre.data.model.Bridge
import fr.anthspl.etatpontlehavre.data.model.BridgeStatus
import fr.anthspl.etatpontlehavre.ui.components.BridgeCard
import fr.anthspl.etatpontlehavre.ui.components.BridgeMapScreen
import fr.anthspl.etatpontlehavre.ui.components.FilterChipsRow
import fr.anthspl.etatpontlehavre.ui.components.WelcomeDisclaimerDialog
import fr.anthspl.etatpontlehavre.ui.components.shouldShowDisclaimer
import fr.anthspl.etatpontlehavre.ui.theme.EtatPontLeHavreTheme
import fr.anthspl.etatpontlehavre.ui.viewmodel.BridgeUiState
import fr.anthspl.etatpontlehavre.ui.viewmodel.BridgeViewModel
import fr.anthspl.etatpontlehavre.ui.viewmodel.FilterType

enum class AppTab(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val label: String
) {
    LIST(
        title = "Ponts du Havre",
        subtitle = "Données Haropa Port / S-Wing",
        icon = Icons.AutoMirrored.Filled.List,
        label = "Liste"
    ),
    MAP(
        title = "Carte des Ponts",
        subtitle = "Localisation des ponts du Havre",
        icon = Icons.Default.Place,
        label = "Carte"
    ),
    SETTINGS(
        title = "Paramètres",
        subtitle = "Fréquence, autorisations & infos",
        icon = Icons.Default.SettingsIcon,
        label = "Paramètres"
    )
}

@Composable
fun HomeScreen(
    viewModel: BridgeViewModel,
    onBridgeClick: (Bridge) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    HomeScreenContent(
        uiState = uiState,
        onBridgeClick = onBridgeClick,
        onRefresh = { viewModel.loadBridges(isPullToRefresh = true) },
        onToggleFavorite = { viewModel.toggleFavorite(it) },
        onFilterSelected = { viewModel.setFilter(it) },
        onSearchQueryChange = { viewModel.setSearchQuery(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    uiState: BridgeUiState,
    onBridgeClick: (Bridge) -> Unit,
    onRefresh: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onFilterSelected: (FilterType) -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(AppTab.LIST) }

    // Gestion de l'affichage du Pop-Up d'Avertissement & Informations au démarrage
    var showDisclaimerDialog by remember { mutableStateOf(shouldShowDisclaimer(context)) }

    if (showDisclaimerDialog) {
        WelcomeDisclaimerDialog(
            onDismiss = { showDisclaimerDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = selectedTab.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = selectedTab.subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Bouton de rafraîchissement (sur les onglets Liste et Carte)
                    if (selectedTab == AppTab.LIST || selectedTab == AppTab.MAP) {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Rafraîchir"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = (selectedTab == tab),
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(text = tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AppTab.LIST -> {
                    // --- ONGLET 1 : LISTE DES PONTS ---
                    when (uiState) {
                        is BridgeUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is BridgeUiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        is BridgeUiState.Success -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Bandeau Synthèse d'Ouverture & Alertes
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.DirectionsCar,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "${uiState.openCount} / ${uiState.totalCount} Ponts ouverts",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    text = "Alertes GPS & surveillances en direct",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                )
                                            }
                                        }

                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_bridge),
                                            contentDescription = "Pont",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Recherche
                                OutlinedTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = onSearchQueryChange,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    placeholder = { Text("Rechercher un pont (ex: Rouge, 7, Hode...)") },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors()
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Filtres rapides
                                FilterChipsRow(
                                    selectedFilter = uiState.selectedFilter,
                                    onFilterSelected = onFilterSelected
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Indicateur de chargement discret lors du rafraîchissement
                                AnimatedVisibility(visible = uiState.isRefreshing) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                                    }
                                }

                                // Liste des cartes de ponts
                                if (uiState.filteredBridges.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Aucun pont ne correspond aux critères.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(
                                            items = uiState.filteredBridges,
                                            key = { it.id }
                                        ) { bridge ->
                                            BridgeCard(
                                                bridge = bridge,
                                                onBridgeClick = onBridgeClick,
                                                onFavoriteToggle = onToggleFavorite
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AppTab.MAP -> {
                    // --- ONGLET 2 : CARTE DES PONTS ---
                    val bridges = (uiState as? BridgeUiState.Success)?.filteredBridges ?: emptyList()
                    BridgeMapScreen(
                        bridges = bridges,
                        modifier = Modifier.fillMaxSize(),
                        onBridgeClick = onBridgeClick
                    )
                }

                AppTab.SETTINGS -> {
                    // --- ONGLET 3 : PARAMÈTRES & INFOS ---
                    SettingsScreen(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Aperçu Compose (Preview) pour l'écran d'accueil en état de succès.
 */
@Preview(showBackground = true, showSystemUi = true, name = "Liste - Succès")
@Composable
fun HomeScreenSuccessPreview() {
    val dummyBridges = listOf(
        Bridge(
            id = "pont_rouge",
            name = "Pont Rouge",
            code = "PR-76",
            description = "Pont levant reliant les Docks au Port",
            status = BridgeStatus.OPEN,
            isFavorite = true,
            lastUpdated = "16:05"
        ),
        Bridge(
            id = "pont_v",
            name = "Pont V",
            code = "PV-05",
            description = "Pont tournant du Bassin Bellot",
            status = BridgeStatus.SOON_OPEN,
            isFavorite = true,
            lastUpdated = "16:05"
        ),
        Bridge(
            id = "pont_7bis",
            name = "Pont 7 bis",
            code = "P7B-07",
            description = "Pont levant du Canal de Tancarville",
            status = BridgeStatus.CLOSED,
            isFavorite = false,
            lastUpdated = "16:05"
        )
    )

    EtatPontLeHavreTheme {
        HomeScreenContent(
            uiState = BridgeUiState.Success(
                bridges = dummyBridges,
                filteredBridges = dummyBridges,
                selectedFilter = FilterType.ALL,
                searchQuery = "",
                openCount = 1,
                totalCount = 3,
                isRefreshing = false
            ),
            onBridgeClick = {},
            onRefresh = {},
            onToggleFavorite = {},
            onFilterSelected = {},
            onSearchQueryChange = {}
        )
    }
}

/**
 * Aperçu Compose (Preview) pour l'écran d'accueil en chargement.
 */
@Preview(showBackground = true, showSystemUi = true, name = "Liste - Chargement")
@Composable
fun HomeScreenLoadingPreview() {
    EtatPontLeHavreTheme {
        HomeScreenContent(
            uiState = BridgeUiState.Loading,
            onBridgeClick = {},
            onRefresh = {},
            onToggleFavorite = {},
            onFilterSelected = {},
            onSearchQueryChange = {}
        )
    }
}

/**
 * Aperçu Compose (Preview) pour l'écran d'accueil en cas d'erreur.
 */
@Preview(showBackground = true, showSystemUi = true, name = "Liste - Erreur")
@Composable
fun HomeScreenErrorPreview() {
    EtatPontLeHavreTheme {
        HomeScreenContent(
            uiState = BridgeUiState.Error("Impossible de se connecter au serveur Haropa Port S-WING. Vérifiez votre connexion."),
            onBridgeClick = {},
            onRefresh = {},
            onToggleFavorite = {},
            onFilterSelected = {},
            onSearchQueryChange = {}
        )
    }
}
