package com.altersub.core.parser

import java.io.File
import java.util.regex.Pattern

/**
 * How long a subtitle file runs: the latest end time of any cue. Shown next to each file on the phone, so the one
 * timed for the same cut as the video is easy to spot. Reads only the timestamps, without building cues.
 */
object SubtitleDuration {

    // "--> 02:19:25,104" (SRT) or "--> 01:02.345" (WebVTT, hours optional)
    private val endTime: Pattern = Pattern.compile("""-->\s*(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")

    fun of(file: File): Long? = of(file.readBytes())

    fun of(bytes: ByteArray): Long? {
        val text = SrtParser.decode(bytes)
        // One matcher for the whole file. Kotlin's Regex.findAll makes a new matcher per match, and on Android each
        // one holds a native copy of the whole text until it's garbage-collected: ~240 MB of native memory for three
        // 90 KB files on the emulator.
        val matcher = endTime.matcher(text)
        var latest: Long? = null
        while (matcher.find()) {
            val hours = matcher.group(1)?.toLong() ?: 0L
            val ms = (hours * 3600 + matcher.group(2)!!.toLong() * 60 + matcher.group(3)!!.toLong()) * 1000 +
                matcher.group(4)!!.padEnd(3, '0').toLong()
            if (latest == null || ms > latest) latest = ms
        }
        return latest
    }
}
