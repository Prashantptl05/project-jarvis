package com.example.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "DeviceTelemetry"

data class DeviceTelemetry(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val networkStatus: String = "SCANNING",
    val isOnline: Boolean = true,
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
    val androidVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
) {
    fun formattedLocalTime(): String {
        val formatter = SimpleDateFormat("EEE, dd MMM yyyy • HH:mm:ss", Locale.getDefault())
        return formatter.format(Date())
    }
}

class DeviceTelemetryManager(private val context: Context) {

    private val _telemetry = MutableStateFlow(readCurrentSnapshot())
    val telemetry: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var batteryReceiverRegistered = false
    private var networkCallbackRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else _telemetry.value.batteryPercent
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            _telemetry.value = _telemetry.value.copy(
                batteryPercent = pct.coerceIn(0, 100),
                isCharging = charging
            )
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshNetworkState()
        }

        override fun onLost(network: Network) {
            refreshNetworkState()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            refreshNetworkState()
        }
    }

    fun startMonitoring() {
        if (!batteryReceiverRegistered) {
            try {
                context.registerReceiver(
                    batteryReceiver,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
                batteryReceiverRegistered = true
            } catch (e: Exception) {
                Log.w(TAG, "Unable to register battery receiver", e)
            }
        }
        if (!networkCallbackRegistered) {
            try {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
                networkCallbackRegistered = true
            } catch (e: Exception) {
                Log.w(TAG, "Unable to register network callback", e)
            }
        }
        _telemetry.value = readCurrentSnapshot()
    }

    fun stopMonitoring() {
        if (batteryReceiverRegistered) {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {
            }
            batteryReceiverRegistered = false
        }
        if (networkCallbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {
            }
            networkCallbackRegistered = false
        }
    }

    fun readCurrentSnapshot(): DeviceTelemetry {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pctFromIntent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val pct = if (pctFromIntent >= 0) {
            pctFromIntent
        } else {
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 } ?: 100
        }
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val (netLabel, online) = readNetworkPair()
        return DeviceTelemetry(
            batteryPercent = pct.coerceIn(0, 100),
            isCharging = charging,
            networkStatus = netLabel,
            isOnline = online
        )
    }

    private fun refreshNetworkState() {
        val (label, online) = readNetworkPair()
        _telemetry.value = _telemetry.value.copy(
            networkStatus = label,
            isOnline = online
        )
    }

    private fun readNetworkPair(): Pair<String, Boolean> {
        return try {
            val active = connectivityManager.activeNetwork ?: return "OFFLINE" to false
            val caps = connectivityManager.getNetworkCapabilities(active) ?: return "OFFLINE" to false
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            when {
                !validated -> "OFFLINE" to false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI" to true
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR" to true
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET" to true
                else -> "LINKED" to true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query network capabilities", e)
            "UNKNOWN" to true
        }
    }

    fun triggerTacticalTimerAlert() {
        // Haptic alert pattern
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pattern = longArrayOf(0, 220, 120, 220, 120, 380)
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 220, 120, 220, 120, 380), -1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration alert unavailable", e)
        }

        // Acoustic tactical beep
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 90)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 650)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator alert unavailable", e)
        }
    }
}
