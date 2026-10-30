package fr.anthspl.etatpontlehavre.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.anthspl.etatpontlehavre.data.model.Bridge
import fr.anthspl.etatpontlehavre.data.model.BridgeStatus
import fr.anthspl.etatpontlehavre.ui.theme.EtatPontLeHavreTheme

@Composable
fun BridgeCard(
    bridge: Bridge,
    onBridgeClick: (Bridge) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = bridge.status.color,
        label = "statusColor"
    )

    // Mélange opaque uniforme (100% opaque) pour éviter les reflets de transparence
    val cardBackgroundColor = lerp(MaterialTheme.colorScheme.surface, statusColor, 0.12f)
    val cardBorderColor = lerp(MaterialTheme.colorScheme.surface, statusColor, 0.50f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onBridgeClick(bridge) },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, cardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBackgroundColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = bridge.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${bridge.code})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { onFavoriteToggle(bridge.id) }) {
                    Icon(
                        imageVector = if (bridge.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favori",
                        tint = if (bridge.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Badge Statut
            Surface(
                color = statusColor.copy(alpha = 0.15f),
                contentColor = statusColor,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bridge.status.label.uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = " • ${bridge.status.description}",
                        fontSize = 11.sp
                    )
                }
            }

            // Vrai bandeau d'alerte automatique (si fermeture imminente < 15 min basée sur les vraies heures)
            if (bridge.minutesUntilNextClosure != null && bridge.minutesUntilNextClosure <= 15) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFFFF3E0),
                    contentColor = Color(0xFFE65100),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = "Alerte",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFE65100)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚠️ Fermeture imminente dans ${bridge.minutesUntilNextClosure} min !",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        // Le bouton "Tester Alerte" a été entièrement supprimé d'ici.
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = bridge.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // On affiche la manœuvre si elle existe, sinon on n'affiche rien (ou la description)
                val nextManeuver = bridge.nextManeuvers.firstOrNull()
                if (nextManeuver != null) {
                    Text(
                        text = "Prochaine manœuvre : ${nextManeuver.startTime}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    // Espace vide pour garder l'alignement de la date de MàJ à droite
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Text(
                    text = "MàJ ${bridge.lastUpdated}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * Aperçu Compose (Preview) pour une carte de pont ouvert.
 */
@Preview(showBackground = true, name = "Carte Pont Ouvert")
@Composable
fun BridgeCardOpenPreview() {
    EtatPontLeHavreTheme {
        BridgeCard(
            bridge = Bridge(
                id = "pont_rouge",
                name = "Pont Rouge",
                code = "PR-76",
                description = "Pont levant reliant les Docks au Port - Bassin de l'Eure",
                status = BridgeStatus.OPEN,
                isFavorite = true,
                lastUpdated = "16:05"
            ),
            onBridgeClick = {},
            onFavoriteToggle = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

/**
 * Aperçu Compose (Preview) pour une carte de pont fermé avec alerte imminente.
 */
@Preview(showBackground = true, name = "Carte Pont Fermé")
@Composable
fun BridgeCardClosedPreview() {
    EtatPontLeHavreTheme {
        BridgeCard(
            bridge = Bridge(
                id = "pont_7bis",
                name = "Pont 7 bis",
                code = "P7B-07",
                description = "Pont levant du Canal de Tancarville",
                status = BridgeStatus.CLOSED,
                minutesUntilNextClosure = 8,
                isFavorite = false,
                lastUpdated = "16:10"
            ),
            onBridgeClick = {},
            onFavoriteToggle = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

/**
 * Aperçu Compose (Preview) pour une carte de pont lorsque l'appareil est hors-ligne.
 */
@Preview(showBackground = true, name = "Carte Appareil Hors-ligne")
@Composable
fun BridgeCardOfflinePreview() {
    EtatPontLeHavreTheme {
        BridgeCard(
            bridge = Bridge(
                id = "pont_7",
                name = "Pont 7",
                code = "LH-01",
                description = "Appareil hors-ligne",
                status = BridgeStatus.OFFLINE,
                isFavorite = false,
                lastUpdated = "10:20:04"
            ),
            onBridgeClick = {},
            onFavoriteToggle = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}