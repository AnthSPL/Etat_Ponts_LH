package fr.anthspl.etatpontlehavre.data.repository

import android.util.Log
import fr.anthspl.etatpontlehavre.data.remote.MeteoFranceApi

class VigilanceRepository(private val api: MeteoFranceApi) {

    suspend fun isWindAlertOrangeOrRedForDept76(): Boolean {
        return try {
            val response = api.getGlobalVigilance()

            val periods = response.product?.periods
            if (periods.isNullOrEmpty()) {
                Log.w("VigilanceRepository", "⚠️ Aucune période de vigilance trouvée dans la réponse !")
                return false
            }

            var maxWindColorId = 1

            for (period in periods) {
                val domainItems = period.timelaps?.domainIds ?: continue

                // Recherche spécifique du domaine "7610" (Seine-Maritime)
                val dept76 = domainItems.find { it.domainId == "7610" }

                if (dept76 != null) {
                    // Phénomène "2" = Vent violent (ou "1" selon les déclinaisons de la nomenclature)
                    val windPhenomenon = dept76.phenomenonItems?.find {
                        it.phenomenonId == "2" || it.phenomenonId == "1"
                    }

                    val colorId = windPhenomenon?.phenomenonMaxColorId ?: dept76.maxColorId ?: 1
                    if (colorId > maxWindColorId) {
                        maxWindColorId = colorId
                    }
                }
            }

            Log.i("VigilanceRepository", "Seine-Maritime (7610) - Niveau max de vigilance vent détecté : $maxWindColorId (Seuil orange = 3)")

            // Niveau 3 = Orange, Niveau 4 = Rouge
            maxWindColorId >= 3

        } catch (e: Exception) {
            Log.e("VigilanceRepository", "❌ Erreur lors de l'appel Météo-France", e)
            false
        }
    }
}