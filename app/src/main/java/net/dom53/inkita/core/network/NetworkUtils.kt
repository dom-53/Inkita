package net.dom53.inkita.core.network

import android.content.Context

object NetworkUtils {
    fun isOnline(context: Context): Boolean = NetworkMonitor.getInstance(context).isOnline()
}
