package com.example.core.synthesis

import com.example.core.model.SynthParams

object PresetDatabase {
  data class PresetDef(
    val id: String,
    val name: String,
    val category: String, // "Bass", "Lead", "Pad", "Keys", "Pluck"
    val liveEquivalent: String, // Ableton Live 12 equivalent (Drift / Analog / Operator / Simpler)
    val params: SynthParams
  )

  val presets = listOf(
    PresetDef(
      id = "warm_pad",
      name = "Warm Analog Pad",
      category = "Pad",
      liveEquivalent = "Drift / Warm Analog Pad",
      params = SynthParams(
        waveform = 2, // Saw
        attackMs = 180f,
        decayMs = 400f,
        sustainLevel = 0.85f,
        releaseMs = 500f,
        cutoffHz = 1600f,
        resonance = 1.1f,
        detuneCents = 8f,
        subOscLevel = 0.4f
      )
    ),
    PresetDef(
      id = "deep_sub_bass",
      name = "Deep Sub Bass",
      category = "Bass",
      liveEquivalent = "Drift / Sub Heavy Bass",
      params = SynthParams(
        waveform = 0, // Sine
        attackMs = 5f,
        decayMs = 150f,
        sustainLevel = 0.9f,
        releaseMs = 120f,
        cutoffHz = 450f,
        resonance = 1.0f,
        detuneCents = 2f,
        subOscLevel = 0.7f
      )
    ),
    PresetDef(
      id = "acid_bass",
      name = "Acid Reso Bass",
      category = "Bass",
      liveEquivalent = "Analog / Acid Sawtooth",
      params = SynthParams(
        waveform = 2, // Saw
        attackMs = 4f,
        decayMs = 180f,
        sustainLevel = 0.3f,
        releaseMs = 90f,
        cutoffHz = 1800f,
        resonance = 3.2f,
        detuneCents = 4f,
        subOscLevel = 0.5f
      )
    ),
    PresetDef(
      id = "soft_keys",
      name = "Soft Dream Keys",
      category = "Keys",
      liveEquivalent = "Electric / Soft Tine EP",
      params = SynthParams(
        waveform = 1, // Triangle
        attackMs = 12f,
        decayMs = 350f,
        sustainLevel = 0.5f,
        releaseMs = 300f,
        cutoffHz = 2400f,
        resonance = 1.2f,
        detuneCents = 5f,
        subOscLevel = 0.25f
      )
    ),
    PresetDef(
      id = "analog_lead",
      name = "80s Punch Lead",
      category = "Lead",
      liveEquivalent = "Drift / Poly 80s Brass Lead",
      params = SynthParams(
        waveform = 2, // Saw
        attackMs = 8f,
        decayMs = 250f,
        sustainLevel = 0.8f,
        releaseMs = 200f,
        cutoffHz = 3800f,
        resonance = 1.5f,
        detuneCents = 10f,
        subOscLevel = 0.35f
      )
    ),
    PresetDef(
      id = "pluck_synth",
      name = "Organic Pluck",
      category = "Pluck",
      liveEquivalent = "Tension / Ambient Pluck",
      params = SynthParams(
        waveform = 3, // Square
        attackMs = 3f,
        decayMs = 140f,
        sustainLevel = 0.05f,
        releaseMs = 100f,
        cutoffHz = 2900f,
        resonance = 1.8f,
        detuneCents = 6f,
        subOscLevel = 0.1f
      )
    )
  )

  fun getPreset(id: String): PresetDef {
    return presets.firstOrNull { it.id == id } ?: presets[0]
  }
}
