package com.opencapture.pocketglance.connection

import android.view.Surface
import kotlinx.coroutines.flow.StateFlow

/**
 * One link path into the shared monitor pipeline. Wireless SoftAP and Wired USB AOA
 * both end at the same decoder surface owner.
 */
interface ConnectionTransport {
    val mode: ConnectionMode
    val state: StateFlow<ConnectionState>

    suspend fun connect()
    suspend fun disconnect()

    /** Bind the live decoder output surface when the transport owns a picture. */
    fun attachSurface(surface: Surface?)

    fun notePresented(sourceTimestampNs: Long) = Unit
}
