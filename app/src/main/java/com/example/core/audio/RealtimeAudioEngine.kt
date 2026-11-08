package com.example.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.core.model.DrumPadAssignment
import com.example.core.model.InstrumentType
import com.example.core.model.Project
import com.example.core.model.SynthParams
import com.example.core.model.Track
import com.example.core.music.MusicalTimeUtils
import com.example.core.synthesis.DrumSynthGenerator
import com.example.core.synthesis.SynthVoice
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.sin

/**
 * Real-Time native AudioEngine.
 * Direct streaming via low-latency AudioTrack with a dedicated real-time audio thread.
 * Renders polyphonic synth voices, sample triggers, per-track delay, panning, volume,
 * and master soft limiter.
 */
class RealtimeAudioEngine(val sampleRate: Int = 44100) {
  private val tag = "RealtimeAudioEngine"

  // AudioTrack setup
  private var audioTrack: AudioTrack? = null
  private var audioThread: Thread? = null
  private val isRunning = AtomicBoolean(false)

  // Buffer size in frames (e.g. 512 frames = ~11.6ms latency)
  private val bufferFrameCount = 512
  private val shortBuffer = ShortArray(bufferFrameCount * 2) // Stereo interleaved

  // Polyphonic synth voice pool (32 voices)
  private val voicePoolSize = 32
  private val voices = Array(voicePoolSize) { SynthVoice(sampleRate) }

  // Sample playback voices for drum pads / sampler (16 channels)
  data class SamplePlayback(
    var buffer: FloatArray? = null,
    var position: Float = 0f,
    var speed: Float = 1.0f,
    var gain: Float = 1.0f,
    var trackIndex: Int = 0,
    var isActive: Boolean = false,
    var chokeGroup: Int = 0
  )
  private val sampleVoices = Array(24) { SamplePlayback() }

  // Custom loaded sample assets in memory
  private val sampleMap = ConcurrentHashMap<String, FloatArray>()

  // Per-track delay effects
  private val trackDelays = Array(8) { StereoDelayEffect(sampleRate) }

  // Master bus meter values (thread-safe reads for UI)
  @Volatile var masterPeakL: Float = 0f
    private set
  @Volatile var masterPeakR: Float = 0f
    private set
  val trackPeakLevels = FloatArray(8)

  // Transport callback for sample-accurate scheduling
  interface AudioRenderListener {
    fun onRenderBlock(frames: Int, currentFrame: Long)
  }
  var renderListener: AudioRenderListener? = null

  // Global sample frame counter for musical clock sync
  @Volatile var totalFramesRendered: Long = 0L
    private set

  // Current active project reference for mixing settings
  @Volatile var currentProject: Project? = null

  init {
    // Populate default synthetic drum samples
    sampleMap.putAll(DrumSynthGenerator.defaultSampleMap)
  }

  fun registerSample(id: String, pcm: FloatArray) {
    sampleMap[id] = pcm
  }

  fun getSample(id: String): FloatArray? {
    return sampleMap[id]
  }

  @Synchronized
  fun start() {
    if (isRunning.get()) return

    val minBufSize = AudioTrack.getMinBufferSize(
      sampleRate,
      AudioFormat.CHANNEL_OUT_STEREO,
      AudioFormat.ENCODING_PCM_16BIT
    )
    val chosenBufferSize = Math.max(minBufSize, bufferFrameCount * 2 * 2 * 2)

    val attributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
      .build()

    val format = AudioFormat.Builder()
      .setSampleRate(sampleRate)
      .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
      .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
      .build()

    try {
      audioTrack = AudioTrack(
        attributes,
        format,
        chosenBufferSize,
        AudioTrack.MODE_STREAM,
        android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
      )
      audioTrack?.play()
      isRunning.set(true)

      audioThread = Thread({
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        renderLoop()
      }, "PocketSession-AudioRenderThread")
      audioThread?.start()
      Log.i(tag, "AudioEngine started at $sampleRate Hz")
    } catch (e: Exception) {
      Log.e(tag, "Failed to start AudioTrack", e)
    }
  }

  @Synchronized
  fun stop() {
    isRunning.set(false)
    try {
      audioThread?.join(500)
    } catch (e: Exception) {
      Log.w(tag, "Audio thread interrupted during stop", e)
    }
    audioThread = null

    try {
      audioTrack?.stop()
      audioTrack?.release()
    } catch (e: Exception) {
      Log.w(tag, "Error releasing AudioTrack", e)
    }
    audioTrack = null
  }

