package com.example.core.midi

import com.example.core.model.Clip
import com.example.core.model.Project
import com.example.core.model.Track
import com.example.core.music.MusicalTimeUtils
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Standard MIDI File (SMF Type 1) Multitrack Exporter.
 * Emits perfectly formed, spec-compliant MIDI files readable by Ableton Live 12,
 * Logic, Pro Tools, and all standard DAWs.
 * Uses 960 PPQ timebase, tempo meta-events, track names, and properly paired Note-On/Note-Off events.
 */
object MidiFileExporter {

  fun exportMultitrack(project: Project): ByteArray {
    val out = ByteArrayOutputStream()

    // Filter tracks that contain at least one clip with notes
    val activeTracks = project.tracks.filter { track -> track.clips.any { it != null && it.hasNotes } }
    val trackCount = (activeTracks.size + 1).coerceAtLeast(2) // 1 tempo master track + instrument tracks

    // 1. Header Chunk: "MThd" (4 bytes), length = 6, format = 1, tracks = trackCount, division = 960
    out.write("MThd".toByteArray(Charsets.US_ASCII))
    writeBigEndian32(out, 6)
    writeBigEndian16(out, 1) // Format 1 (multitrack synchronous)
    writeBigEndian16(out, trackCount)
    writeBigEndian16(out, MusicalTimeUtils.PPQ.toInt()) // 960 PPQ

    // 2. Track 0: Master Tempo & Time Signature Track
    val tempoTrack = ByteArrayOutputStream()
    // Time Signature Meta Event: FF 58 04 nn dd cc bb
    // 4/4 => 04 02 18 08 (dd = 2 means 2^2 = 4)
    writeVarLen(tempoTrack, 0) // delta time 0
    tempoTrack.write(0xFF)
    tempoTrack.write(0x58)
    tempoTrack.write(0x04)
    tempoTrack.write(project.timeSignatureNumerator)
    tempoTrack.write(2) // 2^2 = 4
    tempoTrack.write(24) // 24 MIDI clocks per metronome click
    tempoTrack.write(8) // 8 32nd notes in quarter note

    // Tempo Meta Event: FF 51 03 tttttt (microseconds per quarter note)
    val tempoUs = (60_000_000.0 / project.tempoBpm).toInt()
    writeVarLen(tempoTrack, 0)
    tempoTrack.write(0xFF)
    tempoTrack.write(0x51)
    tempoTrack.write(0x03)
    tempoTrack.write((tempoUs shr 16) and 0xFF)
    tempoTrack.write((tempoUs shr 8) and 0xFF)
    tempoTrack.write(tempoUs and 0xFF)

    // Track Name: Project Name
    writeTrackName(tempoTrack, project.name)

    // End of Track: FF 2F 00
    writeVarLen(tempoTrack, 0)
    tempoTrack.write(0xFF)
    tempoTrack.write(0x2F)
    tempoTrack.write(0x00)

    writeTrackChunk(out, tempoTrack.toByteArray())

    // 3. Instrument Tracks
    for ((index, track) in activeTracks.withIndex()) {
      val trackBytes = exportTrackToMidi(track, (index + 1) % 16)
      writeTrackChunk(out, trackBytes)
    }

    // If no active tracks, write an empty track
    if (activeTracks.isEmpty()) {
      val emptyTrack = ByteArrayOutputStream()
      writeTrackName(emptyTrack, "Empty Track")
      writeVarLen(emptyTrack, 0)
      emptyTrack.write(0xFF)
      emptyTrack.write(0x2F)
      emptyTrack.write(0x00)
      writeTrackChunk(out, emptyTrack.toByteArray())
    }

    return out.toByteArray()
  }

  private fun exportTrackToMidi(track: Track, channel: Int): ByteArray {
    val out = ByteArrayOutputStream()

    // Track Name
    writeTrackName(out, track.name)

    // Collect all note-on and note-off events sorted by absolute tick
    data class MidiEvent(val tick: Long, val type: Int, val pitch: Int, val velocity: Int)

    val events = mutableListOf<MidiEvent>()

    // Flatten clips along the timeline in scene order
    var currentSceneOffset = 0L
    for (clip in track.clips) {
      if (clip != null && clip.hasNotes) {
        val clipLength = clip.lengthTicks
        for (note in clip.notes) {
          val onTick = currentSceneOffset + note.startTick
          val offTick = onTick + note.durationTicks
          val velInt = (note.velocity * 127).toInt().coerceIn(1, 127)
          events.add(MidiEvent(onTick, 0x90, note.pitch, velInt))
          events.add(MidiEvent(offTick, 0x80, note.pitch, 0))
        }
        currentSceneOffset += clipLength
      } else {
        // Empty scene adds 4 bars of silence
        currentSceneOffset += MusicalTimeUtils.TICKS_PER_BAR * 4
      }
    }

    // Sort events by tick (Note-Off before Note-On if at same tick)
    events.sortBy { it.tick * 2 + if (it.type == 0x80) 0 else 1 }

    var lastTick = 0L
    for (event in events) {
      val delta = (event.tick - lastTick).coerceAtLeast(0L)
      writeVarLen(out, delta)

      val status = event.type or (channel and 0x0F)
      out.write(status)
      out.write(event.pitch.coerceIn(0, 127))
      out.write(event.velocity.coerceIn(0, 127))

      lastTick = event.tick
    }

    // End of Track
    writeVarLen(out, 0)
    out.write(0xFF)
    out.write(0x2F)
    out.write(0x00)

    return out.toByteArray()
  }

  private fun writeTrackName(out: OutputStream, name: String) {
    val nameBytes = name.toByteArray(Charsets.UTF_8)
    writeVarLen(out, 0)
    out.write(0xFF)
    out.write(0x03) // Track Name meta event
    writeVarLen(out, nameBytes.size.toLong())
    out.write(nameBytes)
  }

  private fun writeTrackChunk(out: OutputStream, trackData: ByteArray) {
    out.write("MTrk".toByteArray(Charsets.US_ASCII))
    writeBigEndian32(out, trackData.size)
    out.write(trackData)
  }

  private fun writeVarLen(out: OutputStream, value: Long) {
    var v = value
    val buffer = ByteArray(4)
    var count = 0
    buffer[count++] = (v and 0x7F).toByte()
    v = v shr 7
    while (v > 0) {
      buffer[count++] = ((v and 0x7F) or 0x80).toByte()
      v = v shr 7
    }
    for (i in count - 1 downTo 0) {
      out.write(buffer[i].toInt())
    }
  }

  private fun writeBigEndian32(out: OutputStream, v: Int) {
    out.write((v shr 24) and 0xFF)
    out.write((v shr 16) and 0xFF)
    out.write((v shr 8) and 0xFF)
    out.write(v and 0xFF)
  }

  private fun writeBigEndian16(out: OutputStream, v: Int) {
    out.write((v shr 8) and 0xFF)
    out.write(v and 0xFF)
  }
}
