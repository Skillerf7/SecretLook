package app.secretlock.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.secretlock.ui.MainActivity

/** Wiederherstellungsweg: Kachel in den Schnelleinstellungen öffnet die Verwaltung, auch bei ausgeblendetem Symbol. */
class RecoveryTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Secret Lock"
            updateTile()
        }
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        val open = {
            startActivityAndCollapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        if (isLocked) unlockAndRun { open() } else open()
    }
}
