package com.okur.reader.share

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Yakındaki paylaşımın çalışması için cihaz koşulları.
 * [required] olanlar olmadan keşif çalışmaz; diğerleri bazı cihazlarda bulunmayı kolaylaştırır.
 */
data class DiagnosticItem(
    val label: String,
    val ok: Boolean,
    val detail: String,
    val required: Boolean,
    val fix: Intent? = null,
    val fixLabel: String? = null,
)

object NearbyDiagnostics {

    @SuppressLint("MissingPermission")
    fun check(context: Context): List<DiagnosticItem> {
        val items = mutableListOf<DiagnosticItem>()

        val play = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        items += DiagnosticItem(
            "Google Play Hizmetleri", play,
            if (play) "Hazır" else "Yakındaki paylaşım Google Play Hizmetleri gerektirir; güncel değil veya bu cihazda yok.",
            required = true,
        )

        items += DiagnosticItem(
            "İzinler", NearbyPermissions.granted(context),
            if (NearbyPermissions.granted(context)) "Verildi" else "Yakındaki cihazlar ve konum izni gerekli",
            required = true,
            fix = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)),
            fixLabel = "İzin ver",
        )

        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val btOn = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
        items += DiagnosticItem(
            "Bluetooth", btOn,
            when {
                adapter == null -> "Bu cihazda Bluetooth yok"
                btOn -> "Açık"
                else -> "Kapalı — cihazları bulmak için açılmalı"
            },
            required = true,
            fix = Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            fixLabel = "Aç",
        )

        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val wifiOn = wifi?.isWifiEnabled == true
        items += DiagnosticItem(
            "Wi-Fi", wifiOn,
            if (wifiOn) "Açık (bir ağa bağlı olmak gerekmez)" else "Kapalı — hızlı aktarım ve keşif için açık olmalı",
            required = false,
            fix = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS),
            fixLabel = "Aç",
        )

        val lm = context.getSystemService(LocationManager::class.java)
        val locOn = lm != null && LocationManagerCompat.isLocationEnabled(lm)
        items += DiagnosticItem(
            "Konum servisi", locOn,
            if (locOn) "Açık (konumun kullanılmaz veya kaydedilmez)" else "Kapalı — birçok cihazda Bluetooth taraması için açık olmalı",
            required = Build.VERSION.SDK_INT < 31,
            fix = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            fixLabel = "Aç",
        )

        if (btOn) {
            val bleAdv = runCatching { adapter?.isMultipleAdvertisementSupported == true }.getOrDefault(true)
            if (!bleAdv) {
                items += DiagnosticItem(
                    "Bluetooth LE yayını", false,
                    "Bu cihaz kendini Bluetooth LE ile duyuramıyor. Bu cihazda \"Kitap al\" yerine gönderen ol " +
                        "ya da iki cihazı aynı Wi-Fi ağına bağla.",
                    required = false,
                )
            }
        }
        return items
    }
}
