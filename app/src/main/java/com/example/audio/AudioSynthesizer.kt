package com.example.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.example.model.ChannelTrack
import com.example.model.TrackGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sin
import kotlin.random.Random

class AudioSynthesizer {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var synthJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    
    // Thread-safe copy of tracks being rendered in active synthesis
    private var synthTracks: List<ChannelTrack> = emptyList()

    // Decoded imported audio PCM buffer
    @Volatile
    private var importedPcm: FloatArray? = null

    fun setImportedPcm(pcm: FloatArray?) {
        importedPcm = pcm
    }
    
    // Waveform frequencies and counters
    private var sampleRate = 22050
    private var activeAudioTrack: AudioTrack? = null

    // For real-time Master peak output levels
    private val _masterVULeft = MutableStateFlow(0.0f)
    val masterVULeft = _masterVULeft.asStateFlow()

    private val _masterVURight = MutableStateFlow(0.0f)
    val masterVURight = _masterVURight.asStateFlow()

    // Peak clipping check
    private val _isClipping = MutableStateFlow(false)
    val isClipping = _isClipping.asStateFlow()

    // Real-time Spectrum levels (8 frequency bands) for the spectrum analyzer
    private val _spectrumBands = MutableStateFlow(FloatArray(8) { 0.0f })
    val spectrumBands = _spectrumBands.asStateFlow()

    // Waveform visualization points
    private val _liveWaveform = MutableStateFlow(FloatArray(30) { 0.0f })
    val liveWaveform = _liveWaveform.asStateFlow()

    // Update tracks state safely
    fun updateTracks(tracks: List<ChannelTrack>) {
        synthTracks = ArrayList(tracks)
    }

