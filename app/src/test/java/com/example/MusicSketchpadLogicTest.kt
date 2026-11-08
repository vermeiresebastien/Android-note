package com.example

import com.example.core.audio.RealtimeAudioEngine
import com.example.core.interop.AbletonLiveTransferExporter
import com.example.core.midi.MidiFileExporter
import com.example.core.model.Clip
import com.example.core.model.DrumPadAssignment
import com.example.core.model.InstrumentType
import com.example.core.model.NoteEvent
import com.example.core.model.NoteQuantization
import com.example.core.model.Project
import com.example.core.model.RootNote
import com.example.core.model.ScaleType
import com.example.core.model.Track
import com.example.core.music.MusicalTimeUtils
import com.example.core.persistence.ProjectSerializer
import com.example.core.synthesis.DrumSynthGenerator
import com.example.core.synthesis.PresetDatabase
import com.example.core.synthesis.SynthVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicSketchpadLogicTest {

  @Test
  fun testMusicalTimeConversions() {
    val bpm = 120.0
    // At 120 BPM, 1 beat (960 ticks) = 0.5 seconds
    val seconds1Beat = MusicalTimeUtils.ticksToSeconds(960L, bpm)
    assertEquals(0.5, seconds1Beat, 0.001)

    val ticksFromSeconds = MusicalTimeUtils.secondsToTicks(0.5, bpm)
    assertEquals(960L, ticksFromSeconds)

    // 1 bar = 4 beats = 3840 ticks = 2.0 seconds
    val seconds1Bar = MusicalTimeUtils.ticksToSeconds(3840L, bpm)
    assertEquals(2.0, seconds1Bar, 0.001)

    // Sample frame conversions at 44.1kHz
    val frames1Bar = MusicalTimeUtils.ticksToFrames(3840L, bpm, 44100)
    assertEquals(88200L, frames1Bar)

    val ticksFromFrames = MusicalTimeUtils.framesToTicks(88200L, bpm, 44100)
    assertEquals(3840L, ticksFromFrames)
  }

  @Test
  fun testMidiPitchToFrequency() {
    // A4 (MIDI 69) should be exactly 440 Hz
    val a4 = MusicalTimeUtils.midiPitchToFrequency(69)
    assertEquals(440.0f, a4, 0.1f)

    // C4 (MIDI 60) should be ~261.63 Hz
    val c4 = MusicalTimeUtils.midiPitchToFrequency(60)
    assertEquals(261.63f, c4, 0.5f)

    // Note name conversion
    assertEquals("C4", MusicalTimeUtils.midiPitchToString(60))
    assertEquals("A4", MusicalTimeUtils.midiPitchToString(69))
    assertEquals("F#2", MusicalTimeUtils.midiPitchToString(42))
  }

  @Test
  fun testNoteQuantization() {
    val grid16th = 240L
    // Note played slightly ahead of 16th (tick 230) snaps to 240
    val snappedAhead = MusicalTimeUtils.quantizeTick(230L, grid16th)
    assertEquals(240L, snappedAhead)

    // Note played slightly late (tick 250) snaps to 240
    val snappedLate = MusicalTimeUtils.quantizeTick(250L, grid16th)
    assertEquals(240L, snappedLate)
  }

  @Test
  fun testDrumSynthesisGeneration() {
    val kick = DrumSynthGenerator.generateKick(300)
    assertTrue("Kick buffer must not be empty", kick.isNotEmpty())

    // Ensure audio signal contains non-silent PCM values and no NaN/Infinity
    var maxAmp = 0f
    for (s in kick) {
      assertTrue("Sample must be finite", !s.isNaN() && !s.isInfinite())
      val abs = Math.abs(s)
      if (abs > maxAmp) maxAmp = abs
    }
    assertTrue("Kick sample must have audible peak amplitude", maxAmp > 0.4f)
    assertTrue("Kick sample must not hard clip beyond 1.0", maxAmp <= 1.0f)

    val snare = DrumSynthGenerator.generateSnare(200)
    assertTrue("Snare buffer must not be empty", snare.isNotEmpty())

    val hihat = DrumSynthGenerator.generateHiHatClosed(50)
    assertTrue("Hi-hat buffer must not be empty", hihat.isNotEmpty())
  }

  @Test
  fun testSynthVoiceAudioRendering() {
    val voice = SynthVoice(44100)
    val preset = PresetDatabase.getPreset("deep_sub_bass")

    // Trigger note C2 (pitch 36)
    voice.noteOn(36, 0.9f, 0, preset.params)
    assertTrue(voice.isActive)

    // Render 1000 samples and verify non-silence
    var hasAudio = false
    for (i in 0 until 1000) {
      val sample = voice.renderNextSample()
      assertTrue("Voice sample must be valid finite float", !sample.isNaN() && !sample.isInfinite())
      if (Math.abs(sample) > 0.05f) {
        hasAudio = true
      }
    }
    assertTrue("Synth voice must generate audible signal", hasAudio)

    // Note Off initiates release
    voice.noteOff()
    // Render until release completes
    for (i in 0 until 44100) {
      voice.renderNextSample()
    }
    assertTrue("Voice should finish release and become inactive", !voice.isActive)
  }

  @Test
  fun testProjectSerializationAndRoundtrip() {
    val note1 = NoteEvent(pitch = 36, velocity = 0.9f, startTick = 0L, durationTicks = 240L)
    val note2 = NoteEvent(pitch = 38, velocity = 0.85f, startTick = 960L, durationTicks = 240L)
    val clip = Clip(trackId = "t1", sceneIndex = 0, name = "Groove", notes = listOf(note1, note2))

    val track = Track(
      id = "t1",
      index = 0,
      name = "Drums",
      instrumentType = InstrumentType.DRUM_KIT,
      presetId = "drum_core_kit",
      presetName = "909 Core Kit",
      color = 0xFFFF5722,
      drumPads = listOf(DrumPadAssignment(0, "Kick", 36, "kick")),
      clips = listOf(clip, null, null, null, null, null, null, null)
    )

    val project = Project(
      name = "Test Sketch",
      tempoBpm = 128.0,
      rootNote = RootNote.D,
      scaleType = ScaleType.DORIAN,
      tracks = listOf(track)
    )

    val jsonStr = ProjectSerializer.toJson(project)
    assertTrue("Serialized JSON must contain project name", jsonStr.contains("Test Sketch"))
    assertTrue("Serialized JSON must contain tempo", jsonStr.contains("128"))

    val deserialized = ProjectSerializer.fromJson(jsonStr)
    assertEquals(project.name, deserialized.name)
    assertEquals(128.0, deserialized.tempoBpm, 0.001)
    assertEquals(RootNote.D, deserialized.rootNote)
    assertEquals(ScaleType.DORIAN, deserialized.scaleType)
    assertEquals(1, deserialized.tracks.size)

    val loadedTrack = deserialized.tracks[0]
    assertEquals("Drums", loadedTrack.name)
    assertEquals(1, loadedTrack.drumPads.size)

    val loadedClip = loadedTrack.clips[0]
    assertNotNull(loadedClip)
    assertEquals(2, loadedClip!!.notes.size)
    assertEquals(36, loadedClip.notes[0].pitch)
    assertEquals(38, loadedClip.notes[1].pitch)
  }

  @Test
  fun testMidiExportSpecCompliance() {
    val note = NoteEvent(pitch = 60, velocity = 0.8f, startTick = 0L, durationTicks = 480L)
    val clip = Clip(trackId = "t1", sceneIndex = 0, name = "Melody", notes = listOf(note))
    val track = Track(
      id = "t1",
      index = 0,
      name = "Keys",
      instrumentType = InstrumentType.SYNTH_PAD,
      presetId = "warm_pad",
      presetName = "Warm Pad",
      color = 0xFF2196F3,
      clips = listOf(clip, null, null, null, null, null, null, null)
    )
    val project = Project(name = "MidiTest", tempoBpm = 120.0, tracks = listOf(track))

    val midiBytes = MidiFileExporter.exportMultitrack(project)
    assertTrue("MIDI file must not be empty", midiBytes.isNotEmpty())

    // Verify SMF Header "MThd"
    assertEquals('M'.code.toByte(), midiBytes[0])
    assertEquals('T'.code.toByte(), midiBytes[1])
    assertEquals('h'.code.toByte(), midiBytes[2])
    assertEquals('d'.code.toByte(), midiBytes[3])

    // Verify SMF format 1 (bytes 8 and 9)
    assertEquals(0.toByte(), midiBytes[8])
    assertEquals(1.toByte(), midiBytes[9])

    // Verify PPQ division 960 (0x03C0) (bytes 12 and 13)
    assertEquals(0x03.toByte(), midiBytes[12])
    assertEquals(0xC0.toByte(), midiBytes[13])
  }

  @Test
  fun testAbletonLiveTransferJsonStructure() {
    val track = Track(
      id = "t1",
      index = 0,
      name = "Bass",
      instrumentType = InstrumentType.SYNTH_BASS,
      presetId = "deep_sub_bass",
      presetName = "Deep Sub Bass",
      color = 0xFF4CAF50
    )
    val project = Project(name = "LiveSync", tempoBpm = 124.0, tracks = listOf(track))
    val liveTransferJson = AbletonLiveTransferExporter.generateLiveTransferJson(project)

    assertTrue("Must mention Ableton Live 12", liveTransferJson.contains("Ableton Live 12"))
    assertTrue("Must contain Drift instrument mapping", liveTransferJson.contains("Drift"))
    assertTrue("Must contain Sub Heavy Bass preset reference", liveTransferJson.contains("Sub Heavy Bass"))
    assertTrue("Must contain tempo 124", liveTransferJson.contains("124"))
  }
}