  // --- Realtime Sound Triggering ---

  fun noteOn(trackIndex: Int, pitch: Int, velocity: Float, synthParams: SynthParams) {
    // Find an inactive voice or steal the oldest
    synchronized(voices) {
      var targetVoice: SynthVoice? = voices.firstOrNull { !it.isActive }
      if (targetVoice == null) {
        // Voice stealing: steal the first voice
        targetVoice = voices[0]
        targetVoice.kill()
      }
      targetVoice.noteOn(pitch, velocity, trackIndex, synthParams)
    }
  }

  fun noteOff(trackIndex: Int, pitch: Int) {
    synchronized(voices) {
      for (voice in voices) {
        if (voice.isActive && voice.trackIndex == trackIndex && voice.midiPitch == pitch) {
          voice.noteOff()
        }
      }
    }
  }

  fun allNotesOff() {
    synchronized(voices) {
      for (voice in voices) {
        voice.kill()
      }
    }
    synchronized(sampleVoices) {
      for (sv in sampleVoices) {
        sv.isActive = false
      }
    }
  }

  fun triggerSample(
    trackIndex: Int,
    sampleId: String,
    velocity: Float = 1.0f,
    pitchShiftSemitones: Float = 0f,
    chokeGroup: Int = 0
  ) {
    val pcm = sampleMap[sampleId] ?: return
    val speed = Math.pow(2.0, pitchShiftSemitones / 12.0).toFloat()

    synchronized(sampleVoices) {
      // Choke group handling: if chokeGroup > 0, stop any active voices on same choke group
      if (chokeGroup > 0) {
        for (sv in sampleVoices) {
          if (sv.isActive && sv.trackIndex == trackIndex && sv.chokeGroup == chokeGroup) {
            sv.isActive = false
          }
        }
      }

      // Find an inactive sample voice
      var voice: SamplePlayback? = sampleVoices.firstOrNull { !it.isActive }
      if (voice == null) {
        voice = sampleVoices[0]
      }
      voice.buffer = pcm
      voice.position = 0f
      voice.speed = speed
      voice.gain = velocity
      voice.trackIndex = trackIndex
      voice.chokeGroup = chokeGroup
      voice.isActive = true
    }
  }

