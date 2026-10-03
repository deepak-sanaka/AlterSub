package com.altersub.ui.settings

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.altersub.AlterSubApp
import com.altersub.AlterSubApp.WebRemoteState
import com.altersub.R
import com.altersub.core.model.SubtitleCue
import com.altersub.core.parser.SubtitleIndex
import com.altersub.databinding.ActivityMainBinding
import com.altersub.server.RemoteAuth
import com.altersub.server.WebRemoteServer
import com.altersub.service.AccessibilityInspectorService
import com.altersub.service.MediaNotificationListener
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var ipAddress = ""
    private var remoteQrLink: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ipAddress = getLocalIpAddress()
        setupWebRemote()
        setupPermissions()
        setupTestButton()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()

        // Phones can only pair while this screen (and so the PIN) is showing on the TV
        AlterSubApp.instance.remoteAuth.openPairing()

        // Start the overlay while we're in the foreground, where Android always allows it. On a TV it then
        // stays up, so later detections never have to start a foreground service from the background.
        // Skipped on phones, where an always-present full-screen overlay would block touches (KI-11).
        if (Settings.canDrawOverlays(this) && packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            AlterSubApp.startOverlayService(this)
        }
    }

    override fun onPause() {
        super.onPause()
        AlterSubApp.instance.remoteAuth.closePairing()
    }

    private fun setupWebRemote() {
        val app = AlterSubApp.instance

        binding.btnWebRemoteToggle.setOnClickListener {
            app.setWebRemoteEnabled(app.webRemoteState.value !is WebRemoteState.Running)
        }
        binding.btnUnpairPhones.setOnClickListener {
            app.remoteAuth.unpair()
            Toast.makeText(this, "Phone unpaired. Scan the code to connect a phone again.", Toast.LENGTH_LONG).show()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(app.webRemoteState, app.remoteAuth.pairing, app.remoteAuth.isPaired) { state, pairing, paired ->
                    Triple(state, pairing, paired)
                }.collect { (state, pairing, paired) -> renderWebRemote(state, pairing, paired) }
            }
        }
    }

    private fun renderWebRemote(state: WebRemoteState, pairing: RemoteAuth.Pairing, paired: Boolean) {
        val running = state as? WebRemoteState.Running
        val pin = (pairing as? RemoteAuth.Pairing.Open)?.pin
        val locked = pairing == RemoteAuth.Pairing.Locked

        // Pairing is offered only while the remote is on, no phone is paired yet, and PIN entry isn't locked.
        // The QR carries the PIN, so scanning it opens the remote already paired.
        val pairingLink = if (running != null && !paired && pin != null) QrCode.remoteLink(ipAddress, running.port, pin) else null
        binding.remotePairing.isVisible = pairingLink != null
        pairingLink?.let(::showRemoteQr)

        binding.remoteStateRow.isVisible = pairingLink == null
        when {
            state == WebRemoteState.Off -> showRemoteState(
                R.drawable.ic_status_off,
                "Phone remote is off",
                "Turn it on to pick subtitles and fix the timing from your phone."
            )

            state == WebRemoteState.Failed -> showRemoteState(
                R.drawable.ic_status_todo,
                "Couldn't start the phone remote",
                "Ports ${WebRemoteServer.PORTS.first()}–${WebRemoteServer.PORTS.last()} are all in use. Press Retry to try again."
            )

            paired -> showRemoteState(
                R.drawable.ic_status_done,
                "Phone paired",
                "Pick subtitles and fix the timing from the AlterSub page on your phone. To use another phone, unpair this one first."
            )

            locked -> showRemoteState(
                R.drawable.ic_status_todo,
                "Too many wrong PINs",
                "Press Back and reopen AlterSub for a new PIN."
            )
        }

        // The address is the big, easy-to-type fallback to the QR code (browsers add http:// themselves)
        binding.remoteAddress.isVisible = running != null
        if (running != null) {
            binding.tvAddressLabel.text = if (paired) "Remote address" else "Or type this address in your phone's browser"
            binding.tvIpAddress.text = "$ipAddress:${running.port}"
        }
        binding.remotePinRow.isVisible = pairingLink != null
        // Grouped as "482 913" so it's easy to read from the couch
        binding.tvRemotePin.text = pin?.chunked(3)?.joinToString(" ").orEmpty()

        val (chipText, chipColor, chipTextColor) = when {
            state == WebRemoteState.Off -> Triple("Off", R.color.button_idle, R.color.text_secondary)
            state == WebRemoteState.Failed -> Triple("Not running", R.color.status_amber, R.color.black)
            paired -> Triple("Paired", R.color.status_green, R.color.black)
            locked -> Triple("Locked", R.color.status_amber, R.color.black)
            else -> Triple("Waiting for phone", R.color.button_idle, R.color.text_secondary)
        }
        binding.tvRemoteStatus.text = chipText
        binding.tvRemoteStatus.backgroundTintList = ColorStateList.valueOf(getColor(chipColor))
        binding.tvRemoteStatus.setTextColor(getColor(chipTextColor))

        // Hiding the focused button would send D-pad focus jumping up the screen, so hand it to its neighbour
        if (!paired && binding.btnUnpairPhones.isFocused) binding.btnWebRemoteToggle.requestFocus()
        binding.btnUnpairPhones.isVisible = paired

        binding.btnWebRemoteToggle.text = when (state) {
            is WebRemoteState.Running -> "Turn off"
            WebRemoteState.Off -> "Turn on"
            WebRemoteState.Failed -> "Retry"
        }
    }

    private fun showRemoteState(icon: Int, title: String, body: String) {
        binding.ivRemoteState.setImageResource(icon)
        binding.tvRemoteStateTitle.text = title
        binding.tvRemoteStateBody.text = body
    }

    /** Encodes a new QR only when the link (address, port or PIN) actually changes. */
    private fun showRemoteQr(link: String) {
        if (link == remoteQrLink) return
        remoteQrLink = link
        binding.ivRemoteQr.setImageDrawable(QrCode.drawable(resources, link))
    }

    private fun setupPermissions() {
        binding.btnOverlayPermission.setOnClickListener {
            openPermissionScreen(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                "Show subtitles over other apps",
                "appops set $packageName SYSTEM_ALERT_WINDOW allow"
            )
        }

        binding.btnAccessibilityPermission.setOnClickListener {
            openPermissionScreen(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                "Recognise what's playing",
                "settings put secure enabled_accessibility_services " +
                    "$packageName/${AccessibilityInspectorService::class.java.name}\n" +
                    "adb shell settings put secure accessibility_enabled 1"
            )
        }

        binding.btnNotificationPermission.setOnClickListener {
            openPermissionScreen(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                "Follow play and pause",
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
        val canOverlay = Settings.canDrawOverlays(this)
        val inspectorEnabled = isAccessibilityInspectorEnabled()
        val listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

        showStep(binding.ivOverlayStatus, binding.btnOverlayPermission, canOverlay)
        showStep(binding.ivAccessibilityStatus, binding.btnAccessibilityPermission, inspectorEnabled)
        showStep(binding.ivNotificationStatus, binding.btnNotificationPermission, listenerEnabled)

        val remaining = listOf(canOverlay, inspectorEnabled, listenerEnabled).count { !it }
        binding.tvReadyChip.text = when (remaining) {
            0 -> "Ready"
            1 -> "1 step left"
            else -> "$remaining steps left"
        }
        binding.tvReadyChip.backgroundTintList =
            ColorStateList.valueOf(getColor(if (remaining == 0) R.color.status_green else R.color.status_amber))

        binding.tvNextStep.text = when {
            !canOverlay -> "Start with the first step: without it, AlterSub can't show subtitles."
            remaining > 0 -> "Almost there. Finish the remaining steps so AlterSub works on its own."
            else -> "You're all set. Start a show in your streaming app and subtitles appear on their own. " +
                "Use your phone to switch tracks or fix the timing."
        }

        // Finished steps hide their buttons, so make sure D-pad focus lands on something visible:
        // the next step to do, or the test button once everything is set up
        val focused = currentFocus
        if (focused == null || !focused.isShown) {
            listOf(binding.btnOverlayPermission, binding.btnAccessibilityPermission, binding.btnNotificationPermission)
                .firstOrNull { it.isVisible }
                ?.requestFocus()
                ?: binding.btnTestSubtitle.requestFocus()
        }
    }

    private fun showStep(icon: ImageView, button: Button, done: Boolean) {
        icon.setImageResource(if (done) R.drawable.ic_status_done else R.drawable.ic_status_todo)
        button.isVisible = !done
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
