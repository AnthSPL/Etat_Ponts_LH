package fr.anthspl.etatpontlehavre.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import fr.anthspl.etatpontlehavre.R
import fr.anthspl.etatpontlehavre.data.model.Bridge
import fr.anthspl.etatpontlehavre.data.model.BridgeStatus
import fr.anthspl.etatpontlehavre.data.remote.SWingDataSource
import kotlinx.coroutines.runBlocking

class FavoriteBridgesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return FavoriteBridgesWidgetFactory(applicationContext)
    }
}

class FavoriteBridgesWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var favoriteBridges: List<Bridge> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        runBlocking {
            try {
                val favPrefs = context.getSharedPreferences("bridge_favorites_prefs", Context.MODE_PRIVATE)
                val favoriteIds = favPrefs.getStringSet("favorite_bridge_ids", emptySet()) ?: emptySet()

                val dataSource = SWingDataSource()
                val allBridges = dataSource.fetchLeHavreBridges()

                favoriteBridges = allBridges.filter { bridge ->
                    favoriteIds.contains(bridge.id) || favoriteIds.contains(bridge.name)
                }
            } catch (e: Exception) {
                favoriteBridges = emptyList()
            }
        }
    }

    override fun onDestroy() {
        favoriteBridges = emptyList()
    }

    override fun getCount(): Int = favoriteBridges.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= favoriteBridges.size) {
            return RemoteViews(context.packageName, R.layout.widget_favorite_bridge_item)
        }

        val bridge = favoriteBridges[position]
        val views = RemoteViews(context.packageName, R.layout.widget_favorite_bridge_item)

        views.setTextViewText(R.id.widget_item_bridge_name, bridge.name)
        views.setTextViewText(R.id.widget_item_last_updated, "MàJ ${bridge.lastUpdated}")

        val statusText = "${bridge.status.label.uppercase()} • ${bridge.status.description}"
        views.setTextViewText(R.id.widget_item_status_label, statusText)

        // Arrière-plan dynamique selon la couleur du statut du pont
        val bgRes = when (bridge.status) {
            BridgeStatus.OPEN -> R.drawable.widget_item_bg_open
            BridgeStatus.CLOSED -> R.drawable.widget_item_bg_closed
            BridgeStatus.SOON_OPEN -> R.drawable.widget_item_bg_soon_open
            BridgeStatus.SCHEDULED_CLOSURE -> R.drawable.widget_item_bg_scheduled
            BridgeStatus.OFFLINE, BridgeStatus.UNKNOWN -> R.drawable.widget_item_bg_offline
        }

        val textColor = when (bridge.status) {
            BridgeStatus.OPEN -> 0xFF2E7D32.toInt()
            BridgeStatus.CLOSED -> 0xFFD32F2F.toInt()
            BridgeStatus.SOON_OPEN -> 0xFF0288D1.toInt()
            BridgeStatus.SCHEDULED_CLOSURE -> 0xFFE65100.toInt()
            BridgeStatus.OFFLINE, BridgeStatus.UNKNOWN -> 0xFF616161.toInt()
        }

        views.setInt(R.id.widget_item_container, "setBackgroundResource", bgRes)
        views.setTextColor(R.id.widget_item_status_label, textColor)

        // Clic sur un pont ouvre l'application
        val fillInIntent = Intent().apply {
            putExtra("bridge_id", bridge.id)
        }
        views.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}