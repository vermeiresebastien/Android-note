package com.example.core.music

object MusicalTimeUtils {
  const val PPQ = 960L // 960 ticks per quarter note (standard DAW resolution)
  const val TICKS_PER_BEAT = PPQ
  const val BEATS_PER_BAR = 4
  const val TICKS_PER_BAR = TICKS_PER_BEAT * BEATS_PER_BAR // 3840 ticks

  fun ticksToSeconds(ticks: Long, bpm: Double): Double {
    if (bpm <= 0.0) return 0.0
    val secondsPerBeat = 60.0 / bpm
    return (ticks.toDouble() / PPQ.toDouble()) * secondsPerBeat
  }

  fun secondsToTicks(seconds: Double, bpm: Double): Long {
    if (bpm <= 0.0) return 0L
    val secondsPerBeat = 60.0 / bpm
    return Math.round((seconds / secondsPerBeat) * PPQ.toDouble())
  }

  fun ticksToFrames(ticks: Long, bpm: Double, sampleRate: Int): Long {
    val seconds = ticksToSeconds(ticks, bpm)
    return Math.round(seconds * sampleRate)
  }

  fun framesToTicks(frames: Long, bpm: Double, sampleRate: Int): Long {
    if (sampleRate <= 0) return 0L
    val seconds = frames.toDouble() / sampleRate.toDouble()
    return secondsToTicks(seconds, bpm)
  }

  fun quantizeTick(tick: Long, gridTicks: Long): Long {
    if (gridTicks <= 1L) return tick
    val remainder = tick % gridTicks
    return if (remainder >= gridTicks / 2L) {
      tick + (gridTicks - remainder)
    } else {
      tick - remainder
    }
  }

  fun midiPitchToFrequency(pitch: Int): Float {
    // 69 = A4 = 440 Hz
    return (440.0 * Math.pow(2.0, (pitch - 69) / 12.0)).toFloat()
  }

  val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

  fun midiPitchToString(pitch: Int): String {
    val note = NOTE_NAMES[Math.floorMod(pitch, 12)]
    val octave = (pitch / 12) - 1
    return "$note$octave"
  }
}
