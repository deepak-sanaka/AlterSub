package com.altersub.core.parser

import java.io.File

/**
 * How long a subtitle file runs: the latest end time of any cue. Shown next to each file on the phone, so the one
 * timed for the same cut as the video is easy to spot. Reads only the timestamps, without building cues.
 */
object SubtitleDuration {

    // "--> 02:19:25,104" (SRT) or "--> 01:02.345" (WebVTT, hours optional)
    private val endTime = Regex("""-->\s*(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")

    fun of(file: File): Long? = of(file.readBytes())

    fun of(bytes: ByteArray): Long? {
        val text = SrtParser.decode(bytes)
        var latest: Long? = null
        for (match in endTime.findAll(text)) {
            val (hours, minutes, seconds, millis) = match.destructured
            val ms = ((hours.toLongOrNull() ?: 0L) * 3600 + minutes.toLong() * 60 + seconds.toLong()) * 1000 +
                millis.padEnd(3, '0').toLong()
            if (latest == null || ms > latest) latest = ms
        }
        return latest
    }
}
