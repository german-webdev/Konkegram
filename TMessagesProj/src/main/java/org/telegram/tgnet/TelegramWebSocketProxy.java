/*
 * This is the source code of Telegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package org.telegram.tgnet;

import android.app.Activity;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.security.SecureRandom;

/** Runs the in-process MTProto-to-WebSocket bridge used by DPI Bypass. */
public final class TelegramWebSocketProxy {
    private static final String PREF_ENABLED = "dpiBypassEnabled";
    private static final String PREF_CLOUDFLARE_FALLBACK = "dpiBypassCloudflareFallback";
    private static final String PREF_SECRET = "dpiBypassSecret";
    private static final String HOST = "127.0.0.1";
    private static final int FIRST_PORT = 1443;
    private static final int PORT_ATTEMPTS = 8;
    private static final long UNKNOWN_NETWORK = Long.MIN_VALUE;
    private static final long NETWORK_RESTART_DELAY_MS = 1200;
    private static final Object networkLock = new Object();

    private static volatile boolean enabled;
    private static volatile boolean cloudflareFallback;
    private static boolean running;
    private static int port;
    private static String secret;
    private static long lastNetworkId = UNKNOWN_NETWORK;
    private static boolean lastNetworkOnline;
    private static boolean networkRestartRunning;

    private static final Runnable networkRestartRunnable = () -> {
        synchronized (networkLock) {
            if (networkRestartRunning || !enabled) {
                return;
            }
            networkRestartRunning = true;
        }
        new Thread(() -> {
            try {
                restartAfterNetworkChange();
            } finally {
                synchronized (networkLock) {
                    networkRestartRunning = false;
                }
            }
        }, "TgWsNetworkRestart").start();
    };

    private TelegramWebSocketProxy() {
    }

    public static synchronized void initialize() {
        SharedPreferences preferences = preferences();
        secret = getOrCreateSecret(preferences);
        boolean defaultEnabled = !preferences.getBoolean("proxy_enabled", false);
        enabled = preferences.getBoolean(PREF_ENABLED, defaultEnabled);
        cloudflareFallback = preferences.getBoolean(PREF_CLOUDFLARE_FALLBACK, true);
        if (!preferences.contains(PREF_ENABLED) || !preferences.contains(PREF_CLOUDFLARE_FALLBACK)) {
            preferences.edit()
                    .putBoolean(PREF_ENABLED, enabled)
                    .putBoolean(PREF_CLOUDFLARE_FALLBACK, cloudflareFallback)
                    .apply();
        }
        if (enabled && !start()) {
            enabled = false;
            preferences.edit().putBoolean(PREF_ENABLED, false).apply();
        }
    }

    public static synchronized boolean setEnabled(boolean value) {
        if (value == enabled && (!value || running)) {
            return true;
        }

        if (value) {
            secret = getOrCreateSecret(preferences());
            if (!start()) {
                enabled = false;
                preferences().edit().putBoolean(PREF_ENABLED, false).apply();
                return false;
            }
            enabled = true;
            preferences().edit().putBoolean(PREF_ENABLED, true).apply();
            ConnectionsManager.refreshProxySettings();
        } else {
            enabled = false;
            preferences().edit().putBoolean(PREF_ENABLED, false).apply();
            ConnectionsManager.refreshProxySettings();
            stop();
        }
        return true;
    }

    public static synchronized boolean isEnabled() {
        return enabled;
    }

    public static synchronized boolean isCloudflareFallbackEnabled() {
        return cloudflareFallback;
    }

    public static synchronized boolean setCloudflareFallbackEnabled(boolean value) {
        if (value == cloudflareFallback) {
            return true;
        }
        boolean previousValue = cloudflareFallback;
        cloudflareFallback = value;
        if (enabled) {
            stop();
            if (!start()) {
                cloudflareFallback = previousValue;
                if (!start()) {
                    enabled = false;
                    preferences().edit().putBoolean(PREF_ENABLED, false).apply();
                }
                ConnectionsManager.refreshProxySettings();
                return false;
            }
            ConnectionsManager.refreshProxySettings();
        }
        preferences().edit().putBoolean(PREF_CLOUDFLARE_FALLBACK, value).apply();
        return true;
    }

    static synchronized boolean isReady() {
        return enabled && running;
    }

    static synchronized String getHost() {
        return HOST;
    }

    static synchronized int getPort() {
        return port;
    }

    static synchronized String getProxySecret() {
        return "dd" + secret;
    }

    public static synchronized String getStats() {
        return running ? ConnectionsManager.native_getWebSocketProxyStats() : "";
    }

    public static void onNetworkChanged(long networkId, boolean online) {
        synchronized (networkLock) {
            if (networkId == lastNetworkId && online == lastNetworkOnline) {
                return;
            }
            boolean hadKnownNetwork = lastNetworkId != UNKNOWN_NETWORK;
            boolean networkBecameReady = online && (!lastNetworkOnline || networkId != lastNetworkId);
            lastNetworkId = networkId;
            lastNetworkOnline = online;
            if (ApplicationLoader.applicationHandler == null) {
                return;
            }
            ApplicationLoader.applicationHandler.removeCallbacks(networkRestartRunnable);
            if (hadKnownNetwork && enabled && networkBecameReady) {
                ApplicationLoader.applicationHandler.postDelayed(networkRestartRunnable, NETWORK_RESTART_DELAY_MS);
            }
        }
    }

    private static synchronized void restartAfterNetworkChange() {
        if (!enabled) {
            return;
        }
        FileLog.d("DPI Bypass: active network changed, restarting WebSocket bridge");
        stop();
        if (!start()) {
            enabled = false;
            preferences().edit().putBoolean(PREF_ENABLED, false).apply();
            FileLog.e("DPI Bypass: WebSocket bridge could not restart after network change");
        }
        ApplicationLoader.applicationHandler.post(ConnectionsManager::refreshProxySettings);
    }

    private static boolean start() {
        if (running) {
            return true;
        }
        File cacheDir = new File(ApplicationLoader.applicationContext.getCacheDir(), "tgwsproxy");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            FileLog.e("DPI Bypass: could not create cache directory");
        }
        for (int candidate = FIRST_PORT; candidate < FIRST_PORT + PORT_ATTEMPTS; candidate++) {
            int result = ConnectionsManager.native_startWebSocketProxy(
                    candidate,
                    cacheDir.getAbsolutePath(),
                    secret,
                    cloudflareFallback,
                    BuildVars.LOGS_ENABLED
            );
            if (result == 0) {
                port = candidate;
                running = true;
                FileLog.d("DPI Bypass: WebSocket bridge listening on " + HOST + ":" + port);
                return true;
            }
            if (result != -3) {
                FileLog.e("DPI Bypass: WebSocket bridge failed with code " + result);
                break;
            }
        }
        return false;
    }

    private static void stop() {
        if (!running) {
            return;
        }
        ConnectionsManager.native_stopWebSocketProxy();
        running = false;
        port = 0;
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
    }

    private static String getOrCreateSecret(SharedPreferences preferences) {
        String value = preferences.getString(PREF_SECRET, "");
        if (value != null && value.matches("[0-9a-fA-F]{32}")) {
            return value.toLowerCase();
        }
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder result = new StringBuilder(32);
        for (byte b : bytes) {
            result.append(String.format("%02x", b & 0xff));
        }
        value = result.toString();
        preferences.edit().putString(PREF_SECRET, value).apply();
        return value;
    }
}
