package fr.anthspl.etatpontlehavre.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import fr.anthspl.etatpontlehavre.data.model.Bridge
import fr.anthspl.etatpontlehavre.data.model.BridgeCoordinates
import fr.anthspl.etatpontlehavre.data.model.BridgeStatus
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun BridgeMapScreen(
    bridges: List<Bridge>,
    modifier: Modifier = Modifier,
    onBridgeClick: ((Bridge) -> Unit)? = null
) {
    val context = LocalContext.current
    val leHavrePort = GeoPoint(49.482, 0.122)

    Configuration.getInstance().userAgentValue = context.packageName

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(
                    XYTileSource(
                        "OpenTopoMap",
                        0, 17, 256, ".png", arrayOf(
                            "https://a.tile.opentopomap.org/",
                            "https://b.tile.opentopomap.org/",
                            "https://c.tile.opentopomap.org/"
                        )
                    )
                )
                setMultiTouchControls(true)
                controller.setZoom(13.5)
                controller.setCenter(leHavrePort)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()

            bridges.forEach { bridge ->
                val coords = BridgeCoordinates.map[bridge.name]

                if (coords != null) {
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(coords.first, coords.second)
                        title = bridge.name
                        snippet = "${bridge.description}\nMis à jour : ${bridge.lastUpdated}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                        // Choix de l'icône selon les 4 statuts S-WiNG
                        val drawableRes = when (bridge.status) {
                            BridgeStatus.OPEN -> android.R.drawable.presence_online       // Vert
                            BridgeStatus.CLOSED -> android.R.drawable.presence_busy       // Rouge
                            BridgeStatus.SOON_OPEN -> android.R.drawable.presence_invisible  // Bleu
                            BridgeStatus.SCHEDULED_CLOSURE -> android.R.drawable.presence_away // Orange
                            else -> android.R.drawable.presence_online
                        }

                        icon = ContextCompat.getDrawable(context, drawableRes)

                        setOnMarkerClickListener { clickedMarker, _ ->
                            clickedMarker.showInfoWindow()
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                }
            }
            mapView.invalidate()
        }
    )
}
