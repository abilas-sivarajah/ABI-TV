package de.aniworld.tv.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log

object NsdHelper {

    private const val TAG = "NsdHelper"
    const val SERVICE_TYPE = "_aniworldtv._tcp."

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun register(context: Context, serviceName: String, port: Int) {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return

        // Multicast-Lock aktivieren, damit mDNS im WLAN nicht vom Standby geblockt wird
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("AniWorldTvNsdLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MulticastLock konnte nicht aktiviert werden", e)
        }

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo?) {
                Log.i(TAG, "NSD Service erfolgreich registriert: ${info?.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "NSD Registrierung fehlgeschlagen: Code $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo?) {
                Log.i(TAG, "NSD Service abgemeldet")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "NSD Abmeldung fehlgeschlagen: Code $errorCode")
            }
        }

        registrationListener = listener

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Aufruf von registerService", e)
        }
    }

    fun unregister(context: Context) {
        val listener = registrationListener ?: return
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        try {
            nsdManager?.unregisterService(listener)
        } catch (e: Exception) {
            Log.w(TAG, "Fehler beim Abmelden des NSD Service", e)
        }
        registrationListener = null

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
        multicastLock = null
    }
}
