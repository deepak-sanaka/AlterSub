package com.altersub.core.parser

import com.altersub.core.model.SubtitleCue
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Parses SRT into a sorted list of SubtitleCues. Also accepts the WebVTT subset players produce:
 * the WEBVTT header and NOTE blocks, hour-less timestamps, cue settings and voice/class tags.
 */
object SrtParser {

    private val htmlTagPattern = Regex("<[^>]*>")
    private val windows1252: Charset = Charset.forName("windows-1252")

    /**
     * Parses a subtitle InputStream into a sorted list of SubtitleCues.
     * Resilient against BOMs, legacy encodings and missing blank lines between cues.
     */
    fun parse(inputStream: InputStream): List<SubtitleCue> {
        val cues = ArrayList<SubtitleCue>(800)

        var startTimeMs = -1L
        var endTimeMs = -1L
        val textBuilder = StringBuilder(128)

        fun flushCue() {
            if (startTimeMs >= 0 && endTimeMs > startTimeMs && textBuilder.isNotEmpty()) {
                cues.add(
                    SubtitleCue(
                        index = cues.size + 1,
                        startTimeMs = startTimeMs,
                        endTimeMs = endTimeMs,
                        text = textBuilder.toString().trim()
                    )
                )
            }
            startTimeMs = -1L
            endTimeMs = -1L
            textBuilder.setLength(0)
        }

        for (line in decode(inputStream.readBytes()).lineSequence()) {
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                flushCue()
            } else if (trimmed.contains("-->")) {
                // A timing line while a cue is still open means the blank separator line was missing,
                // so the line before it was the next cue's number, not dialogue
                if (startTimeMs >= 0) {
                    dropTrailingCueNumber(textBuilder)
                    flushCue()
                }
                val parts = trimmed.split("-->")
                startTimeMs = parseTimestamp(parts[0].trim())
                // End time may be followed by styling tokens or WebVTT cue settings (align:, position:)
                endTimeMs = parseTimestamp(parts[1].trim().takeWhile { !it.isWhitespace() })
            } else if (startTimeMs >= 0) {
                // This is subtitle text content (strip markup like <i>, <font>, <v Speaker>)
                val sanitizedLine = cleanMarkup(trimmed)
                if (sanitizedLine.isNotEmpty()) {
                    if (textBuilder.isNotEmpty()) {
                        textBuilder.append("\n")
                    }
                    textBuilder.append(sanitizedLine)
                }
            }
        }

        // Add trailing cue if file didn't end with blank line
        flushCue()

        return cues.sortedBy { it.startTimeMs }
    }

    /**
     * Converts "00:01:23,456", "00:01:23.456" or WebVTT's hour-less "01:23.456" into milliseconds.
     */
    fun parseTimestamp(timestampStr: String): Long {
        try {
            val parts = timestampStr.replace(',', '.').split(":")
            val hours: Long
            val minutes: Long
            val secondsField: String
            when (parts.size) {
                3 -> {
                    hours = parts[0].trim().toLong()
                    minutes = parts[1].trim().toLong()
                    secondsField = parts[2].trim()
                }
                2 -> {
                    hours = 0L
                    minutes = parts[0].trim().toLong()
                    secondsField = parts[1].trim()
                }
                else -> return -1L
            }

            val secParts = secondsField.split(".")
            val seconds = secParts[0].toLong()
            val millis = if (secParts.size > 1) {
                secParts[1].padEnd(3, '0').take(3).toLong()
            } else 0L

            return (hours * 3600000L) + (minutes * 60000L) + (seconds * 1000L) + millis
        } catch (_: Exception) {
            // Ignore parse errors on malformed timestamps
        }
        return -1L
    }

    /**
     * Subtitle files in the wild are UTF-8, UTF-16 or legacy Windows-1252: honour a BOM, accept
     * valid UTF-8, and otherwise fall back to Windows-1252 instead of showing replacement characters.
     */
    internal fun decode(bytes: ByteArray): String {
        fun startsWith(vararg prefix: Int) =
            bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it].toByte() }

        return when {
            startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            // BOM-less UTF-16: the ASCII digits every subtitle file starts with leave a zero in every other byte
            bytes.size >= 4 && bytes[0] != 0.toByte() && bytes[1] == 0.toByte() && bytes[3] == 0.toByte() ->
                String(bytes, Charsets.UTF_16LE)
            bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] != 0.toByte() && bytes[2] == 0.toByte() ->
                String(bytes, Charsets.UTF_16BE)
            else -> try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (_: CharacterCodingException) {
                String(bytes, windows1252)
            }
        }
    }

    private fun dropTrailingCueNumber(text: StringBuilder) {
        val lastLineStart = text.lastIndexOf("\n") + 1
        if (lastLineStart < text.length && (lastLineStart until text.length).all { text[it].isDigit() }) {
            text.setLength((lastLineStart - 1).coerceAtLeast(0))
        }
    }

    private fun cleanMarkup(input: String): String {
        var text = input.replace(htmlTagPattern, "")
        if (text.indexOf('&') >= 0) {
            // &amp; last so "&amp;lt;" stays a literal "&lt;"
            text = text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace("&lrm;", "")
                .replace("&rlm;", "")
                .replace("&amp;", "&")
        }
        return text.trim()
    }
}
