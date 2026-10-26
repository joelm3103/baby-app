package app.babylog.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * The real answer, from `ConnectivityManager`.
 *
 * `NET_CAPABILITY_INTERNET` is the capability that matters: a phone on a captive-portal Wi-Fi has a
 * network and still cannot reach Firestore, but that case ends as "the server did not answer", which is
 * what actually happened. `VALIDATED` would call that offline, and then the card would blame the parent's
 * connection for the hotel's.
 *
 * `ACCESS_NETWORK_STATE` is a normal permission — no runtime prompt, nothing to grant.
 */
class AndroidNetworkStatus(context: Context) : NetworkStatus {

    private val manager: ConnectivityManager? =
        context.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val active = manager?.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
