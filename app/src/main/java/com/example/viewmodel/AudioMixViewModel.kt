package com.example.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.audio.AudioSynthesizer
import com.example.model.AudioSongSample
import com.example.model.ChannelTrack
import com.example.model.ChannelTrackPreset
import com.example.model.TrackGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioMixViewModel(private val app: Application) : AndroidViewModel(app) {

    private val synthesizer = AudioSynthesizer()

    // Active track structures (Tracks 1-10)
    private val _tracks = MutableStateFlow<List<ChannelTrack>>(emptyList())
    val tracks = _tracks.asStateFlow()

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playheadSeconds = MutableStateFlow(0.0f)
    val playheadSeconds = _playheadSeconds.asStateFlow()

    private val _isLooping = MutableStateFlow(true)
    val isLooping = _isLooping.asStateFlow()

    private val _currentSong = MutableStateFlow<AudioSongSample?>(null)
    val currentSong = _currentSong.asStateFlow()

    // AI Stem separation processing state
    private val _isAnalyzingStems = MutableStateFlow(false)
    val isAnalyzingStems = _isAnalyzingStems.asStateFlow()

    private val _analysisProgress = MutableStateFlow(0.0f)
    val analysisProgress = _analysisProgress.asStateFlow()

    // Master VU and clipping indicators
    val masterVULeft = synthesizer.masterVULeft
    val masterVURight = synthesizer.masterVURight
    val isClipping = synthesizer.isClipping
    val spectrumBands = synthesizer.spectrumBands
    val liveWaveform = synthesizer.liveWaveform

    // AI Instrument cloning style modification state
    private val _cloningTrackId = MutableStateFlow<Int?>(null)
    val cloningTrackId = _cloningTrackId.asStateFlow()

    // Audio rendering/exporting flow
    private val _isRendering = MutableStateFlow(false)
    val isRendering = _isRendering.asStateFlow()

    private val _renderProgress = MutableStateFlow(0.0f)
    val renderProgress = _renderProgress.asStateFlow()

    // "Bro AI Assistant" (Gemini) chat conversation logs
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage("BRO AI", "Halo kawan! Selamat datang di BRO AUDIO MIX. Saya adalah asisten music producer AI Anda. Ada yang bisa saya bantu terkait Audio Separation, Instrument Cloner, atau mixing hari ini?", false)
        )
    )
    val chatMessages = _chatMessages.asStateFlow()

    private val _chatInputText = MutableStateFlow("")
    val chatInputText = _chatInputText.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading = _isChatLoading.asStateFlow()

    private var playbackJob: Job? = null

    // Sample catalogue songs
    val songLibrary = listOf(
        AudioSongSample(
            id = "s1",
            title = "Kopi Dangdut",
            artist = "Fahmi Shahab",
            genre = "Salsa Dangdut",
            durationText = "03:12",
            basicStyles = listOf("Dangdut", "Latin", "Pop", "EDM", "Jazz"),
            tracksData = listOf(
                ChannelTrackPreset(1, "Vocal Lead", "Fahmi Vocal Lead", "#00E5FF", 1.2f),
                ChannelTrackPreset(2, "Classical Guitar", "Acoustic Guitar", "#FF007F", 0.8f),
                ChannelTrackPreset(3, "Brass Section", "Salsa Trumpet", "#FFD700", 1.1f),
                ChannelTrackPreset(4, "Bass Line", "Acoustic Bass", "#39FF14", 0.9f),
                ChannelTrackPreset(5, "Percussion Block", "Kendang & Agogo Beat", "#EA80FC", 1.1f)
            )
        ),
        AudioSongSample(
            id = "s2",
            title = "Lathi",
            artist = "Weird Genius ft. Sara",
            genre = "Indo EDM Electro",
            durationText = "03:09",
            basicStyles = listOf("EDM", "Dangdut", "Orchestral", "Rock", "Metal"),
            tracksData = listOf(
                ChannelTrackPreset(1, "Vocal Sinden", "Sinden Vocal", "#00E5FF", 1.5f),
                ChannelTrackPreset(2, "Acoustic Pluck", "Acoustic Guitar", "#FF007F", 0.7f),
                ChannelTrackPreset(3, "Gamelan Bells", "Saron & Gamelan Synth", "#FFD700", 1.3f),
                ChannelTrackPreset(4, "Cyber Sub-Bass", "EDM Sub Bass", "#39FF14", 1.0f),
                ChannelTrackPreset(5, "EDM Drums", "Electro Drum Kit", "#EA80FC", 1.4f)
            )
        ),
        AudioSongSample(
            id = "s3",
            title = "Smells Like Teen Spirit",
            artist = "Nirvana",
            genre = "Grunge Alternative",
            durationText = "04:30",
            basicStyles = listOf("Rock", "Metal", "Grunge", "Blues", "Cinematic"),
            tracksData = listOf(
                ChannelTrackPreset(1, "Lead Vocals", "Cobain Screams", "#00E5FF", 1.3f),
                ChannelTrackPreset(2, "Distorted Left", "Fender Jaguar", "#FF007F", 1.2f),
                ChannelTrackPreset(3, "Rhythm Right", "Stereo Chorus Guitar", "#FFD700", 0.9f),
                ChannelTrackPreset(4, "Precision Bass", "Solid Bass Track", "#39FF14", 1.1f),
                ChannelTrackPreset(5, "Rock Drums", "Heavy Acoustic Drums", "#EA80FC", 1.4f)
            )
        )
    )

    init {
        // Build 10 default offline channel tracks
        resetTracks()
        // Sync synthesizer immediately on start
        synthesizer.updateTracks(_tracks.value)
    }

    private fun resetTracks() {
        val initialList = mutableListOf<ChannelTrack>()
        // Tracks 1 to 5 (Original items)
        for (i in 1..5) {
            val name = when (i) {
                1 -> "Vocal"
                2 -> "Gitar"
                3 -> "Instrumen Utama"
                4 -> "Bass"
                else -> "Drum"
            }
            initialList.add(
                ChannelTrack(
                    id = i,
                    name = name,
                    group = TrackGroup.ORIGINAL,
                    instrumentName = name,
                    defaultInstrument = name,
                    colorHex = getChannelColor(i),
                    waveformPoints = generateWaveformDummy(i, 1.0f)
                )
            )
        }
        // Tracks 6 to 10 (Custom Cloned / Added)
        for (i in 6..10) {
            val placeholder = when (i) {
                6 -> "Tambah Instrumen"
                7 -> "Tambah Drum"
                8 -> "Tambah Bass"
                9 -> "Tambah Gitar"
                else -> "Tambah Instrumen Lain"
            }
            initialList.add(
                ChannelTrack(
                    id = i,
                    name = placeholder,
                    group = TrackGroup.CLONED,
                    instrumentName = "Muted Space",
                    defaultInstrument = "Synth Pad",
                    volume = 0.0f, // Start silent
                    colorHex = "#4A5568", // Gray offline
                    waveformPoints = emptyList()
                )
            )
        }
        _tracks.value = initialList
    }

    private fun getChannelColor(trackId: Int): String {
        return when (trackId) {
            1 -> "#00E5FF" // Electric blue
            2 -> "#FF007F" // Vibrant pink
            3 -> "#FFD700" // Neon yellow
            4 -> "#39FF14" // Acid green
            5 -> "#EA80FC" // Light violet
            else -> "#A0AEC0"
        }
    }

    private fun generateWaveformDummy(trackId: Int, modifier: Float): List<Float> {
        val count = 40
        val out = mutableListOf<Float>()
        val rndSeed = trackId * 15
        for (i in 0 until count) {
            val wave = 0.1f + 0.8f * kotlin.math.abs(
                kotlin.math.sin(i * 0.3f + rndSeed) * kotlin.math.cos(i * 0.08f)
            )
            out.add((wave * modifier).coerceIn(0.0f, 1.0f))
        }
        return out
    }

    // Load / Import song and trigger "AI Stem Separation"
    fun loadSongSample(song: AudioSongSample) {
        viewModelScope.launch {
            _isAnalyzingStems.value = true
            _analysisProgress.value = 0.0f
            _currentSong.value = song
            stopPlayback()

            // Cool multi-threaded analytical progress simulation
            while (_analysisProgress.value < 100.0f) {
                delay(120)
                _analysisProgress.value += 4.5f
            }
            _analysisProgress.value = 100.0f
            delay(400)
            _isAnalyzingStems.value = false

            // Populate Track 1 to 5 with imported song stems
            val currentList = _tracks.value.toMutableList()
            song.tracksData.forEach { preset ->
                val channelIdx = preset.id - 1
                if (channelIdx in 0..4) {
                    val currentTrack = currentList[channelIdx]
                    currentList[channelIdx] = currentTrack.copy(
                        name = preset.name,
                        instrumentName = preset.instrumentName,
                        colorHex = preset.colorHex,
                        waveformPoints = generateWaveformDummy(preset.id, preset.density),
                        volume = 0.8f,
                        isCloned = false,
                        originalStyle = song.genre,
                        currentStyle = song.genre
                    )
                }
            }

            // Keep track parameters, but reset custom Tracks 6-10 back to standby
            for (i in 5..9) {
                val currentTrack = currentList[i]
                val customPlaceholderName = when (i + 1) {
                    6 -> "Tambah Instrumen"
                    7 -> "Tambah Drum"
                    8 -> "Tambah Bass"
                    9 -> "Tambah Gitar"
                    else -> "Tambah Instrumen Lain"
                }
                currentList[i] = currentTrack.copy(
                    name = customPlaceholderName,
                    instrumentName = "Muted Space",
                    volume = 0.0f,
                    colorHex = "#4A5568",
                    waveformPoints = emptyList()
                )
            }

            _tracks.value = currentList
            synthesizer.updateTracks(currentList)
            startPlayback()

            Toast.makeText(app, "AI berhasil memilah 5 stem musik!", Toast.LENGTH_SHORT).show()
        }
    }

    // Transport Control Handlers
    fun togglePlayPause() {
        if (_isPlaying.value) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        synthesizer.start()

        playbackJob = viewModelScope.launch {
            while (_isPlaying.value) {
                delay(100)
                var nextPos = _playheadSeconds.value + 0.1f
                if (nextPos >= 180.0f) { // Loop if track ends
                    if (_isLooping.value) {
                        nextPos = 0.0f
                    } else {
                        nextPos = 0.0f
                        _isPlaying.value = false
                        synthesizer.stop()
                    }
                }
                _playheadSeconds.value = nextPos
            }
        }
    }

    fun stopPlayback() {
        _isPlaying.value = false
        synthesizer.stop()
        playbackJob?.cancel()
        playbackJob = null
    }

    fun resetTransport() {
        stopPlayback()
        _playheadSeconds.value = 0.0f
    }

    fun seekTo(seconds: Float) {
        _playheadSeconds.value = seconds.coerceIn(0.0f, 180.0f)
    }

    fun toggleLoop() {
        _isLooping.value = !_isLooping.value
    }

    // Track Mixing Handlers
    fun updateTrackVolume(trackId: Int, volumeValue: Float) {
        val list = _tracks.value.map {
            if (it.id == trackId) it.copy(volume = volumeValue) else it
        }
        _tracks.value = list
        synthesizer.updateTracks(list)
    }

    fun updateTrackPanning(trackId: Int, panValue: Float) {
        val list = _tracks.value.map {
            if (it.id == trackId) it.copy(pan = panValue.coerceIn(-1.0f, 1.0f)) else it
        }
        _tracks.value = list
        synthesizer.updateTracks(list)
    }

    fun toggleTrackMute(trackId: Int) {
        val list = _tracks.value.map {
            if (it.id == trackId) it.copy(isMuted = !it.isMuted) else it
        }
        _tracks.value = list
        synthesizer.updateTracks(list)
    }

    fun toggleTrackSolo(trackId: Int) {
        val targetTrack = _tracks.value.firstOrNull { it.id == trackId } ?: return
        val currentSoloState = targetTrack.isSoloed
        
        val list = _tracks.value.map {
            if (it.id == trackId) {
                it.copy(isSoloed = !currentSoloState)
            } else {
                it.copy(isSoloed = false) // Clear other solos to isolate properly
            }
        }
        _tracks.value = list
        synthesizer.updateTracks(list)
    }

    fun renameChannelTrack(trackId: Int, newName: String) {
        val list = _tracks.value.map {
            if (it.id == trackId) it.copy(name = newName) else it
        }
        _tracks.value = list
        synthesizer.updateTracks(list)
    }

    fun setChannelColor(trackId: Int, hex: String) {
        val list = _tracks.value.map {
            if (it.id == trackId) it.copy(colorHex = hex) else it
        }
        _tracks.value = list
        synthesizer.updateTracks(list)
    }

    // Three dots menu sub actions
    fun duplicateTrack(trackId: Int) {
        val source = _tracks.value.firstOrNull { it.id == trackId } ?: return
        // Find first empty, unused Track from tracks 6-10
        var targetIndex = -1
        for (i in 5..9) {
            if (_tracks.value[i].volume <= 0.05f && _tracks.value[i].waveformPoints.isEmpty()) {
                targetIndex = i
                break
            }
        }

        if (targetIndex != -1) {
            val list = _tracks.value.toMutableList()
            val targetId = targetIndex + 1
            list[targetIndex] = list[targetIndex].copy(
                name = "${source.name} (Copy)",
                instrumentName = "Cloned ${source.instrumentName}",
                volume = source.volume,
                pan = source.pan,
                colorHex = source.colorHex,
                waveformPoints = source.waveformPoints,
                isCloned = true,
                originalStyle = source.originalStyle,
                currentStyle = source.currentStyle
            )
            _tracks.value = list
            synthesizer.updateTracks(list)
            Toast.makeText(app, "Track diduplikasi ke Track $targetId", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(app, "Gagal menduplikasi: Track klobing penuh (maks 5 tambahan).", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteTrack(trackId: Int) {
        // Safe channel clear or reset to default
        val list = _tracks.value.map {
            if (it.id == trackId) {
                if (it.group == TrackGroup.ORIGINAL) {
                    it.copy(
                        name = it.defaultInstrument,
                        instrumentName = it.defaultInstrument,
                        volume = 0.8f,
                        pan = 0.0f,
                        isMuted = false,
                        isSoloed = false,
                        isCloned = false,
                        currentStyle = "Pop"
                    )
                } else {
                    it.copy(
                        name = "Tambah Instrumen",
                        instrumentName = "Muted Space",
                        volume = 0.0f,
                        pan = 0.0f,
                        isMuted = false,
                        isSoloed = false,
                        colorHex = "#4A5568",
                        waveformPoints = emptyList(),
                        isCloned = false
                    )
                }
            } else it
        }
        _tracks.value = list
        synthesizer.updateTracks(list)
        Toast.makeText(app, "Track disetel ulang", Toast.LENGTH_SHORT).show()
    }

    fun injectLibraryInstrument(trackTargetId: Int, instrumentName: String) {
        val list = _tracks.value.map {
            if (it.id == trackTargetId) {
                it.copy(
                    name = instrumentName,
                    instrumentName = instrumentName,
                    volume = 0.8f, // Turn on volume
                    colorHex = getChannelColor(trackTargetId),
                    waveformPoints = generateWaveformDummy(trackTargetId, 1.0f)
                )
            } else it
        }
        _tracks.value = list
        synthesizer.updateTracks(list)
        Toast.makeText(app, "$instrumentName ditambahkan ke Track $trackTargetId", Toast.LENGTH_SHORT).show()
    }

    // AI Style conversion technology
    fun runStyleConversion(trackId: Int, styleName: String) {
        viewModelScope.launch {
            _cloningTrackId.value = trackId
            Toast.makeText(app, "AI Cloning: Mengonversi style track $trackId ke $styleName...", Toast.LENGTH_SHORT).show()
            delay(2800) // Deep cloning wave synthesis delay simulation
            
            val list = _tracks.value.map {
                if (it.id == trackId) {
                    it.copy(
                        currentStyle = styleName,
                        instrumentName = "AI $styleName Preset",
                        isCloned = true,
                        waveformPoints = generateWaveformDummy(it.id, 1.1f)
                    )
                } else it
            }
            _tracks.value = list
            synthesizer.updateTracks(list)
            _cloningTrackId.value = null
            Toast.makeText(app, "Selesai! Waveform & generator diperbarui.", Toast.LENGTH_SHORT).show()
        }
    }

    // Studio Quality audio renderer export function
    fun startRenderAudio(format: String, quality: String, onFinished: () -> Unit) {
        viewModelScope.launch {
            _isRendering.value = true
            _renderProgress.value = 0.0f
            
            while (_renderProgress.value < 100.0f) {
                delay(120)
                _renderProgress.value += 5.0f
            }
            _renderProgress.value = 100.0f
            delay(300)
            _isRendering.value = false
            onFinished()
        }
    }

    // Gemini Assistant chat execution
    fun setChatInput(text: String) {
        _chatInputText.value = text
    }

    fun sendChatMessage() {
        val prompt = _chatInputText.value.trim()
        if (prompt.isEmpty()) return

        val userMsg = ChatMessage("Anda", prompt, true)
        _chatMessages.value = _chatMessages.value + userMsg
        _chatInputText.value = ""
        _isChatLoading.value = true

        viewModelScope.launch {
            val response = GeminiClient.generateDAWAdvice(prompt)
            val aiMsg = ChatMessage("BRO AI", response, false)
            _chatMessages.value = _chatMessages.value + aiMsg
            _isChatLoading.value = false
        }
    }

    fun quickAssistantPrompt(p: String) {
        _chatInputText.value = p
        sendChatMessage()
    }

    override fun onCleared() {
        super.onCleared()
        synthesizer.stop()
    }
}

data class ChatMessage(
    val sender: String,
    val message: String,
    val isUser: Boolean
)
