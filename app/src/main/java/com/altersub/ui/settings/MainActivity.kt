package com.altersub.ui.settings

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.altersub.AlterSubApp
import com.altersub.R
import com.altersub.core.model.SubtitleCue
import com.altersub.core.parser.SubtitleIndex
import com.altersub.databinding.ActivityMainBinding
import com.altersub.service.AccessibilityInspectorService
import com.altersub.service.MediaNotificationListener
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupIpAddress()
        setupPermissions()
        setupTestButton()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()

        // Start the overlay while we're in the foreground, where Android always allows it. On a TV it then
        // stays up, so later detections never have to start a foreground service from the background.
        // Skipped on phones, where an always-present full-screen overlay would block touches (KI-11).
        if (Settings.canDrawOverlays(this) && packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            AlterSubApp.startOverlayService(this)
        }
    }

    private fun setupIpAddress() {
        val ip = getLocalIpAddress()
        binding.tvIpAddress.text = "http://$ip:8080"
    }

    private fun setupPermissions() {
        binding.btnOverlayPermission.setOnClickListener {
            openPermissionScreen(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                "Display Over Other Apps",
                "appops set $packageName SYSTEM_ALERT_WINDOW allow"
            )
        }

        binding.btnAccessibilityPermission.setOnClickListener {
            openPermissionScreen(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                "Accessibility Title Inspector",
                "settings put secure enabled_accessibility_services " +
                    "$packageName/${AccessibilityInspectorService::class.java.name}\n" +
                    "adb shell settings put secure accessibility_enabled 1"
            )
        }

        binding.btnNotificationPermission.setOnClickListener {
            openPermissionScreen(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                "MediaSession Play/Pause Sync",
                "cmd notification allow_listener $packageName/${MediaNotificationListener::class.java.name}"
            )
        }
    }

    /**
     * Many Android TV builds (including the Android TV emulator images) ship without these special-access
     * screens, and launching a missing one crashed the app. Fall back to explaining the ADB grant instead.
     */
    private fun openPermissionScreen(intent: Intent, permissionName: String, adbShellCommand: String) {
        try {
            startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
        }

        AlertDialog.Builder(this)
            .setTitle(permissionName)
            .setMessage(
                "This TV has no settings screen for this permission. Grant it from a computer " +
                    "connected with ADB:\n\nadb shell $adbShellCommand"
            )
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun updatePermissionStatuses() {
        val canOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        binding.tvOverlayStatus.text = if (canOverlay) {
            "1. Display Over Other Apps: ✅ GRANTED"
        } else {
            "1. Display Over Other Apps: ❌ REQUIRED"
        }
        binding.btnOverlayPermission.isEnabled = !canOverlay

        val inspectorEnabled = isAccessibilityInspectorEnabled()
        binding.tvAccessibilityStatus.text = if (inspectorEnabled) {
            "2. Accessibility Title Inspector: ✅ ENABLED"
        } else {
            "2. Accessibility Title Inspector: ❌ NOT ENABLED"
        }
        binding.btnAccessibilityPermission.isEnabled = !inspectorEnabled

        val listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        binding.tvNotificationStatus.text = if (listenerEnabled) {
            "3. MediaSession Play/Pause Sync: ✅ ENABLED"
        } else {
            "3. MediaSession Play/Pause Sync: ❌ NOT ENABLED"
        }
        binding.btnNotificationPermission.isEnabled = !listenerEnabled
    }

    private fun isAccessibilityInspectorEnabled(): Boolean {
        val inspector = ComponentName(this, AccessibilityInspectorService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == inspector }
    }

    private fun setupTestButton() {
        binding.btnTestSubtitle.setOnClickListener {
            // Load test subtitles into engine
            val testCues = listOf(
                SubtitleCue(1, 0, 3000, "🎬 AlterSub Overlay is Working!"),
                SubtitleCue(2, 3200, 6500, "Synchronized subtitles appear over any streaming app."),
                SubtitleCue(3, 6700, 10000, "Open the web remote on your phone to control sync!")
            )

            val app = AlterSubApp.instance
            app.clock.reset()
            app.clock.play()
            app.setTestSubtitleIndex(SubtitleIndex(testCues))

            AlterSubApp.startOverlayService(this)
            Toast.makeText(this, "Test subtitles started for 10 seconds!", Toast.LENGTH_LONG).show()
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback to WifiManager
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ip = wifiManager?.connectionInfo?.ipAddress ?: 0
        @Suppress("DEPRECATION")
        return Formatter.formatIpAddress(ip)
    }
}
