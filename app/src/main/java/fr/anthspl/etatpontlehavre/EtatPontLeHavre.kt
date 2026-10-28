package fr.anthspl.etatpontlehavre

import android.app.Application
import fr.anthspl.etatpontlehavre.notifications.BridgeAlertWorker

class EtatPontLeHavre : Application() {
    override fun onCreate() {
        super.onCreate()
        // Planifie la vérification périodique des ponts dès le lancement de l'application
        BridgeAlertWorker.schedulePeriodicCheck(this)
    }
}