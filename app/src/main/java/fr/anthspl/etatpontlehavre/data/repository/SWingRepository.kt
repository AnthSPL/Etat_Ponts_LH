package com.example.etatpontlehavre.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.etatpontlehavre.data.model.Bridge
import com.example.etatpontlehavre.data.model.BridgeStatus
import com.example.etatpontlehavre.data.remote.SWingDataSource
import com.example.etatpontlehavre.notifications.BridgeNotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dépôt central pour l'accès aux données des ponts du Havre.
 */
class SWingRepository(
    private val context: Context,
    private val dataSource: SWingDataSource = SWingDataSource()
) {
    private val _bridgesFlow = MutableStateFlow<List<Bridge>>(emptyList())
    val bridgesFlow: StateFlow<List<Bridge>> = _bridgesFlow.asStateFlow()

    private val notificationHelper = BridgeNotificationHelper(context)

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("bridge_favorites_prefs", Context.MODE_PRIVATE)

    private val alertSharedPreferences: SharedPreferences =
        context.getSharedPreferences("bridge_alert_prefs", Context.MODE_PRIVATE)

    private val favoriteIds: MutableSet<String> = loadFavoritesFromPrefs()

    // Stocke les anciens statuts des ponts pour détecter les transitions
    private val previousStatuses = mutableMapOf<String, BridgeStatus>()

    private fun loadFavoritesFromPrefs(): MutableSet<String> {
        val savedSet = sharedPreferences.getStringSet("favorite_bridge_ids", emptySet()) ?: emptySet()
        return savedSet.toMutableSet()
    }

    private fun saveFavoritesToPrefs() {
        sharedPreferences.edit().putStringSet("favorite_bridge_ids", favoriteIds).apply()
    }

    suspend fun refreshBridges(): List<Bridge> {
        val remoteList = dataSource.fetchLeHavreBridges()

        val updatedList = remoteList.map { bridge ->
            val bridgeName = bridge.name
            val isFav = favoriteIds.contains(bridge.id) || favoriteIds.contains(bridgeName)
            val isClosed = bridge.status == BridgeStatus.CLOSED
            val wasClosedKey = "was_closed_$bridgeName"
            val wasPreviouslyClosed = alertSharedPreferences.getBoolean(wasClosedKey, false)
            val notifiedReopenedKey = "notified_reopened_$bridgeName"
            val alreadyNotifiedReopened = alertSharedPreferences.getBoolean(notifiedReopenedKey, false)

            if (isFav) {
                if (wasPreviouslyClosed && !isClosed) {
                    if (!alreadyNotifiedReopened) {
                        notificationHelper.sendNowOpenNotification(
                            bridgeName,
                            "Bonne nouvelle : $bridgeName est à nouveau ouvert à la circulation !"
                        )
                        alertSharedPreferences.edit()
                            .putBoolean(notifiedReopenedKey, true)
                            .putBoolean(wasClosedKey, false)
                            .apply()
                    }
                } else if (isClosed) {
                    alertSharedPreferences.edit()
                        .putBoolean(wasClosedKey, true)
                        .putBoolean(notifiedReopenedKey, false)
                        .apply()
                } else if (bridge.status == BridgeStatus.SOON_OPEN) {
                    notificationHelper.sendSoonOpenNotification(bridgeName)
                }
            }

            previousStatuses[bridge.id] = bridge.status

            bridge.copy(
                isFavorite = isFav,
                status = bridge.status
            )
        }

        _bridgesFlow.value = updatedList
        return updatedList
    }

    fun toggleFavorite(bridgeId: String) {
        if (favoriteIds.contains(bridgeId)) {
            favoriteIds.remove(bridgeId)
        } else {
            favoriteIds.add(bridgeId)
        }

        saveFavoritesToPrefs()

        _bridgesFlow.value = _bridgesFlow.value.map { bridge ->
            if (bridge.id == bridgeId) {
                bridge.copy(isFavorite = favoriteIds.contains(bridgeId))
            } else {
                bridge
            }
        }
    }

    fun getFavoriteBridgesNeedingAlert(): List<Bridge> {
        return _bridgesFlow.value.filter { bridge ->
            bridge.isFavorite && (
                bridge.status == BridgeStatus.SCHEDULED_CLOSURE ||
                    bridge.status == BridgeStatus.CLOSED ||
                    (bridge.minutesUntilNextClosure != null && bridge.minutesUntilNextClosure <= 15)
                )
        }
    }
}
