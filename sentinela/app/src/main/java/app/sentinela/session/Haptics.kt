package app.sentinela.session

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Convenção háptica. A vibração ocorre **somente depois** do evento real da API
 * de captura, nunca ao toque — "iniciou" significa que a gravação entrou em
 * estado ativo de verdade.
 *
 * - 1 pulso curto .................. captura confirmada
 * - 2 pulsos curtos ................ captura encerrada e arquivos finalizados
 * - 1 pulso longo + 2 curtos ....... falha ou sessão incompleta
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        }
        else -> @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    }

    fun confirmedStart() = play(longArrayOf(0, 60))
    fun stopped() = play(longArrayOf(0, 60, 90, 60))
    fun failure() = play(longArrayOf(0, 260, 120, 60, 90, 60))

    private fun play(timings: LongArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createWaveform(timings, -1)) }
    }
}
