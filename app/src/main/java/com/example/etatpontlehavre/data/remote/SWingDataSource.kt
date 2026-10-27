package com.example.etatpontlehavre.data.remote

import android.util.Log
import com.example.etatpontlehavre.data.model.Bridge
import com.example.etatpontlehavre.data.model.BridgeCoordinates
import com.example.etatpontlehavre.data.model.BridgeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SWING_URL = "https://www.havre-port.com/ponts"

/**
 * Source de données distante responsable de l'interaction avec l'API Haropa Port / S-Wing.
 */
class SWingDataSource {

    /**
     * Effectue une requête réseau pour récupérer la liste actualisée des ponts du Havre.
     */
    suspend fun fetchLeHavreBridges(): List<Bridge> = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())

        try {
            // 1. Chargement de la page HTML S-WiNG pour récupérer l'URL dynamique relPonts
            val doc = Jsoup.connect(SWING_URL)
                .timeout(10000)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()

            val pontsDiv = doc.select("#ponts")
            val relPontsAttr = pontsDiv.attr("relPonts")

            val jsonUrl = if (relPontsAttr.startsWith("http")) {
                relPontsAttr
            } else {
                "https://www.havre-port.com$relPontsAttr"
            }

            // 2. Requête vers l'API JSON /map/getPonts
            val jsonResponse = Jsoup.connect(jsonUrl)
                .ignoreContentType(true)
                .timeout(10000)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .execute()
                .body()

            val jsonObject = JSONObject(jsonResponse)
            val dataObj = jsonObject.optJSONObject("data")

            val scrapedBridges = mutableListOf<Bridge>()

            if (dataObj != null) {
                val keys = dataObj.keys()
                var index = 0
                while (keys.hasNext()) {
                    val key = keys.next()
                    val bridgeObj = dataObj.getJSONObject(key)

                    val rawName = bridgeObj.optString("nom", "Pont $index")
                    val name = cleanBridgeName(rawName)
                    val statutCode = bridgeObj.optInt("statut", 0)
                    val statutText = bridgeObj.optString("statutText", "Ouvert aux véhicules")
                    val dateStr = bridgeObj.optString("date", "")

                    // Mappage fidèle des 4 codes couleurs officiels S-WiNG :
                    // 0 -> Ouvert aux véhicules (Vert)
                    // 1 -> Fermé (Rouge)
                    // 2 -> Fermeture imminente (Orange)
                    // 3 -> Bientôt ouvert aux véhicules (Bleu)
                    val status = when (statutCode) {
                        0 -> BridgeStatus.OPEN
                        1 -> BridgeStatus.CLOSED
                        2 -> BridgeStatus.SCHEDULED_CLOSURE
                        3 -> BridgeStatus.SOON_OPEN
                        else -> BridgeStatus.OPEN
                    }

                    val description = if (dateStr.isNotBlank()) "Depuis le $dateStr" else statutText
                    val coords = BridgeCoordinates.map[name] ?: Pair(49.482, 0.122)

                    scrapedBridges.add(
                        Bridge(
                            id = "bridge_$key",
                            name = name,
                            code = "LH-0${index + 1}",
                            description = description,
                            status = status,
                            minutesUntilNextClosure = null,
                            nextManeuvers = emptyList(),
                            latitude = coords.first,
                            longitude = coords.second,
                            isFavorite = false,
                            lastUpdated = timeStamp
                        )
                    )
                    index++
                }
            }

            if (scrapedBridges.isNotEmpty()) {
                ensureAllBridgesPresent(scrapedBridges, timeStamp)
                scrapedBridges
            } else {
                Log.w("SWingDataSource", "JSON vide, utilisation du fallback")
                getCleanFallbackBridges(timeStamp)
            }

        } catch (e: Exception) {
            Log.e("SWingDataSource", "Erreur lors de la récupération JSON : ${e.message}", e)
            getCleanFallbackBridges(timeStamp)
        }
    }

    private fun cleanBridgeName(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("7 bis") -> "Pont 7 bis"
            lower.contains("rouge") -> "Pont rouge"
            lower.contains("7") -> "Pont 7"
            lower.contains("8") -> "Pont 8"
            lower.contains("6") -> "Pont 6"
            lower.contains("5") -> "Pont 5"
            lower.contains("hode") -> "Pont du Hode"
            lower.contains("eure") -> "Pont de l'Eure"
            lower.contains("fr1") && lower.contains("amont") -> "Pont amont FR1"
            lower.contains("fr1") && lower.contains("aval") -> "Pont aval FR1"
            lower.contains("quinette") && lower.contains("amont") -> "Pont amont Quinette"
            lower.contains("quinette") && lower.contains("aval") -> "Pont aval Quinette"
            lower.contains("vétillart") && lower.contains("amont") -> "Pont amont vétillart"
            lower.contains("vétillart") && lower.contains("aval") -> "Pont aval vétillart"
            else -> raw
        }
    }

    private fun ensureAllBridgesPresent(scraped: MutableList<Bridge>, timeStamp: String) {
        val allOfficialNames = BridgeCoordinates.map.keys
        for (officialName in allOfficialNames) {
            if (scraped.none { it.name.equals(officialName, ignoreCase = true) }) {
                val coords = BridgeCoordinates.map[officialName]!!
                scraped.add(
                    Bridge(
                        id = "bridge_off_${officialName.hashCode()}",
                        name = officialName,
                        code = "LH-XX",
                        description = "Synchronisé Haropa Port",
                        status = BridgeStatus.OPEN,
                        minutesUntilNextClosure = null,
                        nextManeuvers = emptyList(),
                        latitude = coords.first,
                        longitude = coords.second,
                        isFavorite = false,
                        lastUpdated = timeStamp
                    )
                )
            }
        }
    }

    private fun getCleanFallbackBridges(timeStamp: String): List<Bridge> {
        return BridgeCoordinates.map.map { (name, coords) ->
            Bridge(
                id = "fallback_${name.hashCode()}",
                name = name,
                code = "LH-01",
                description = "Mode hors-ligne",
                status = BridgeStatus.OPEN,
                minutesUntilNextClosure = null,
                nextManeuvers = emptyList(),
                latitude = coords.first,
                longitude = coords.second,
                isFavorite = false,
                lastUpdated = timeStamp
            )
        }.toList()
    }
}
