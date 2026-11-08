package com.example.core.scheduler

import com.example.core.audio.RealtimeAudioEngine
import com.example.core.model.Clip
import com.example.core.model.InstrumentType
import com.example.core.model.LaunchQuantization
import com.example.core.model.NoteEvent
import com.example.core.model.Project
import com.example.core.music.MusicalTimeUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * High-precision Master Musical Clock and Event Scheduler.
 * Driven directly by sample-frame callbacks from the real-time audio thread,
 * ensuring zero drift, sample-accurate loop boundaries, and musical clip launching.
 */
class MusicalScheduler(private val audioEngine: RealtimeAudioEngine) : RealtimeAudioEngine.AudioRenderListener {

  // Transport state
  @Volatile var isPlaying: Boolean = false
    private set

  @Volatile var isRecording: Boolean = false
    private set

  // The active clip currently playing for each of the 8 tracks (null = stopped)
  private val activeClips = arrayOfNulls<Clip>(8)

  // Queued clips waiting for next launch quantization boundary
  private val pendingClips = arrayOfNulls<Clip>(8)
  private val pendingStopTracks = BooleanArray(8)

  // Retrospective MIDI capture ring buffer
  data class CapturedNote(
    val pitch: Int,
    val velocity: Float,
    val startTick: Long,
    val durationTicks: Long,
    val trackIndex: Int
  )
  private val captureBuffer = CopyOnWriteArrayList<CapturedNote>()
  private val maxCaptureEvents = 500

  // Active held notes during live performance for duration measurement
  private val activeLiveNotes = ConcurrentHashMap<Pair<Int, Int>, Long>() // (track, pitch) -> pressStartTick

  // Monotonic musical position
  @Volatile var currentTick: Long = 0L
    private set

  // Listeners for UI playhead synchronization
  interface TransportListener {
    fun onPositionChanged(bar: Int, beat: Int, tickInBeat: Long)
    fun onClipLaunch(trackIndex: Int, clip: Clip?)
    fun onPlayStateChanged(playing: Boolean)
  }
  var transportListener: TransportListener? = null

  // Active sounding notes scheduled for note-off: (noteOffTick, trackIndex, pitch)
  private val pendingNoteOffs = CopyOnWriteArrayList<Triple<Long, Int, Int>>()

  init {
    audioEngine.renderListener = this
  }

  fun play() {
    if (!isPlaying) {
      isPlaying = true
      currentTick = 0L
      audioEngine.start()
      transportListener?.onPlayStateChanged(true)
    }
  }

  fun stop() {
    isPlaying = false
    isRecording = false
    allNotesOff()
    pendingNoteOffs.clear()
    for (i in 0 until 8) {
      pendingClips[i] = null
      pendingStopTracks[i] = false
    }
    transportListener?.onPlayStateChanged(false)
  }

  fun togglePlay() {
    if (isPlaying) stop() else play()
  }

  fun toggleRecord() {
    isRecording = !isRecording
    if (isRecording && !isPlaying) {
      play()
    }
  }

  fun queueClipLaunch(trackIndex: Int, clip: Clip?, quantization: LaunchQuantization = LaunchQuantization.BAR) {
    if (!isPlaying) {
      // If stopped, launch immediately and start playback
      activeClips[trackIndex] = clip
      play()
      transportListener?.onClipLaunch(trackIndex, clip)
      return
    }

    if (quantization == LaunchQuantization.NONE) {
      activeClips[trackIndex] = clip
      transportListener?.onClipLaunch(trackIndex, clip)
    } else {
      pendingClips[trackIndex] = clip
    }
  }

  fun queueSceneLaunch(sceneIndex: Int, project: Project) {
    for (track in project.tracks) {
      val clip = track.clips.getOrNull(sceneIndex)
      queueClipLaunch(track.index, clip, project.launchQuantization)
    }
  }

  fun stopTrack(trackIndex: Int, quantization: LaunchQuantization = LaunchQuantization.BAR) {
    if (quantization == LaunchQuantization.NONE || !isPlaying) {
      activeClips[trackIndex] = null
      transportListener?.onClipLaunch(trackIndex, null)
    } else {
      pendingStopTracks[trackIndex] = true
    }
  }

  fun getActiveClip(trackIndex: Int): Clip? {
    return if (trackIndex in 0..7) activeClips[trackIndex] else null
  }

  fun getPendingClip(trackIndex: Int): Clip? {
    return if (trackIndex in 0..7) pendingClips[trackIndex] else null
  }

  // --- Real-Time Performance & Capture ---

  fun recordLiveNoteOn(trackIndex: Int, pitch: Int, velocity: Float) {
    activeLiveNotes[Pair(trackIndex, pitch)] = currentTick
  }

  fun recordLiveNoteOff(trackIndex: Int, pitch: Int) {
    val start = activeLiveNotes.remove(Pair(trackIndex, pitch)) ?: return
    val duration = (currentTick - start).coerceAtLeast(120L) // Min 32nd note

    val captured = CapturedNote(pitch, 0.85f, start, duration, trackIndex)
    captureBuffer.add(captured)
    if (captureBuffer.size > maxCaptureEvents) {
      captureBuffer.removeAt(0)
    }
  }

