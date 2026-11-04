package app.sentinela.trigger.quicktile

import android.annotation.SuppressLint
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import app.sentinela.R
import app.sentinela.trigger.TriggerActivity

/**
 * Atalho oficial no painel de configurações rápidas, para fabricantes que não
 * oferecem o mapeamento do botão lateral. Tocar no tile dispara a mesma
 * [TriggerActivity], de modo que a captura começa com o app em estado visível,
 * como o Android exige.
 */
@RequiresApi(Build.VERSION_CODES.N)
class SentinelaTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    // A sobrecarga com PendingIntent só existe na API 34+; para 26–33 a versão
    // com Intent (deprecada na 34) é a única disponível, por isso a supressão.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = TriggerActivity.launchIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.icon = Icon.createWithResource(this, R.drawable.ic_sentinela_notification)
        }
        tile.updateTile()
    }
}
