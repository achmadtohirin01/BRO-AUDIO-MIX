package com.example.model

import androidx.compose.ui.graphics.Color

// Represents a channel track in our digital audio workstation (DAW)
data class ChannelTrack(
    val id: Int,                        // 1 to 10
    val name: String,                   // e.g. "Vocal", "Gitar", "Tambah Bass"
    val group: TrackGroup,              // ORIGINAL (1-5) or CLONE (6-10)
    val instrumentName: String,         // The current instrument assigned (e.g. "Acoustic Guitar", "Metal Drum")
    val defaultInstrument: String,      // Default instrument for backup
    val volume: Float = 0.8f,           // 0.0f to 1.0f
    val pan: Float = 0.0f,              // -1.0f (Left), 0.0f (Center), 1.0f (Right)
    val isMuted: Boolean = false,
    val isSoloed: Boolean = false,
    val colorHex: String = "#00E5FF",   // Hex color for track theme
    val waveformPoints: List<Float> = emptyList(), // Layout bars for visualization
    val vuLeft: Float = 0.0f,           // Current Left channel output level (0.0f to 1.0f)
    val vuRight: Float = 0.0f,          // Current Right channel output level (0.0f to 1.0f)
    val isCloned: Boolean = false,      // Has this track undergone AI Instrument Cloning?
    val originalStyle: String = "Pop",  // Original audio style
    val currentStyle: String = "Pop"    // Current transformed style
)

enum class TrackGroup {
    ORIGINAL,   // Tracks 1 to 5
    CLONED      // Tracks 6 to 10 (Custom added)
}

// Represents a pre-loaded track standard in our BRO AUDIO MIX catalog
data class AudioSongSample(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val durationText: String,
    val basicStyles: List<String>,
    val tracksData: List<ChannelTrackPreset>
)

data class ChannelTrackPreset(
    val id: Int,
    val name: String,
    val instrumentName: String,
    val colorHex: String,
    val density: Float // Waveform multiplier
)
