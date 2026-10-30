package fr.anthspl.etatpontlehavre.data.model

import androidx.compose.ui.graphics.Color

/**
 * Représente l'état courant de circulation d'un pont mobile du Havre selon la légende officielle S-WiNG.
 *
 * @property label Libellé textuel identique au site S-WiNG.
 * @property description Explication détaillée du statut.
 * @property colorHex Code couleur ARGB sous forme de Long.
 */
enum class BridgeStatus(
    val label: String,
    val description: String,
    val colorHex: Long
) {
    /** Code 0 sur S-WiNG : Ouvert aux véhicules (Vert). */
    OPEN("Ouvert aux véhicules", "Circulation libre", 0xFF2E7D32),

    /** Code 2 sur S-WiNG : Fermeture imminente (Orange/Jaune). */
    SCHEDULED_CLOSURE("Fermeture imminente", "Fermeture à la circulation très proche", 0xFFE65100),

    /** Code 3 sur S-WiNG : Bientôt ouvert aux véhicules (Bleu). */
    SOON_OPEN("Bientôt ouvert aux véhicules", "Manœuvre se terminant, réouverture imminente", 0xFF0288D1),

    /** Code 1 sur S-WiNG : Fermé (Rouge). */
    CLOSED("Fermé", "Pont levé / Circulation interrompue", 0xFFD32F2F),

    /** Hors-ligne / Perte de connexion réseau (Gris). */
    OFFLINE("Perte connexion réseau", "Attente réseau", 0xFF757575),

    /** Statut indisponible ou non communiqué (Gris). */
    UNKNOWN("Inconnu", "Statut indisponible", 0xFF757575);

    /**
     * Convertit le code couleur hexadecimal en instance [Color] de Compose.
     */
    val color: Color
        get() = Color(colorHex)
}

/**
 * Représente un horaire de manœuvre ou de fermeture programmée pour un pont.
 */
data class ManeuverSchedule(
    val id: String,
    val startTime: String,
    val endTime: String,
    val reason: String = "Passage de navire",
    val minutesUntilStart: Int? = null
)

/**
 * Modèle de données principal représentant un pont mobile de la zone portuaire du Havre.
 */
data class Bridge(
    val id: String,
    val name: String,
    val code: String,
    val description: String,
    val status: BridgeStatus,
    val minutesUntilNextClosure: Int? = null,
    val nextManeuvers: List<ManeuverSchedule> = emptyList(),
    val latitude: Double = 49.4900,
    val longitude: Double = 0.1200,
    val isFavorite: Boolean = false,
    val lastUpdated: String = "À l'instant"
)
