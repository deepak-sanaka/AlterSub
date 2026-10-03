package com.altersub.detection

/**
 * Parses `dumpsys media_session` output into the play state and position each app publishes.
 *
 * This is the fallback for low-RAM TVs (`ro.config.low_ram=true`, common on 1-2 GB boxes), where Android
 * refuses notification-listener access to every non-system app, so MediaSessionManager is unusable. Reading
 * the dump needs the DUMP permission instead, which ADB can grant once (`pm grant com.altersub
 * android.permission.DUMP`). The format below is Android 9's; unknown lines are ignored.
 */
object MediaSessionDump {

    data class Session(
        val packageName: String,
        val active: Boolean,
        /** android.media.session.PlaybackState constant, or null if the app publishes no state. */
        val state: Int?,
        /** -1 when the app publishes no position. */
        val positionMs: Long,
        val speed: Float,
        /** SystemClock.elapsedRealtime() at which [positionMs] was reported. */
        val updatedRealtimeMs: Long,
        /** Title from the session's metadata, or null (Netflix publishes none). */
        val title: String?
    )

    private val STATE = Regex(
        """state=PlaybackState \{state=(-?\d+), position=(-?\d+), buffered position=-?\d+, speed=(-?[\d.]+), updated=(\d+)"""
    )
    private val METADATA = Regex("""metadata:size=(\d+), description=(.*)""")

    fun parse(dump: String): List<Session> {
        val sessions = ArrayList<Session>()
        var builder: Builder? = null

        for (rawLine in dump.lineSequence()) {
            val line = rawLine.trim()
            if (line.startsWith("package=")) {
                builder?.let { sessions += it.build() }
                builder = Builder(line.removePrefix("package="))
                continue
            }
            val current = builder ?: continue
            when {
                line.startsWith("active=") -> current.active = line == "active=true"
                line.startsWith("state=PlaybackState") -> STATE.find(line)?.let { m ->
                    current.state = m.groupValues[1].toInt()
                    current.positionMs = m.groupValues[2].toLong()
                    current.speed = m.groupValues[3].toFloat()
                    current.updatedRealtimeMs = m.groupValues[4].toLong()
                }

                line.startsWith("metadata:") -> METADATA.find(line)?.let { m ->
                    if (m.groupValues[1] != "0") current.title = titleOf(m.groupValues[2])
                }

                // A blank line or the next section ends the session list
                line.isEmpty() || line.startsWith("Audio playback") -> {
                    sessions += current.build()
                    builder = null
                }
            }
        }
        builder?.let { sessions += it.build() }
        return sessions
    }

    /**
     * MediaDescription prints as "title, subtitle, description". The title itself may contain commas, so
     * the last two separators are stripped rather than splitting on the first.
     */
    private fun titleOf(description: String): String? {
        var title = description
        repeat(2) {
            val cut = title.lastIndexOf(", ")
            if (cut >= 0) title = title.substring(0, cut)
        }
        return title.trim().takeUnless { it.isEmpty() || it == "null" }
    }

    private class Builder(val packageName: String) {
        var active = false
        var state: Int? = null
        var positionMs = -1L
        var speed = 1f
        var updatedRealtimeMs = 0L
        var title: String? = null

        fun build() = Session(packageName, active, state, positionMs, speed, updatedRealtimeMs, title)
    }
}
