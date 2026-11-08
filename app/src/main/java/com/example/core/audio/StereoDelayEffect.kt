package com.example.core.audio

import com.example.core.model.DelayParams

/**
 * Stereo feedback delay line with soft saturation and low-pass dampening in feedback loop.
 */
class StereoDelayEffect(private val sampleRate: Int = 44100) {
  private val maxDelaySamples = sampleRate * 2 // up to 2 seconds
  private val bufferL = FloatArray(maxDelaySamples)
  private val bufferR = FloatArray(maxDelaySamples)
  private var writeIndex = 0

  private var dampFilterL = 0f
  private var dampFilterR = 0f

  fun process(
    inputL: Float,
    inputR: Float,
    params: DelayParams,
    bpm: Double
  ): Pair<Float, Float> {
    if (!params.enabled || params.dryWet <= 0.001f) {
      return Pair(inputL, inputR)
    }

    // If tempo sync is enabled, calculate delay time from BPM (e.g. dotted 8th = 3/16 beats)
    val effectiveDelayMs = if (params.tempoSync && bpm > 20.0) {
      // 3/16 note delay (popular dotted eighth groove)
      ((60000f / bpm.toFloat()) * 0.75f).coerceIn(40f, 1500f)
    } else {
      params.delayTimeMs.coerceIn(10f, 1800f)
    }

    val delaySamples = (effectiveDelayMs * sampleRate / 1000f).toInt().coerceIn(1, maxDelaySamples - 1)
    // Add subtle ping-pong offset between L and R (15ms difference)
    val delaySamplesR = (delaySamples * 1.08f).toInt().coerceIn(1, maxDelaySamples - 1)

    var readIndexL = writeIndex - delaySamples
    if (readIndexL < 0) readIndexL += maxDelaySamples

    var readIndexR = writeIndex - delaySamplesR
    if (readIndexR < 0) readIndexR += maxDelaySamples

    val delayedL = bufferL[readIndexL]
    val delayedR = bufferR[readIndexR]

    // Low-pass filter damping on feedback loop
    dampFilterL += (delayedL - dampFilterL) * 0.45f
    dampFilterR += (delayedR - dampFilterR) * 0.45f

    val fb = params.feedback.coerceIn(0f, 0.85f)
    // Soft clip feedback
    bufferL[writeIndex] = (inputL + dampFilterL * fb).coerceIn(-1.2f, 1.2f)
    bufferR[writeIndex] = (inputR + dampFilterR * fb).coerceIn(-1.2f, 1.2f)

    writeIndex = (writeIndex + 1) % maxDelaySamples

    val dry = 1f - params.dryWet
    val wet = params.dryWet

    val outL = inputL * dry + delayedL * wet
    val outR = inputR * dry + delayedR * wet

    return Pair(outL, outR)
  }

  fun reset() {
    bufferL.fill(0f)
    bufferR.fill(0f)
    writeIndex = 0
    dampFilterL = 0f
    dampFilterR = 0f
  }
}
