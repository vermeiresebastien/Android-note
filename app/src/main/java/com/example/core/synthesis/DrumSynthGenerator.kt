package com.example.core.synthesis

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * High-quality synthetic drum generator.
 * Produces crisp, punchy, punch-tested PCM 16-bit / 44.1kHz audio samples entirely on device.
 * No external file dependencies required.
 */
object DrumSynthGenerator {
  const val SAMPLE_RATE = 44100

  fun generateKick(durationMs: Int = 350): FloatArray {
    val totalSamples = (SAMPLE_RATE * durationMs) / 1000
    val buffer = FloatArray(totalSamples)
    var phase = 0.0

    for (i in 0 until totalSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      // Pitch drop: starts high at 160Hz and exponentially drops to 48Hz
      val freq = 48.0 + 120.0 * exp(-t * 22.0)
      phase += 2.0 * PI * freq / SAMPLE_RATE
      if (phase >= 2.0 * PI) phase -= 2.0 * PI

      // Exponential decay amplitude envelope with sharp initial click
      val amp = exp(-t * 9.0)
      val click = (exp(-t * 80.0) * (Math.random() * 2.0 - 1.0) * 0.4).toFloat()

      var sample = (sin(phase).toFloat() * 0.85f * amp.toFloat()) + click
      // Soft saturation
      sample = Math.tanh(sample * 1.4).toFloat() * 0.85f
      buffer[i] = sample
    }
    return buffer
  }

  fun generateSnare(durationMs: Int = 260): FloatArray {
    val totalSamples = (SAMPLE_RATE * durationMs) / 1000
    val buffer = FloatArray(totalSamples)
    var phase = 0.0

    for (i in 0 until totalSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      // Tone body: 210Hz dropping to 150Hz
      val freq = 150.0 + 60.0 * exp(-t * 25.0)
      phase += 2.0 * PI * freq / SAMPLE_RATE
      if (phase >= 2.0 * PI) phase -= 2.0 * PI

      val body = sin(phase).toFloat() * exp(-t * 18.0).toFloat() * 0.5f
      // Snare wire noise with fast decay
      val noise = ((Math.random() * 2.0 - 1.0) * exp(-t * 12.0) * 0.65).toFloat()
      val sample = (body + noise) * 0.85f
      buffer[i] = Math.max(-0.95f, Math.min(0.95f, sample))
    }
    return buffer
  }

  fun generateHiHatClosed(durationMs: Int = 65): FloatArray {
    val totalSamples = (SAMPLE_RATE * durationMs) / 1000
    val buffer = FloatArray(totalSamples)
    var prev = 0f

    for (i in 0 until totalSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      val rawNoise = (Math.random() * 2.0 - 1.0).toFloat()
      // High-pass filter
      val hp = rawNoise - prev * 0.85f
      prev = rawNoise

      val env = exp(-t * 60.0).toFloat()
      buffer[i] = hp * env * 0.65f
    }
    return buffer
  }

  fun generateHiHatOpen(durationMs: Int = 380): FloatArray {
    val totalSamples = (SAMPLE_RATE * durationMs) / 1000
    val buffer = FloatArray(totalSamples)
    var prev = 0f

    for (i in 0 until totalSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      val rawNoise = (Math.random() * 2.0 - 1.0).toFloat()
      val hp = rawNoise - prev * 0.85f
      prev = rawNoise

      val env = exp(-t * 9.0).toFloat()
      buffer[i] = hp * env * 0.65f
    }
    return buffer
  }

  fun generateClap(durationMs: Int = 220): FloatArray {
    val totalSamples = (SAMPLE_RATE * durationMs) / 1000
    val buffer = FloatArray(totalSamples)

    for (i in 0 until totalSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      val rawNoise = (Math.random() * 2.0 - 1.0).toFloat()

      // Multiple mini burst envelopes before the main tail
      val burst1 = if (t in 0.0..0.012) exp(-t * 120.0) else 0.0
      val burst2 = if (t in 0.012..0.024) exp(-(t - 0.012) * 120.0) else 0.0
      val burst3 = if (t >= 0.024) exp(-(t - 0.024) * 20.0) else 0.0

      val env = (burst1 + burst2 + burst3).toFloat()
      buffer[i] = rawNoise * env * 0.65f
    }
    return buffer
  }

  fun generateTom(durationMs: Int = 300, startFreq: Double = 180.0, endFreq: Double = 75.0): FloatArray {
    val totalSamples = (SAMPLE_RATE * durationMs) / 1000
    val buffer = FloatArray(totalSamples)
    var phase = 0.0

    for (i in 0 until totalSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      val freq = endFreq + (startFreq - endFreq) * exp(-t * 15.0)
      phase += 2.0 * PI * freq / SAMPLE_RATE
      if (phase >= 2.0 * PI) phase -= 2.0 * PI

      val env = exp(-t * 8.0).toFloat()
      buffer[i] = sin(phase).toFloat() * env * 0.8f
    }
    return buffer
  }

  fun generateRim(durationMs: Int = 70): FloatArray {
    val totalSamples = (SAMPLE_RATE * durationMs) / 1000
    val buffer = FloatArray(totalSamples)
    var phase = 0.0

    for (i in 0 until totalSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      phase += 2.0 * PI * 1400.0 / SAMPLE_RATE
      val env = exp(-t * 70.0).toFloat()
      val noise = (Math.random() * 2.0 - 1.0).toFloat() * 0.3f
      buffer[i] = (sin(phase).toFloat() * 0.7f + noise) * env * 0.8f
    }
    return buffer
  }

  fun generatePerc(durationMs: Int = 110): FloatArray {
    val totalSamples = (SAMPLE_RATE * durationMs) / 1000
    val buffer = FloatArray(totalSamples)
    var phase1 = 0.0
    var phase2 = 0.0

    for (i in 0 until totalSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      phase1 += 2.0 * PI * 880.0 / SAMPLE_RATE
      phase2 += 2.0 * PI * 1320.0 / SAMPLE_RATE
      val env = exp(-t * 35.0).toFloat()
      buffer[i] = (sin(phase1).toFloat() * 0.5f + sin(phase2).toFloat() * 0.5f) * env * 0.7f
    }
    return buffer
  }

  val defaultSampleMap: Map<String, FloatArray> by lazy {
    mapOf(
      "kick" to generateKick(350),
      "snare" to generateSnare(260),
      "hihat_closed" to generateHiHatClosed(65),
      "hihat_open" to generateHiHatOpen(380),
      "clap" to generateClap(220),
      "tom_low" to generateTom(320, 140.0, 55.0),
      "tom_mid" to generateTom(280, 210.0, 85.0),
      "tom_hi" to generateTom(240, 280.0, 115.0),
      "rim" to generateRim(70),
      "perc" to generatePerc(110)
    )
  }
}
