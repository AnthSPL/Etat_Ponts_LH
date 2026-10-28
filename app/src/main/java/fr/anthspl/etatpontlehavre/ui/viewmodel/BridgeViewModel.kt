package fr.anthspl.etatpontlehavre.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.anthspl.etatpontlehavre.data.model.Bridge
import fr.anthspl.etatpontlehavre.data.model.BridgeStatus
import fr.anthspl.etatpontlehavre.data.repository.SWingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Type de filtre sélectionnable dans la barre de puces de l'écran principal.
 *
 * @property label Libellé textuel affiché dans l'interface utilisateur.
 */
enum class FilterType(val label: String) {
    ALL("Tous"),
    FAVORITES("⭐ Favoris"),
    OPEN("🟢 Ouverts"),
    SOON_OPEN("🔵 Bientôt ouverts"),
    CLOSED("🔴 Fermés / Imminents")
}

/**
 * Représente l'état UI complet de l'écran principal des ponts.
 */
sealed interface BridgeUiState {
    /** Chargement initial des données en cours. */
    object Loading : BridgeUiState

    /**
     * Données chargées avec succès.
     */
    data class Success(
        val bridges: List<Bridge>,
        val filteredBridges: List<Bridge>,
        val selectedFilter: FilterType,
        val searchQuery: String,
        val openCount: Int,
        val totalCount: Int,
        val isRefreshing: Boolean = false
    ) : BridgeUiState

    /** Erreur survenue lors de la récupération des données S-Wing. */
    data class Error(val message: String) : BridgeUiState
}

/**
 * ViewModel principal gérant la logique de présentation et l'état de l'application "État des Ponts du Havre".
 *
 * @param application Contexte Android transmis à la classe mère [AndroidViewModel].
 */
class BridgeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SWingRepository(application)

    private val _uiState = MutableStateFlow<BridgeUiState>(BridgeUiState.Loading)

    /** Flux d'état UI observable par l'interface Jetpack Compose. */
    val uiState: StateFlow<BridgeUiState> = _uiState.asStateFlow()

    private var currentFilter = FilterType.ALL
    private var currentSearch = ""

    init {
        loadBridges()
    }

    /**
     * Charge ou rafraîchit la liste des ponts depuis le dépôt S-Wing.
     *
     * @param isPullToRefresh 'true' s'il s'agit d'une action utilisateur de rechargement manuel.
     */
    fun loadBridges(isPullToRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!isPullToRefresh && _uiState.value is BridgeUiState.Loading) {
                _uiState.value = BridgeUiState.Loading
            } else if (isPullToRefresh && _uiState.value is BridgeUiState.Success) {
                val current = _uiState.value as BridgeUiState.Success
                _uiState.value = current.copy(isRefreshing = true)
            }

            try {
                val bridges = repository.refreshBridges()
                updateSuccessState(bridges, isRefreshing = false)
            } catch (e: Exception) {
                _uiState.value = BridgeUiState.Error("Impossible de contacter S-Wing Haropa : ${e.localizedMessage}")
            }
        }
    }

    /**
     * Bascule le statut favori d'un pont et recalcule la liste affichée.
     *
     * @param bridgeId Identifiant du pont sélectionné.
     */
    fun toggleFavorite(bridgeId: String) {
        repository.toggleFavorite(bridgeId)
        viewModelScope.launch {
            val bridges = repository.bridgesFlow.value
            updateSuccessState(bridges)
        }
    }

    /**
     * Applique un nouveau filtre de catégorie.
     *
     * @param filter Filtre à appliquer.
     */
    fun setFilter(filter: FilterType) {
        currentFilter = filter
        if (_uiState.value is BridgeUiState.Success) {
            val bridges = repository.bridgesFlow.value
            updateSuccessState(bridges)
        }
    }

    /**
     * Met à jour le mot-clé de recherche pour filtrer les ponts par nom ou code.
     *
     * @param query Terme recherché.
     */
    fun setSearchQuery(query: String) {
        currentSearch = query
        if (_uiState.value is BridgeUiState.Success) {
            val bridges = repository.bridgesFlow.value
            updateSuccessState(bridges)
        }
    }

    /**
     * Calcule le nouvel état [BridgeUiState.Success] en appliquant les critères de recherche et de filtrage.
     */
    private fun updateSuccessState(bridges: List<Bridge>, isRefreshing: Boolean = false) {
        val filtered = bridges.filter { bridge ->
            val matchesFilter = when (currentFilter) {
                FilterType.ALL -> true
                FilterType.FAVORITES -> bridge.isFavorite
                FilterType.OPEN -> bridge.status == BridgeStatus.OPEN
                FilterType.SOON_OPEN -> bridge.status == BridgeStatus.SOON_OPEN
                FilterType.CLOSED -> bridge.status == BridgeStatus.CLOSED ||
                    bridge.status == BridgeStatus.SCHEDULED_CLOSURE
            }

            val matchesSearch = currentSearch.isEmpty() ||
                bridge.name.contains(currentSearch, ignoreCase = true) ||
                bridge.code.contains(currentSearch, ignoreCase = true) ||
                bridge.description.contains(currentSearch, ignoreCase = true)

            matchesFilter && matchesSearch
        }

        val openCount = bridges.count { it.status == BridgeStatus.OPEN }

        _uiState.value = BridgeUiState.Success(
            bridges = bridges,
            filteredBridges = filtered,
            selectedFilter = currentFilter,
            searchQuery = currentSearch,
            openCount = openCount,
            totalCount = bridges.size,
            isRefreshing = isRefreshing
        )
    }
}
