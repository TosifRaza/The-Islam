package com.ikp.dev.app.core.common;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.lifecycle.LiveData;

/**
 * Connectivity awareness (prompt §18/§19): LiveData<Boolean> for the offline
 * banner and repository offline fallbacks. Callback fires immediately with
 * the current state on registration.
 */
public final class NetworkMonitor extends LiveData<Boolean> {

    private final ConnectivityManager connectivityManager;
    private final ConnectivityManager.NetworkCallback callback;

    public NetworkMonitor(Context context) {
        this.connectivityManager = (ConnectivityManager)
                context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        this.callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@androidx.annotation.NonNull Network network) {
                postValue(true);
            }

            @Override
            public void onLost(@androidx.annotation.NonNull Network network) {
                postValue(isAnyNetworkConnected());
            }

            @Override
            public void onCapabilitiesChanged(@androidx.annotation.NonNull Network network,
                                              @androidx.annotation.NonNull NetworkCapabilities capabilities) {
                postValue(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
            }
        };
    }

    @Override
    protected void onActive() {
        super.onActive();
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(request, callback);
        } catch (RuntimeException ignored) {
            // Permission/system issue: treat as unknown → assume connected to
            // avoid blocking the UI; requests will fail into the offline path.
        }
        postValue(isAnyNetworkConnected());
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        try {
            connectivityManager.unregisterNetworkCallback(callback);
        } catch (RuntimeException ignored) {
            // never registered
        }
    }

    private boolean isAnyNetworkConnected() {
        if (connectivityManager == null) {
            return false;
        }
        Network[] networks = connectivityManager.getAllNetworks();
        for (Network n : networks) {
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(n);
            if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return true;
            }
        }
        return false;
    }
}