  /**
   * Retrospectively captures notes played on a track into a new Clip.
   */
  fun retrospectiveCapture(trackIndex: Int, lengthBars: Int = 4): List<NoteEvent> {
    val lengthTicks = lengthBars * MusicalTimeUtils.TICKS_PER_BAR
    val trackNotes = captureBuffer.filter { it.trackIndex == trackIndex }
    if (trackNotes.isEmpty()) return emptyList()

    // Find the latest cluster of notes
    val latestTick = trackNotes.last().startTick
    val startWindow = (latestTick - lengthTicks).coerceAtLeast(0L)

    val capturedEvents = mutableListOf<NoteEvent>()
    for (note in trackNotes) {
      if (note.startTick in startWindow..latestTick) {
        val relTick = (note.startTick - startWindow) % lengthTicks
        capturedEvents.add(
          NoteEvent(
            pitch = note.pitch,
            velocity = note.velocity,
            startTick = relTick,
            durationTicks = note.durationTicks.coerceAtMost(lengthTicks - relTick)
          )
        )
      }
    }
    return capturedEvents
  }

  fun clearCapture() {
    captureBuffer.clear()
  }

  private fun allNotesOff() {
    audioEngine.allNotesOff()
  }

  /**
   * Executed on Audio Render Thread before each buffer block is processed.
   * Frame-to-tick math ensures accurate loop timing and quantization.
   */
  override fun onRenderBlock(frames: Int, currentFrame: Long) {
    if (!isPlaying) return

    val project = audioEngine.currentProject ?: return
    val bpm = project.tempoBpm
    val sampleRate = audioEngine.sampleRate

    val startTick = MusicalTimeUtils.framesToTicks(currentFrame, bpm, sampleRate)
    val endTick = MusicalTimeUtils.framesToTicks(currentFrame + frames, bpm, sampleRate)

    // Process Launch Quantization Boundaries
    val quantTicks = when (project.launchQuantization) {
      LaunchQuantization.NONE -> 1L
      LaunchQuantization.BEAT -> MusicalTimeUtils.TICKS_PER_BEAT
      LaunchQuantization.BAR -> MusicalTimeUtils.TICKS_PER_BAR
    }

    if (quantTicks > 1L) {
      // Check if a quantization boundary falls within [startTick, endTick)
      val prevBoundary = startTick / quantTicks
      val nextBoundary = endTick / quantTicks
      if (nextBoundary > prevBoundary) {
        // Boundary crossed! Swap pending clips
        for (t in 0 until 8) {
          if (pendingClips[t] != null) {
            activeClips[t] = pendingClips[t]
            pendingClips[t] = null
            transportListener?.onClipLaunch(t, activeClips[t])
          }
          if (pendingStopTracks[t]) {
            activeClips[t] = null
            pendingStopTracks[t] = false
            transportListener?.onClipLaunch(t, null)
          }
        }
      }
    }

    // Process Note-Off events due in this window
    val expiredOffs = mutableListOf<Triple<Long, Int, Int>>()
    for (off in pendingNoteOffs) {
      if (off.first in startTick..endTick) {
        audioEngine.noteOff(off.second, off.third)
        expiredOffs.add(off)
      }
    }
    pendingNoteOffs.removeAll(expiredOffs)

    // Schedule active clips note events for all 8 tracks
    val tracks = project.tracks
    for (t in 0 until 8) {
      val clip = activeClips[t] ?: continue
      if (!clip.hasNotes) continue

      val clipLength = clip.lengthTicks.coerceAtLeast(MusicalTimeUtils.TICKS_PER_BAR)
      val track = tracks.getOrNull(t) ?: continue

      for (note in clip.notes) {
        // Find if note startTick falls inside current window when wrapped to loop
        val noteTickInLoop = note.startTick % clipLength
        val windowStartInLoop = startTick % clipLength
        val windowEndInLoop = endTick % clipLength

        val noteHits = if (windowStartInLoop <= windowEndInLoop) {
          noteTickInLoop in windowStartInLoop until windowEndInLoop
        } else {
          // Wrapped around loop boundary in this block
          noteTickInLoop >= windowStartInLoop || noteTickInLoop < windowEndInLoop
        }

        if (noteHits) {
          // Trigger Sound
          if (track.instrumentType == InstrumentType.DRUM_KIT) {
            val pad = track.drumPads.firstOrNull { it.midiNote == note.pitch }
            val sampleId = pad?.sampleId ?: "kick"
            audioEngine.triggerSample(
              t,
              sampleId,
              note.velocity * (pad?.gain ?: 1f),
              pad?.pitchShiftSemitones ?: 0f,
              pad?.chokeGroup ?: 0
            )
          } else {
            audioEngine.noteOn(t, note.pitch, note.velocity, track.synthParams)
            // Schedule note off
            val noteOffTick = startTick + note.durationTicks
            pendingNoteOffs.add(Triple(noteOffTick, t, note.pitch))
          }
        }
      }
    }

    currentTick = endTick

    // Update transport listener
    val currentBar = (currentTick / MusicalTimeUtils.TICKS_PER_BAR).toInt() + 1
    val tickInBar = currentTick % MusicalTimeUtils.TICKS_PER_BAR
    val currentBeat = (tickInBar / MusicalTimeUtils.TICKS_PER_BEAT).toInt() + 1
    val tickInBeat = tickInBar % MusicalTimeUtils.TICKS_PER_BEAT

    transportListener?.onPositionChanged(currentBar, currentBeat, tickInBeat)
  }
}
