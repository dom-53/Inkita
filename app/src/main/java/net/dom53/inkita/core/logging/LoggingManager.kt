package net.dom53.inkita.core.logging

import android.util.Log

/**
 * Thin logging wrapper to keep tags/format consistent and allow easy disabling.
 * Verbose/debug logs are emitted only when enabled; info/warn/error follow the same flag to avoid leaking secrets.
 */
object LoggingManager {
    private var enabled: Boolean = true
    private var errorsEnabled: Boolean = true

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun setErrorsEnabled(value: Boolean) {
        errorsEnabled = value
    }

    fun d(
        tag: String,
        msg: String,
    ) {
        if (!enabled) return
        Log.d(tag, msg)
    }

    fun i(
        tag: String,
        msg: String,
    ) {
        if (!enabled) return
        Log.i(tag, msg)
    }

    fun w(
        tag: String,
        msg: String,
        tr: Throwable? = null,
    ) {
        if (!enabled && !errorsEnabled) return
        if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
    }

    fun e(
        tag: String,
        msg: String,
        tr: Throwable? = null,
    ) {
        if (!errorsEnabled) return
        if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
    }
}
