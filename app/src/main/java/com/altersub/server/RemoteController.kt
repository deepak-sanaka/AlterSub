package com.altersub.server

import com.altersub.core.clock.SubtitleClock
import com.altersub.core.model.ContentMetadata
import com.altersub.core.model.SubtitleStyle
import com.altersub.core.model.SubtitleTrack
import com.altersub.detection.DetectionSource
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Everything the phone web remote can read and do. Implemented by AlterSubApp; a plain interface so the
 * HTTP routes can be tested on the JVM against a fake.
 */
interface RemoteController {
    val clock: SubtitleClock
    val currentContent: StateFlow<ContentMetadata?>
    val availableTracks: StateFlow<List<SubtitleTrack>>
    val activeTrack: StateFlow<SubtitleTrack?>
    val overlayRunning: StateFlow<Boolean>
    val overlayError: StateFlow<String?>
    val subtitleStyle: StateFlow<SubtitleStyle>

    fun onContentDetected(metadata: ContentMetadata, source: DetectionSource)
    fun selectTrack(track: SubtitleTrack)
    fun loadDirectSrt(file: File, displayName: String)
    fun updateSubtitleStyle(change: (SubtitleStyle) -> SubtitleStyle)
}