    fun start() {
        if (isRunning.getAndSet(true)) return
        
        synthJob = scope.launch {
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            val trackBufferSize = if (minBufSize > 0) minBufSize else 4096
            
            try {
                activeAudioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    trackBufferSize,
                    AudioTrack.MODE_STREAM
                )
                
                activeAudioTrack?.play()
            } catch (e: Exception) {
                Log.e("AudioSynthesizer", "Failed to init AudioTrack: ${e.message}")
                isRunning.set(false)
                return@launch
            }

            val bufferSizeSamples = 512 // Stereo frames (1024 shorts total)
            val outputBuffer = ShortArray(bufferSizeSamples * 2)
            var globalSampleCount = 0L

            // Simple synthesizer patterns
            while (isRunning.get()) {
                val tracks = synthTracks
                
                // Determine if there is any Solo track active
                val isAnySoloActive = tracks.any { it.isSoloed }

                // Multi-track synth mixer
                for (frame in 0 until bufferSizeSamples) {
                    var sumLeft = 0.0f
                    var sumRight = 0.0f
                    val t = globalSampleCount / sampleRate.toFloat()

                    tracks.forEach { track ->
                        // If track is muted or another track is soloed (and this track is not soloed), skip synthesizing
                        if (track.isMuted) return@forEach
                        if (isAnySoloActive && !track.isSoloed) return@forEach

                        // Generate wave sample based on target channel
                        val sampleVal = generateInstrumentSample(track.id, t, globalSampleCount, track.currentStyle)
                        
                        // Compute volume envelope
                        val currentVolume = track.volume

                        // Apply panning formula (Constant power panning approximation)
                        // pan goes from -1.0f (Left) to 1.0f (Right)
                        val p = track.pan
                        val volL = currentVolume * (1.0f - p).coerceIn(0.0f, 2.0f) / 2.0f
                        val volR = currentVolume * (1.0f + p).coerceIn(0.0f, 2.0f) / 2.0f

                        sumLeft += sampleVal * volL
                        sumRight += sampleVal * volR
                    }

                    // Master gain limiter to avoid severe clipping
                    sumLeft = sumLeft.coerceIn(-1.0f, 1.0f)
                    sumRight = sumRight.coerceIn(-1.0f, 1.0f)

                    // Write short values (-32768 to 32767)
                    outputBuffer[frame * 2] = (sumLeft * 32767.0f).toInt().toShort()
                    outputBuffer[frame * 2 + 1] = (sumRight * 32767.0f).toInt().toShort()
                    
                    globalSampleCount++
                }

                // Analyze buffer to write real-time VU meter values
                var maxAbsLeft = 0.0f
                var maxAbsRight = 0.0f
                val size = bufferSizeSamples
                
                for (frame in 0 until size) {
                    val lVal = kotlin.math.abs(outputBuffer[frame * 2] / 32767.0f)
                    val rVal = kotlin.math.abs(outputBuffer[frame * 2 + 1] / 32767.0f)
                    if (lVal > maxAbsLeft) maxAbsLeft = lVal
                    if (rVal > maxAbsRight) maxAbsRight = rVal
                }

                // Smooth out the meters
                _masterVULeft.value = _masterVULeft.value * 0.7f + maxAbsLeft * 0.3f
                _masterVURight.value = _masterVURight.value * 0.7f + maxAbsRight * 0.3f
                _isClipping.value = (_masterVULeft.value > 0.95f || _masterVURight.value > 0.95f)

                // Generate fake spectrum band levels reflecting the master sound
                val bands = FloatArray(8)
                val totalPower = (_masterVULeft.value + _masterVURight.value) / 2.0f
                for (i in 0..7) {
                    val bVal = totalPower * (0.3f + 0.7f * sin(globalSampleCount / 200.0f + i * 1.5f))
                    bands[i] = bVal.coerceIn(0.0f, 1.0f)
                }
                _spectrumBands.value = bands

                // Generate live scrolling wave lines in timeline
                val livePoints = FloatArray(30)
                for (i in 0..29) {
                    livePoints[i] = _masterVULeft.value * sin(globalSampleCount / 100.0f + i * 0.5f)
                }
                _liveWaveform.value = livePoints

                // Write to AudioTrack buffer
                activeAudioTrack?.write(outputBuffer, 0, outputBuffer.size)
            }

            // Cleanup track when player stops
            try {
                activeAudioTrack?.stop()
                activeAudioTrack?.release()
                activeAudioTrack = null
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun generateInstrumentSample(trackId: Int, t: Float, sampleCount: Long, style: String): Float {
        val pcm = importedPcm
        if (pcm != null && pcm.isNotEmpty()) {
            val totalFrames = pcm.size / 2
            if (totalFrames > 0) {
                val cycleVal = 0.5f
                val beatPhaseVal = (t % cycleVal) / cycleVal
                return when (trackId) {
                    1 -> { // VOCAL preset: Plays original centered audio
                        val frameIdx = (sampleCount % totalFrames).toInt()
                        val l = pcm[frameIdx * 2]
                        val r = pcm[frameIdx * 2 + 1]
                        (l + r) * 0.55f
                    }
                    2 -> { // GUITAR preset: Plays 1.5x speed with higher pass filter (chorus/plucked feel)
                        val frameIdx = ((sampleCount * 3 / 2) % totalFrames).toInt()
                        val l = pcm[frameIdx * 2]
                        val r = pcm[frameIdx * 2 + 1]
                        val raw = (l + r) * 0.5f
                        val prevIdx = ((frameIdx - 1 + totalFrames) % totalFrames).toInt()
                        val prevL = pcm[prevIdx * 2]
                        val prevR = pcm[prevIdx * 2 + 1]
                        val prevRaw = (prevL + prevR) * 0.5f
                        (raw - prevRaw * 0.8f) * 0.85f
                    }
                    3 -> { // MAIN INSTRUMENT: Alternating pitch shift chord pads
                        val pitchShift = 1.25f
                        val frameIdx = ((sampleCount * pitchShift).toLong() % totalFrames).toInt()
                        val l = pcm[frameIdx * 2]
                        val r = pcm[frameIdx * 2 + 1]
                        (l + r) * 0.45f
                    }
                    4 -> { // BASS tracks: Plays half speed (1 octave lower) + low pass filter
                        val frameIdx = ((sampleCount / 2) % totalFrames).toInt()
                        val nextIdx = ((frameIdx + 1) % totalFrames).toInt()
                        val l1 = pcm[frameIdx * 2]
                        val r1 = pcm[frameIdx * 2 + 1]
                        val l2 = pcm[nextIdx * 2]
                        val r2 = pcm[nextIdx * 2 + 1]
                        val sample1 = (l1 + r1) * 0.5f
                        val sample2 = (l2 + r2) * 0.5f
                        ((sample1 + sample2) * 0.5f) * 1.5f
                    }
                    5 -> { // DRUM track: Pulsing, high-pass filtered rhythm gate beats
                        val frameIdx = (sampleCount % totalFrames).toInt()
                        val l = pcm[frameIdx * 2]
                        val r = pcm[frameIdx * 2 + 1]
                        val sample = (l + r) * 0.5f
                        val prevIdx = ((frameIdx - 1 + totalFrames) % totalFrames).toInt()
                        val prevL = pcm[prevIdx * 2]
                        val prevR = pcm[prevIdx * 2 + 1]
                        val prevSample = (prevL + prevR) * 0.5f
                        val highPassed = sample - prevSample * 0.95f
                        val cycleFrames = (22050 * 0.5f).toLong()
                        val phase = (sampleCount % cycleFrames).toFloat() / cycleFrames.toFloat()
                        val gate = if (phase < 0.15f || (phase in 0.48f..0.55f)) 1.4f else 0.15f
                        highPassed * gate * 1.5f
                    }
                    6 -> { // Keyboard add-on
                        val frameIdx = ((sampleCount * 2) % totalFrames).toInt()
                        pcm[frameIdx * 2] * 0.4f
                    }
                    7, 8 -> { // Custom percussion
                        val frameIdx = ((sampleCount + totalFrames / 4) % totalFrames).toInt()
                        val raw = pcm[frameIdx * 2]
                        val cycleSec = 0.25f
                        val p = (t % cycleSec) / cycleSec
                        val pluck = (1.0f - p * 4.0f).coerceIn(0.0f, 1.0f)
                        raw * pluck * 0.35f
                    }
                    else -> { // Default mix ambient drone
                        val frameIdx = (sampleCount % totalFrames).toInt()
                        pcm[frameIdx * 2] * 0.2f
                    }
                }
            }
        }

        // Base rhythmic structure: beat (tempo = 120 bpm, pulse every 0.5 seconds)
        val cycle = 0.5f
        val beatPhase = (t % cycle) / cycle // 0.0 to 1.0 within beat
        val noteTrigger = beatPhase < 0.15f

        val multiplier = when (style) {
            "Metal" -> 1.5f
            "Rock" -> 1.2f
            "EDM", "House" -> 1.1f
            "Jazz", "Blues" -> 0.8f
            "Orchestral", "Cinematic" -> 0.9f
            "Dangdut" -> 1.3f
            else -> 1.0f // Pop
        }

        return when (trackId) {
            1 -> { // VOCAL preset: smooth, sliding singing whistler
                val vocalFreq = 330.0f + 110.0f * sin(t * 0.8f) + 12.0f * sin(t * 8.0f) // Soft singing vibrato
                val wave = sin(2.0f * kotlin.math.PI.toFloat() * vocalFreq * t)
                val envelope = if (style == "Dangdut") {
                    0.25f + 0.15f * sin(t * 12.0f) // Vibrato cengkok sengau
                } else {
                    0.35f
                }
                wave * envelope * multiplier
            }
            2 -> { // GUITAR preset: triangle arpeggios
                val notes = floatArrayOf(261.63f, 329.63f, 392.00f, 523.25f) // C Chord arpeggio
                val seqIndex = ((t * 4) % 4).toInt()
                val freq = notes[seqIndex]
                val decay = (1.0f - beatPhase).coerceIn(0.0f, 1.0f)
                
                var wave = if (style == "Metal") {
                    // Heavily clipped wave for distortion guitar!
                    val w = sin(2.0f * kotlin.math.PI.toFloat() * freq * t) * 4.0f
                    w.coerceIn(-0.6f, 0.6f)
                } else {
                    // Standard guitar pluck triangle
                    val base = (t * freq) % 1.0f
                    val w = if (base < 0.5f) 4.0f * base - 1.0f else 3.0f - 4.0f * base
                    w
                }
                wave * decay * 0.25f * multiplier
            }
            3 -> { // MAIN INSTRUMENT (Piano / Synths): square pads / chords
                val chordNotes = floatArrayOf(261.63f, 293.66f, 329.63f, 349.23f)
                val seqIndex = ((t / 2.0f) % 4).toInt()
                val f1 = chordNotes[seqIndex]
                val f2 = f1 * 1.5f // Perfect fifth harmony
                
                val wave1 = sin(2.0f * kotlin.math.PI.toFloat() * f1 * t)
                val wave2 = sin(2.0f * kotlin.math.PI.toFloat() * f2 * t)
                
                val out = (wave1 + wave2) * 0.15f
                if (style == "EDM" || style == "House") {
                    // Pulsing sidechain synth compressor simulation
                    val sidechain = beatPhase
                    out * sidechain * multiplier
                } else {
                    out * multiplier
                }
            }
            4 -> { // BASS tracks: sub sine wave
                val bassNotes = floatArrayOf(65.41f, 73.42f, 82.41f, 87.31f) // C2, D2, E2, F2
                val seqIndex = ((t / 2.0f) % 4).toInt()
                val freq = bassNotes[seqIndex]
                
                val wave = sin(2.0f * kotlin.math.PI.toFloat() * freq * t)
                val groove = if (style == "EDM" || style == "House" || style == "Dangdut") {
                    // Sync with beat pulse
                    if (beatPhase < 0.6f) 0.5f else 0.1f
                } else {
                    0.35f
                }
                wave * groove * multiplier
            }
            5 -> { // DRUM track: synthetic kick / snare ticks
                val kickDecay = (1.0f - beatPhase * 3.0f).coerceIn(0.0f, 1.0f)
                val kick = sin(2.0f * kotlin.math.PI.toFloat() * 60.0f * (1.0f - beatPhase) * t) * kickDecay * 0.6f
                
                // Snare white noise burst on beat index 2 (time offset)
                val isSecondBeat = ((t * 2) % 2).toInt() == 1
                val noiseDecay = (1.0f - (beatPhase) * 4.0f).coerceIn(0.0f, 1.0f)
                val snare = if (isSecondBeat && beatPhase < 0.25f) {
                    (Random.nextFloat() * 2.0f - 1.0f) * noiseDecay * 0.3f
                } else 0.0f

                // HH ticks
                val hh = if (((t * 8) % 2).toInt() == 1 && beatPhase < 0.1f) {
                    (Random.nextFloat() * 2.0f - 1.0f) * 0.08f
                } else 0.0f
                
                val rawDrum = kick + snare + hh
                
                if (style == "Dangdut") {
                    // Simulated traditional Kendang double-slap (Tak-tung-dut)
                    val kPhase = beatPhase * 2.0f % 1.0f
                    val tak = if (beatPhase < 0.2f) {
                        sin(2.0f * kotlin.math.PI.toFloat() * 1200.0f * t) * (1.0f - kPhase * 5.0f).coerceIn(0.0f, 1.0f) * 0.2f
                    } else 0.0f
                    val dut = if (beatPhase in 0.3f..0.6f) {
                        val dEnve = (1.0f - (beatPhase - 0.3f) * 3.0f).coerceIn(0.0f, 1.0f)
                        sin(2.0f * kotlin.math.PI.toFloat() * 100.0f * t) * dEnve * 0.3f
                    } else 0.0f
                    rawDrum * 0.3f + (tak + dut) * 0.6f
                } else {
                    rawDrum
                }
            }
            // Channels 6-10 are customizable based on the library presets!
            6 -> { // Custom Keyboard add-on
                val wave = sin(2.0f * kotlin.math.PI.toFloat() * 523.25f * t) // C5 tone sine
                val decay = (1.0f - beatPhase * 2.0f).coerceIn(0.0f, 1.0f)
                wave * decay * 0.25f
            }
            7, 8 -> { // Custom percussion/gamelan
                // Metallic ring pluck representing Angklung / Gamelan!
                val resFreq = 880.0f // A5 bells
                val pluck = (1.0f - beatPhase * 5.0f).coerceIn(0.0f, 1.0f)
                val bell = sin(2.0f * kotlin.math.PI.toFloat() * resFreq * t) * (1.0f + 0.3f * sin(t * 40.0f))
                bell * pluck * 0.2f
            }
            else -> { // Backup drone waves
                val freq = 440.0f
                sin(2.0f * kotlin.math.PI.toFloat() * freq * t) * 0.1f
            }
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        synthJob?.cancel()
        synthJob = null
        
        _masterVULeft.value = 0.0f
        _masterVURight.value = 0.0f
        _isClipping.value = false
        _spectrumBands.value = FloatArray(8) { 0.0f }
        _liveWaveform.value = FloatArray(30) { 0.0f }
    }
}
