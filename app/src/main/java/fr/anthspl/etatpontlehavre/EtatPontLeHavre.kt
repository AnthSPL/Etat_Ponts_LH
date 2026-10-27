package fr.anthspl.etatpontlehavre

import android.app.Application
import com.example.etatpontlehavre.notifications.BridgeAlertWorker

class EtatPontLeHavre : Application() {
    override fun onCreate() {
        super.onCreate()
        // Planifie la vérification périodique des ponts dès le lancement de l'application
        BridgeAlertWorker.schedulePeriodicCheck(this)
    }
}