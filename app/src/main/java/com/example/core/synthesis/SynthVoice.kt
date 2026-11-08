package com.example.core.synthesis

import com.example.core.model.SynthParams
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tan

/**
 * Polyphonic voice implementation with Anti-click ADSR, 4 selectable waveforms,
 * detuned sub-oscillator, and a resonant Moog/Chamberlin low-pass state variable filter.
 */
class SynthVoice(private val sampleRate: Int = 44100) {
  var isActive = false
    private set

  var midiPitch: Int = 60
    private set

  var trackIndex: Int = 0
  private var targetGain: Float = 0.8f
  private var currentGain: Float = 0.0f

  // ADSR states: 0=IDLE, 1=ATTACK, 2=DECAY, 3=SUSTAIN, 4=RELEASE
  private var envState = 0
  private var envLevel = 0.0f

  // Oscillators phase
  private var phaseMain = 0.0
  private var phaseSub = 0.0
  private var phaseIncMain = 0.0
  private var phaseIncSub = 0.0

  // Filter state
  private var filterLow = 0.0f
  private var filterBand = 0.0f

  private var params = SynthParams()

  fun noteOn(pitch: Int, velocity: Float, track: Int, synthParams: SynthParams) {
    this.midiPitch = pitch
    this.targetGain = velocity
    this.trackIndex = track
    this.params = synthParams
    this.isActive = true
    this.envState = 1 // ATTACK

    val baseFreq = 440.0 * Math.pow(2.0, (pitch - 69) / 12.0)
    val detunedFreq = baseFreq * Math.pow(2.0, params.detuneCents / 1200.0)

    phaseIncMain = (2.0 * PI * baseFreq) / sampleRate
    phaseIncSub = (2.0 * PI * detunedFreq) / sampleRate

    // Reset filter states smoothly
    filterLow = 0f
    filterBand = 0f
  }

  fun noteOff() {
    if (isActive && envState != 0) {
      envState = 4 // RELEASE
    }
  }

  fun kill() {
    isActive = false
    envState = 0
    envLevel = 0f
  }

  fun renderNextSample(): Float {
    if (!isActive || envState == 0) return 0f

    // Process ADSR envelope
    when (envState) {
      1 -> { // ATTACK
        val attackSamples = (params.attackMs * sampleRate / 1000f).coerceAtLeast(1f)
        envLevel += 1.0f / attackSamples
        if (envLevel >= 1.0f) {
          envLevel = 1.0f
          envState = 2 // DECAY
        }
      }
      2 -> { // DECAY
        val decaySamples = (params.decayMs * sampleRate / 1000f).coerceAtLeast(1f)
        val step = (1.0f - params.sustainLevel) / decaySamples
        envLevel -= step
        if (envLevel <= params.sustainLevel) {
          envLevel = params.sustainLevel
          envState = 3 // SUSTAIN
        }
      }
      3 -> { // SUSTAIN
        envLevel = params.sustainLevel
      }
      4 -> { // RELEASE
        val releaseSamples = (params.releaseMs * sampleRate / 1000f).coerceAtLeast(1f)
        val step = (params.sustainLevel.coerceAtLeast(0.01f)) / releaseSamples
        envLevel -= step
        if (envLevel <= 0.0001f) {
          envLevel = 0f
          envState = 0
          isActive = false
          return 0f
        }
      }
    }

    // Oscillators: Main + Detuned sub
    val osc1 = generateWave(params.waveform, phaseMain)
    val osc2 = generateWave(params.waveform, phaseSub)
    val mixedOsc = osc1 * (1f - params.subOscLevel * 0.5f) + osc2 * params.subOscLevel

    phaseMain += phaseIncMain
    if (phaseMain >= 2.0 * PI) phaseMain -= 2.0 * PI

    phaseSub += phaseIncSub
    if (phaseSub >= 2.0 * PI) phaseSub -= 2.0 * PI

    // State Variable Filter (SVF Lowpass)
    val cutoffClamped = params.cutoffHz.coerceIn(50f, sampleRate * 0.45f)
    val f = 2.0f * sin(PI * cutoffClamped / sampleRate).toFloat()
    val q = (1.0f / params.resonance.coerceIn(0.5f, 4.0f))

    filterLow += f * filterBand
    val high = mixedOsc - filterLow - q * filterBand
    filterBand += f * high

    val filtered = filterLow

    // Gain smoothing
    currentGain += (targetGain - currentGain) * 0.05f

    return filtered * envLevel * currentGain
  }

  private fun generateWave(waveform: Int, phase: Double): Float {
    return when (waveform) {
      0 -> sin(phase).toFloat() // Sine
      1 -> { // Triangle
        val norm = (phase / (2.0 * PI)).toFloat()
        (Math.abs(norm - 0.5f) - 0.25f) * 4f
      }
      2 -> { // Sawtooth
        val norm = (phase / (2.0 * PI)).toFloat()
        (norm * 2f - 1f)
      }
      3 -> { // Square
        if (phase < PI) 0.85f else -0.85f
      }
      else -> sin(phase).toFloat()
    }
  }
}
