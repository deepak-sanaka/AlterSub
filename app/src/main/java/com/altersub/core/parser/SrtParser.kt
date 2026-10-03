package com.altersub.core.parser

import com.altersub.core.model.SubtitleCue
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object SrtParser {

    private val htmlTagPattern = Regex("<[^>]*>")

    /**
     * Parses an SRT InputStream into a sorted list of SubtitleCues.
     * Memory efficient and resilient against malformed/BOM headers.
     */
    fun parse(inputStream: InputStream): List<SubtitleCue> {
        val cues = ArrayList<SubtitleCue>(800)
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

        var currentCueIndex = 1
        var startTimeMs = -1L
        var endTimeMs = -1L
        val textBuilder = StringBuilder(128)

        var line: String? = reader.readLine()
        while (line != null) {
            val trimmed = line.trim().removePrefix("\uFEFF") // Strip UTF-8 BOM if present

            if (trimmed.isEmpty()) {
                if (startTimeMs >= 0 && endTimeMs > startTimeMs && textBuilder.isNotEmpty()) {
                    cues.add(
                        SubtitleCue(
                            index = currentCueIndex++,
                            startTimeMs = startTimeMs,
                            endTimeMs = endTimeMs,
                            text = textBuilder.toString().trim()
                        )
                    )
                }
                startTimeMs = -1L
                endTimeMs = -1L
                textBuilder.setLength(0)
            } else if (trimmed.contains("-->")) {
                val parts = trimmed.split("-->")
                if (parts.size >= 2) {
                    startTimeMs = parseTimestamp(parts[0].trim())
                    // End time may have styling tokens after it, grab first token
                    val endPart = parts[1].trim().split(" ")[0]
                    endTimeMs = parseTimestamp(endPart)
                }
            } else if (startTimeMs >= 0) {
                // This is subtitle text content (strip basic HTML tags like <i>, <font>, etc.)
                val sanitizedLine = cleanHtmlTags(trimmed)
                if (sanitizedLine.isNotEmpty()) {
                    if (textBuilder.isNotEmpty()) {
                        textBuilder.append("\n")
                    }
                    textBuilder.append(sanitizedLine)
                }
            }

            line = reader.readLine()
        }

        // Add trailing cue if file didn't end with blank line
        if (startTimeMs >= 0 && endTimeMs > startTimeMs && textBuilder.isNotEmpty()) {
            cues.add(
                SubtitleCue(
                    index = currentCueIndex,
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                    text = textBuilder.toString().trim()
                )
            )
        }

        return cues.sortedBy { it.startTimeMs }
    }

    /**
     * Converts "00:01:23,456" or "00:01:23.456" into milliseconds.
     */
    fun parseTimestamp(timestampStr: String): Long {
        try {
            val normalized = timestampStr.replace(',', '.')
            val parts = normalized.split(":")
            if (parts.size == 3) {
                val hours = parts[0].trim().toLong()
                val minutes = parts[1].trim().toLong()
                val secParts = parts[2].trim().split(".")
                val seconds = secParts[0].toLong()
                val millis = if (secParts.size > 1) {
                    val rawMs = secParts[1].padEnd(3, '0').take(3)
                    rawMs.toLong()
                } else 0L

                return (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + millis
            }
        } catch (_: Exception) {
            // Ignore parse errors on malformed timestamps
        }
        return -1L
    }

    private fun cleanHtmlTags(input: String): String {
        return input.replace(htmlTagPattern, "")
    }
}
