package com.altersub.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.altersub.AlterSubApp
import com.altersub.R
import com.altersub.core.model.SubtitleCue
import com.altersub.core.parser.SubtitleIndex
import com.altersub.databinding.ActivityMainBinding
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
    }

    private fun setupIpAddress() {
        val ip = getLocalIpAddress()
        binding.tvIpAddress.text = "http://$ip:8080"
    }

    private fun setupPermissions() {
        binding.btnOverlayPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        binding.btnAccessibilityPermission.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.btnNotificationPermission.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }
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
