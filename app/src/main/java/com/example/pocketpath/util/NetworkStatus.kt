package com.example.pocketpath.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

// checks whether the phone can reach the internet before loading from firebase
object NetworkStatus {

    // true when the active network says it has internet access
    // with no connection firestore quietly returns an empty cache instead of
    // failing, which would show a misleading score of zero, so screens check
    // this first and show an error instead
    fun isOnline(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