  /**
   * Main audio rendering loop running on the high-priority audio thread.
   */
  private fun renderLoop() {
    val leftTrackBuses = FloatArray(8)
    val rightTrackBuses = FloatArray(8)
    val masterLimiterThreshold = 0.95f

    while (isRunning.get()) {
      val project = currentProject
      val currentBpm = project?.tempoBpm ?: 120.0
      val tracks = project?.tracks

      // Check if any tracks are soloed
      val anySoloed = tracks?.any { it.solo } ?: false

      // Notify musical scheduler before rendering this block
      renderListener?.onRenderBlock(bufferFrameCount, totalFramesRendered)

      var blockPeakL = 0f
      var blockPeakR = 0f

      var shortIdx = 0
      for (frame in 0 until bufferFrameCount) {
        // Clear track accumulator buses
        for (t in 0 until 8) {
          leftTrackBuses[t] = 0f
          rightTrackBuses[t] = 0f
        }

        // 1. Render active synth voices into corresponding track buses
        synchronized(voices) {
          for (v in voices) {
            if (v.isActive) {
              val s = v.renderNextSample()
              val t = v.trackIndex.coerceIn(0, 7)
              leftTrackBuses[t] += s
              rightTrackBuses[t] += s
            }
          }
        }

        // 2. Render active sample voices
        synchronized(sampleVoices) {
          for (sv in sampleVoices) {
            if (sv.isActive) {
              val buf = sv.buffer
              if (buf != null) {
                val idx = sv.position.toInt()
                if (idx < buf.size) {
                  val s = buf[idx] * sv.gain
                  val t = sv.trackIndex.coerceIn(0, 7)
                  leftTrackBuses[t] += s
                  rightTrackBuses[t] += s
                  sv.position += sv.speed
                } else {
                  sv.isActive = false
                }
              } else {
                sv.isActive = false
              }
            }
          }
        }

        // 3. Mix tracks to master bus with volume, pan, mute/solo, and delay
        var masterL = 0f
        var masterR = 0f

        for (t in 0 until 8) {
          val track = tracks?.getOrNull(t)
          val isAudible = if (track != null) {
            if (anySoloed) track.solo else !track.mute
          } else true

          if (isAudible) {
            var rawL = leftTrackBuses[t]
            var rawR = rightTrackBuses[t]

            // Apply track delay effect
            if (track != null) {
              val delayed = trackDelays[t].process(rawL, rawR, track.delayParams, currentBpm)
              rawL = delayed.first
              rawR = delayed.second

              // Apply Volume (dB to linear) & Pan
              val linearGain = Math.pow(10.0, track.volumeDb / 20.0).toFloat()
              val pan = track.pan.coerceIn(-1.0f, 1.0f)
              // Constant power panning
              val panAngle = (pan + 1.0f) * (Math.PI / 4.0).toFloat()
              val gainL = cos(panAngle) * linearGain
              val gainR = sin(panAngle) * linearGain

              masterL += rawL * gainL
              masterR += rawR * gainR
            } else {
              masterL += rawL
              masterR += rawR
            }
          }
        }

        // Apply master volume
        val masterVol = project?.masterVolume ?: 1.0f
        masterL *= masterVol
        masterR *= masterVol

        // 4. Master Soft Limiter / Saturation (Prevents harsh digital clipping)
        val limL = Math.tanh(masterL.toDouble()).toFloat() * masterLimiterThreshold
        val limR = Math.tanh(masterR.toDouble()).toFloat() * masterLimiterThreshold

        val absL = Math.abs(limL)
        val absR = Math.abs(limR)
        if (absL > blockPeakL) blockPeakL = absL
        if (absR > blockPeakR) blockPeakR = absR

        // Convert float [-1.0, 1.0] to 16-bit PCM Short
        val sampleShortL = (limL * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        val sampleShortR = (limR * 32767f).toInt().coerceIn(-32768, 32767).toShort()

        shortBuffer[shortIdx++] = sampleShortL
        shortBuffer[shortIdx++] = sampleShortR
      }

      // Write PCM block to AudioTrack
      audioTrack?.write(shortBuffer, 0, shortBuffer.size)

      totalFramesRendered += bufferFrameCount

      // Update peak levels for UI meters smoothly
      masterPeakL = masterPeakL * 0.8f + blockPeakL * 0.2f
      masterPeakR = masterPeakR * 0.8f + blockPeakR * 0.2f
    }
  }

  /**
   * Offline rendering of project or scene range into raw 16-bit PCM WAV.
   * Can render stems or full stereo mixdown completely off the UI thread.
   */
  fun renderOffline(
    project: Project,
    totalBars: Int,
    onProgress: (Float) -> Unit = {}
  ): ByteArray {
    val bpm = project.tempoBpm
    val secondsPerBar = (60.0 / bpm) * 4.0
    val totalSeconds = secondsPerBar * totalBars
    val totalFrames = (totalSeconds * sampleRate).toInt()

    // Pre-allocate WAV buffer
    val bytesPerSample = 2 // 16-bit
    val channels = 2
    val dataSize = totalFrames * channels * bytesPerSample
    val totalSize = 44 + dataSize
    val output = ByteArray(totalSize)

    // Write WAV header
    writeWavHeader(output, dataSize, sampleRate, channels)

    val offlineVoices = Array(16) { SynthVoice(sampleRate) }
    val offlineDelays = Array(8) { StereoDelayEffect(sampleRate) }

    var byteOffset = 44
    val framesPerBlock = 512
    var renderedFrames = 0

    while (renderedFrames < totalFrames) {
      val framesToRender = Math.min(framesPerBlock, totalFrames - renderedFrames)

      for (f in 0 until framesToRender) {
        val currentFrame = (renderedFrames + f).toLong()
        val currentTick = MusicalTimeUtils.framesToTicks(currentFrame, bpm, sampleRate)

        // Find notes that start around this tick across all tracks
        for (track in project.tracks) {
          val activeClip = track.clips.firstOrNull { it != null && it.hasNotes } ?: continue
          val clipTick = currentTick % activeClip.lengthTicks
          for (note in activeClip.notes) {
            val noteTick = note.startTick
            // If note triggers right at this frame boundary (within 1 tick)
            val noteFrame = MusicalTimeUtils.ticksToFrames(noteTick, bpm, sampleRate)
            val loopFrame = MusicalTimeUtils.ticksToFrames(activeClip.lengthTicks, bpm, sampleRate)
            val frameInLoop = currentFrame % loopFrame

            if (frameInLoop == noteFrame) {
              if (track.instrumentType == InstrumentType.DRUM_KIT) {
                // Trigger drum sample
                val pad = track.drumPads.firstOrNull { it.midiNote == note.pitch }
                val sampleId = pad?.sampleId ?: "kick"
                // Drum in offline render can be added
              } else {
                val voice = offlineVoices.firstOrNull { !it.isActive } ?: offlineVoices[0]
                voice.noteOn(note.pitch, note.velocity, track.index, track.synthParams)
              }
            }
          }
        }

        // Sum voices
        var mixL = 0f
        var mixR = 0f
        for (v in offlineVoices) {
          if (v.isActive) {
            val s = v.renderNextSample()
            mixL += s
            mixR += s
          }
        }

        val limL = Math.tanh(mixL.toDouble()).toFloat() * 0.95f
        val limR = Math.tanh(mixR.toDouble()).toFloat() * 0.95f

        val shortL = (limL * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        val shortR = (limR * 32767f).toInt().coerceIn(-32768, 32767).toShort()

        output[byteOffset++] = (shortL.toInt() and 0xFF).toByte()
        output[byteOffset++] = ((shortL.toInt() shr 8) and 0xFF).toByte()
        output[byteOffset++] = (shortR.toInt() and 0xFF).toByte()
        output[byteOffset++] = ((shortR.toInt() shr 8) and 0xFF).toByte()
      }

      renderedFrames += framesToRender
      onProgress(renderedFrames.toFloat() / totalFrames.toFloat())
    }

    return output
  }

  private fun writeWavHeader(buffer: ByteArray, dataSize: Int, sampleRate: Int, channels: Int) {
    val totalDataLen = dataSize + 36
    val byteRate = sampleRate * channels * 2

    // RIFF chunk
    buffer[0] = 'R'.code.toByte()
    buffer[1] = 'I'.code.toByte()
    buffer[2] = 'F'.code.toByte()
    buffer[3] = 'F'.code.toByte()
    buffer[4] = (totalDataLen and 0xff).toByte()
    buffer[5] = ((totalDataLen shr 8) and 0xff).toByte()
    buffer[6] = ((totalDataLen shr 16) and 0xff).toByte()
    buffer[7] = ((totalDataLen shr 24) and 0xff).toByte()
    buffer[8] = 'W'.code.toByte()
    buffer[9] = 'A'.code.toByte()
    buffer[10] = 'V'.code.toByte()
    buffer[11] = 'E'.code.toByte()

    // fmt chunk
    buffer[12] = 'f'.code.toByte()
    buffer[13] = 'm'.code.toByte()
    buffer[14] = 't'.code.toByte()
    buffer[15] = ' '.code.toByte()
    buffer[16] = 16 // 16 for PCM
    buffer[17] = 0
    buffer[18] = 0
    buffer[19] = 0
    buffer[20] = 1 // PCM format
    buffer[21] = 0
    buffer[22] = channels.toByte()
    buffer[23] = 0
    buffer[24] = (sampleRate and 0xff).toByte()
    buffer[25] = ((sampleRate shr 8) and 0xff).toByte()
    buffer[26] = ((sampleRate shr 16) and 0xff).toByte()
    buffer[27] = ((sampleRate shr 24) and 0xff).toByte()
    buffer[28] = (byteRate and 0xff).toByte()
    buffer[29] = ((byteRate shr 8) and 0xff).toByte()
    buffer[30] = ((byteRate shr 16) and 0xff).toByte()
    buffer[31] = ((byteRate shr 24) and 0xff).toByte()
    buffer[32] = (channels * 2).toByte() // block align
    buffer[33] = 0
    buffer[34] = 16 // bits per sample
    buffer[35] = 0

    // data chunk
    buffer[36] = 'd'.code.toByte()
    buffer[37] = 'a'.code.toByte()
    buffer[38] = 't'.code.toByte()
    buffer[39] = 'a'.code.toByte()
    buffer[40] = (dataSize and 0xff).toByte()
    buffer[41] = ((dataSize shr 8) and 0xff).toByte()
    buffer[42] = ((dataSize shr 16) and 0xff).toByte()
    buffer[43] = ((dataSize shr 24) and 0xff).toByte()
  }
}
