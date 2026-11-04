package app.sentinela.core.logging

import android.util.Log

/** Wrapper fino para facilitar testes e padronizar a tag. */
object SLog {
    private const val TAG = "Sentinela"

    fun i(message: String) { Log.i(TAG, message) }
    fun w(message: String, t: Throwable? = null) { Log.w(TAG, message, t) }
    fun e(message: String, t: Throwable? = null) { Log.e(TAG, message, t) }
}
