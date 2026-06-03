package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.nio.ByteBuffer

object AudioDecoder {
    fun decodeToPcm(
        context: Context,
        uriString: String,
        targetSampleRate: Int,
        targetChannels: Int,
        maxSeconds: Int = 45
    ): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: return null

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }

            if (trackIndex == -1 || format == null) {
                extractor.release()
                return null
            }

            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false
            val timeoutUs = 5000L

            val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val decodedData = ArrayList<Float>()
            val maxOutSamples = targetSampleRate * targetChannels * maxSeconds

            // Simple loop to pull raw PCM short packets
            while (!isOutputEOS && decodedData.size < maxOutSamples) {
                if (!isInputEOS) {
                    val inputBufIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inputBufIndex >= 0) {
                        val dstBuf = codec.getInputBuffer(inputBufIndex)
                        if (dstBuf != null) {
                            dstBuf.clear()
                            val sampleSize = extractor.readSampleData(dstBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputBufIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                isInputEOS = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(
                                    inputBufIndex,
                                    0,
                                    sampleSize,
                                    presentationTimeUs,
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, timeoutUs)
                if (outIndex >= 0) {
                    val buf = codec.getOutputBuffer(outIndex)
                    if (buf != null) {
                        val shortBuffer = buf.asShortBuffer()
                        val count = shortBuffer.remaining()
                        val tempShorts = ShortArray(count)
                        shortBuffer.get(tempShorts)

                        // Accumulate and parse samples matching source channel structure
                        for (i in 0 until count step sourceChannels) {
                            if (decodedData.size >= maxOutSamples) break
                            val leftVal = tempShorts[i] / 32768.0f
                            val rightVal = if (sourceChannels > 1 && i + 1 < count) {
                                tempShorts[i + 1] / 32768.0f
                            } else {
                                leftVal
                            }
                            decodedData.add(leftVal)
                            decodedData.add(rightVal)
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                }
            }

            val resampledData = FloatArray(decodedData.size)
            for (i in 0 until decodedData.size) {
                resampledData[i] = decodedData[i]
            }

            // Perform simple, high-performance linear resampling in float domains if native sample rate diverges
            if (sourceSampleRate != targetSampleRate && resampledData.isNotEmpty()) {
                val ratio = sourceSampleRate.toDouble() / targetSampleRate.toDouble()
                val targetLength = (((resampledData.size / 2) / ratio).toInt() * 2) - 2
                if (targetLength > 0) {
                    val finalPcm = FloatArray(targetLength)
                    for (frame in 0 until targetLength / 2) {
                        val sourceFrame = (frame * ratio).toInt()
                        if (sourceFrame * 2 + 1 < resampledData.size) {
                            finalPcm[frame * 2] = resampledData[sourceFrame * 2]
                            finalPcm[frame * 2 + 1] = resampledData[sourceFrame * 2 + 1]
                        }
                    }
                    return finalPcm
                }
            }

            return resampledData
        } catch (e: Exception) {
            Log.e("AudioDecoder", "Error decoding audio file to PCM", e)
            return null
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                extractor.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
