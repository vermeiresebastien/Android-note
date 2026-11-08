package com.example.core.model

import java.util.UUID

enum class ScaleType(val displayName: String, val intervals: List<Int>) {
  MAJOR("Major", listOf(0, 2, 4, 5, 7, 9, 11)),
  NATURAL_MINOR("Minor", listOf(0, 2, 3, 5, 7, 8, 10)),
  DORIAN("Dorian", listOf(0, 2, 3, 5, 7, 9, 10)),
  PENTATONIC_MAJOR("Penta Major", listOf(0, 2, 4, 7, 9)),
  PENTATONIC_MINOR("Penta Minor", listOf(0, 3, 5, 7, 10)),
  CHROMATIC("Chromatic", (0..11).toList())
}

enum class RootNote(val displayName: String, val semitone: Int) {
  C("C", 0), C_SHARP("C#", 1), D("D", 2), D_SHARP("D#", 3),
  E("E", 4), F("F", 5), F_SHARP("F#", 6), G("G", 7),
  G_SHARP("G#", 8), A("A", 9), A_SHARP("A#", 10), B("B", 11)
}

enum class LaunchQuantization(val displayName: String, val ticks: Long) {
  NONE("None", 0L),
  BEAT("1 Beat", 960L),
  BAR("1 Bar", 3840L)
}

enum class NoteQuantization(val displayName: String, val ticks: Long) {
  OFF("Off", 0L),
  QUARTER("1/4", 960L),
  EIGHTH("1/8", 480L),
  SIXTEENTH("1/16", 240L),
  THIRTY_SECOND("1/32", 120L)
}

enum class InstrumentType {
  DRUM_KIT,
  SYNTH_PAD,
  SYNTH_LEAD,
  SYNTH_BASS,
  SAMPLER
}

data class NoteEvent(
  val id: String = UUID.randomUUID().toString(),
  val pitch: Int,             // MIDI note 0-127 (e.g. 36 = C1 kick, 60 = C4 middle C)
  val velocity: Float = 0.8f, // 0.0 to 1.0
  val startTick: Long,        // Position in ticks within the clip (0 to lengthTicks - 1)
  val durationTicks: Long,    // Duration in ticks (e.g. 240 for 16th note at PPQ 960)
  val channel: Int = 0,
  val probability: Float = 1.0f
)

data class Clip(
  val id: String = UUID.randomUUID().toString(),
  val trackId: String,
  val sceneIndex: Int,
  val name: String = "Clip",
  val lengthBars: Int = 4, // 1, 2, 4, 8, 16 bars
  val lengthTicks: Long = lengthBars * 3840L, // at 4/4 PPQ 960: 4 beats * 960 = 3840 per bar
  val loopStartTicks: Long = 0L,
  val loopEndTicks: Long = lengthBars * 3840L,
  val notes: List<NoteEvent> = emptyList(),
  val color: Long = 0xFF00D2B4
) {
  val hasNotes: Boolean get() = notes.isNotEmpty()
}

data class SynthParams(
  val waveform: Int = 0,      // 0: Sine, 1: Triangle, 2: Saw, 3: Square
  val attackMs: Float = 10f,
  val decayMs: Float = 120f,
  val sustainLevel: Float = 0.7f,
  val releaseMs: Float = 250f,
  val cutoffHz: Float = 3500f,
  val resonance: Float = 1.2f,
  val detuneCents: Float = 4f,
  val subOscLevel: Float = 0.3f
)

data class DrumPadAssignment(
  val padIndex: Int,          // 0 to 15
  val name: String,
  val midiNote: Int,          // e.g. 36 for kick
  val sampleId: String,       // "kick_punch", "snare_crack", etc., or custom sample ID
  val gain: Float = 1.0f,
  val pitchShiftSemitones: Float = 0f,
  val chokeGroup: Int = 0     // 0 = none, e.g. 1 for hi-hat choke
)

data class DelayParams(
  val enabled: Boolean = true,
  val dryWet: Float = 0.25f,
  val delayTimeMs: Float = 300f,
  val feedback: Float = 0.35f,
  val tempoSync: Boolean = true
)

data class Track(
  val id: String = UUID.randomUUID().toString(),
  val index: Int,
  val name: String,
  val instrumentType: InstrumentType,
  val presetId: String,
  val presetName: String,
  val color: Long,
  val volumeDb: Float = 0f,   // 0 dB = 1.0 gain
  val pan: Float = 0f,        // -1.0 (L) to +1.0 (R)
  val mute: Boolean = false,
  val solo: Boolean = false,
  val synthParams: SynthParams = SynthParams(),
  val drumPads: List<DrumPadAssignment> = emptyList(),
  val delayParams: DelayParams = DelayParams(),
  val clips: List<Clip?> = List(8) { null } // 8 scenes slots
)

data class Scene(
  val index: Int,
  val name: String = "Scene ${index + 1}",
  val color: Long = 0xFF3C3F46
)

data class SampleAsset(
  val id: String,
  val name: String,
  val localPath: String? = null, // null if built-in synthesized sample
  val sampleRate: Int = 44100,
  val durationMs: Long = 500L
)

data class Project(
  val id: String = UUID.randomUUID().toString(),
  val schemaVersion: Int = 1,
  val name: String = "New Session",
  val createdAt: Long = System.currentTimeMillis(),
  val modifiedAt: Long = System.currentTimeMillis(),
  val tempoBpm: Double = 120.0,
  val timeSignatureNumerator: Int = 4,
  val timeSignatureDenominator: Int = 4,
  val rootNote: RootNote = RootNote.C,
  val scaleType: ScaleType = ScaleType.NATURAL_MINOR,
  val grooveAmount: Float = 0f, // 0.0 (straight) to 1.0 (heavy swing)
  val launchQuantization: LaunchQuantization = LaunchQuantization.BAR,
  val tracks: List<Track> = emptyList(),
  val scenes: List<Scene> = List(8) { Scene(it) },
  val samples: List<SampleAsset> = emptyList(),
  val masterVolume: Float = 1.0f
)
