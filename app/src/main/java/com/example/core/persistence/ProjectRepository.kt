package com.example.core.persistence

import android.content.Context
import com.example.core.model.Clip
import com.example.core.model.DelayParams
import com.example.core.model.DrumPadAssignment
import com.example.core.model.InstrumentType
import com.example.core.model.LaunchQuantization
import com.example.core.model.NoteEvent
import com.example.core.model.Project
import com.example.core.model.RootNote
import com.example.core.model.ScaleType
import com.example.core.model.Scene
import com.example.core.model.SynthParams
import com.example.core.model.Track
import com.example.core.synthesis.PresetDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ProjectSerializer {

  fun toJson(project: Project): String {
    val json = JSONObject()
    json.put("id", project.id)
    json.put("schemaVersion", project.schemaVersion)
    json.put("name", project.name)
    json.put("createdAt", project.createdAt)
    json.put("modifiedAt", project.modifiedAt)
    json.put("tempoBpm", project.tempoBpm)
    json.put("timeSignatureNumerator", project.timeSignatureNumerator)
    json.put("timeSignatureDenominator", project.timeSignatureDenominator)
    json.put("rootNote", project.rootNote.name)
    json.put("scaleType", project.scaleType.name)
    json.put("grooveAmount", project.grooveAmount.toDouble())
    json.put("launchQuantization", project.launchQuantization.name)
    json.put("masterVolume", project.masterVolume.toDouble())

    // Tracks
    val tracksArray = JSONArray()
    for (track in project.tracks) {
      val tObj = JSONObject()
      tObj.put("id", track.id)
      tObj.put("index", track.index)
      tObj.put("name", track.name)
      tObj.put("instrumentType", track.instrumentType.name)
      tObj.put("presetId", track.presetId)
      tObj.put("presetName", track.presetName)
      tObj.put("color", track.color)
      tObj.put("volumeDb", track.volumeDb.toDouble())
      tObj.put("pan", track.pan.toDouble())
      tObj.put("mute", track.mute)
      tObj.put("solo", track.solo)

      // SynthParams
      val sp = track.synthParams
      val spObj = JSONObject()
      spObj.put("waveform", sp.waveform)
      spObj.put("attackMs", sp.attackMs.toDouble())
      spObj.put("decayMs", sp.decayMs.toDouble())
      spObj.put("sustainLevel", sp.sustainLevel.toDouble())
      spObj.put("releaseMs", sp.releaseMs.toDouble())
      spObj.put("cutoffHz", sp.cutoffHz.toDouble())
      spObj.put("resonance", sp.resonance.toDouble())
      spObj.put("detuneCents", sp.detuneCents.toDouble())
      spObj.put("subOscLevel", sp.subOscLevel.toDouble())
      tObj.put("synthParams", spObj)

      // DelayParams
      val dp = track.delayParams
      val dpObj = JSONObject()
      dpObj.put("enabled", dp.enabled)
      dpObj.put("dryWet", dp.dryWet.toDouble())
      dpObj.put("delayTimeMs", dp.delayTimeMs.toDouble())
      dpObj.put("feedback", dp.feedback.toDouble())
      dpObj.put("tempoSync", dp.tempoSync)
      tObj.put("delayParams", dpObj)

      // DrumPads
      val padsArray = JSONArray()
      for (pad in track.drumPads) {
        val pObj = JSONObject()
        pObj.put("padIndex", pad.padIndex)
        pObj.put("name", pad.name)
        pObj.put("midiNote", pad.midiNote)
        pObj.put("sampleId", pad.sampleId)
        pObj.put("gain", pad.gain.toDouble())
        pObj.put("pitchShiftSemitones", pad.pitchShiftSemitones.toDouble())
        pObj.put("chokeGroup", pad.chokeGroup)
        padsArray.put(pObj)
      }
      tObj.put("drumPads", padsArray)

      // Clips
      val clipsArray = JSONArray()
      for (clip in track.clips) {
        if (clip == null) {
          clipsArray.put(JSONObject.NULL)
        } else {
          val cObj = JSONObject()
          cObj.put("id", clip.id)
          cObj.put("trackId", clip.trackId)
          cObj.put("sceneIndex", clip.sceneIndex)
          cObj.put("name", clip.name)
          cObj.put("lengthBars", clip.lengthBars)
          cObj.put("lengthTicks", clip.lengthTicks)
          cObj.put("loopStartTicks", clip.loopStartTicks)
          cObj.put("loopEndTicks", clip.loopEndTicks)
          cObj.put("color", clip.color)

          // Notes
          val notesArray = JSONArray()
          for (n in clip.notes) {
            val nObj = JSONObject()
            nObj.put("id", n.id)
            nObj.put("pitch", n.pitch)
            nObj.put("velocity", n.velocity.toDouble())
            nObj.put("startTick", n.startTick)
            nObj.put("durationTicks", n.durationTicks)
            nObj.put("channel", n.channel)
            nObj.put("probability", n.probability.toDouble())
            notesArray.put(nObj)
          }
          cObj.put("notes", notesArray)
          clipsArray.put(cObj)
        }
      }
      tObj.put("clips", clipsArray)

      tracksArray.put(tObj)
    }
    json.put("tracks", tracksArray)

    // Scenes
    val scenesArray = JSONArray()
    for (s in project.scenes) {
      val sObj = JSONObject()
      sObj.put("index", s.index)
      sObj.put("name", s.name)
      sObj.put("color", s.color)
      scenesArray.put(sObj)
    }
    json.put("scenes", scenesArray)

    return json.toString(2)
  }

  fun fromJson(jsonStr: String): Project {
    val json = JSONObject(jsonStr)
    val id = json.optString("id", java.util.UUID.randomUUID().toString())
    val schemaVersion = json.optInt("schemaVersion", 1)
    val name = json.optString("name", "Untitled Session")
    val createdAt = json.optLong("createdAt", System.currentTimeMillis())
    val modifiedAt = json.optLong("modifiedAt", System.currentTimeMillis())
    val tempoBpm = json.optDouble("tempoBpm", 120.0)
    val num = json.optInt("timeSignatureNumerator", 4)
    val den = json.optInt("timeSignatureDenominator", 4)
    val root = try { RootNote.valueOf(json.optString("rootNote", "C")) } catch (e: Exception) { RootNote.C }
    val scale = try { ScaleType.valueOf(json.optString("scaleType", "NATURAL_MINOR")) } catch (e: Exception) { ScaleType.NATURAL_MINOR }
    val groove = json.optDouble("grooveAmount", 0.0).toFloat()
    val launchQ = try { LaunchQuantization.valueOf(json.optString("launchQuantization", "BAR")) } catch (e: Exception) { LaunchQuantization.BAR }
    val masterVol = json.optDouble("masterVolume", 1.0).toFloat()

    val tracksList = mutableListOf<Track>()
    val tracksArray = json.optJSONArray("tracks")
    if (tracksArray != null) {
      for (i in 0 until tracksArray.length()) {
        val tObj = tracksArray.getJSONObject(i)
        val tId = tObj.getString("id")
        val tIndex = tObj.optInt("index", i)
        val tName = tObj.optString("name", "Track ${i + 1}")
        val instType = try { InstrumentType.valueOf(tObj.optString("instrumentType", "SYNTH_PAD")) } catch (e: Exception) { InstrumentType.SYNTH_PAD }
        val presetId = tObj.optString("presetId", "warm_pad")
        val presetName = tObj.optString("presetName", "Warm Pad")
        val color = tObj.optLong("color", 0xFF00D2B4)
        val vol = tObj.optDouble("volumeDb", 0.0).toFloat()
        val pan = tObj.optDouble("pan", 0.0).toFloat()
        val mute = tObj.optBoolean("mute", false)
        val solo = tObj.optBoolean("solo", false)

        val spObj = tObj.optJSONObject("synthParams")
        val synthParams = if (spObj != null) {
          SynthParams(
            waveform = spObj.optInt("waveform", 0),
            attackMs = spObj.optDouble("attackMs", 10.0).toFloat(),
            decayMs = spObj.optDouble("decayMs", 120.0).toFloat(),
            sustainLevel = spObj.optDouble("sustainLevel", 0.7).toFloat(),
            releaseMs = spObj.optDouble("releaseMs", 250.0).toFloat(),
            cutoffHz = spObj.optDouble("cutoffHz", 3500.0).toFloat(),
            resonance = spObj.optDouble("resonance", 1.2).toFloat(),
            detuneCents = spObj.optDouble("detuneCents", 4.0).toFloat(),
            subOscLevel = spObj.optDouble("subOscLevel", 0.3).toFloat()
          )
        } else SynthParams()

        val dpObj = tObj.optJSONObject("delayParams")
        val delayParams = if (dpObj != null) {
          DelayParams(
            enabled = dpObj.optBoolean("enabled", true),
            dryWet = dpObj.optDouble("dryWet", 0.25).toFloat(),
            delayTimeMs = dpObj.optDouble("delayTimeMs", 300.0).toFloat(),
            feedback = dpObj.optDouble("feedback", 0.35).toFloat(),
            tempoSync = dpObj.optBoolean("tempoSync", true)
          )
        } else DelayParams()

        val padsList = mutableListOf<DrumPadAssignment>()
        val padsArray = tObj.optJSONArray("drumPads")
        if (padsArray != null) {
          for (p in 0 until padsArray.length()) {
            val pObj = padsArray.getJSONObject(p)
            padsList.add(
              DrumPadAssignment(
                padIndex = pObj.optInt("padIndex", p),
                name = pObj.optString("name", "Pad ${p + 1}"),
                midiNote = pObj.optInt("midiNote", 36 + p),
                sampleId = pObj.optString("sampleId", "kick"),
                gain = pObj.optDouble("gain", 1.0).toFloat(),
                pitchShiftSemitones = pObj.optDouble("pitchShiftSemitones", 0.0).toFloat(),
                chokeGroup = pObj.optInt("chokeGroup", 0)
              )
            )
          }
        }

        val clipsList = mutableListOf<Clip?>()
        val clipsArray = tObj.optJSONArray("clips")
        if (clipsArray != null) {
          for (c in 0 until clipsArray.length()) {
            if (clipsArray.isNull(c)) {
              clipsList.add(null)
            } else {
              val cObj = clipsArray.getJSONObject(c)
              val cId = cObj.getString("id")
              val cTrackId = cObj.optString("trackId", tId)
              val cScene = cObj.optInt("sceneIndex", c)
              val cName = cObj.optString("name", "Clip ${c + 1}")
              val cBars = cObj.optInt("lengthBars", 4)
              val cTicks = cObj.optLong("lengthTicks", cBars * 3840L)
              val cStart = cObj.optLong("loopStartTicks", 0L)
              val cEnd = cObj.optLong("loopEndTicks", cTicks)
              val cColor = cObj.optLong("color", color)

              val notesList = mutableListOf<NoteEvent>()
              val notesArray = cObj.optJSONArray("notes")
              if (notesArray != null) {
                for (n in 0 until notesArray.length()) {
                  val nObj = notesArray.getJSONObject(n)
                  notesList.add(
                    NoteEvent(
                      id = nObj.optString("id", java.util.UUID.randomUUID().toString()),
                      pitch = nObj.getInt("pitch"),
                      velocity = nObj.optDouble("velocity", 0.8).toFloat(),
                      startTick = nObj.getLong("startTick"),
                      durationTicks = nObj.getLong("durationTicks"),
                      channel = nObj.optInt("channel", 0),
                      probability = nObj.optDouble("probability", 1.0).toFloat()
                    )
                  )
                }
              }

              clipsList.add(
                Clip(
                  id = cId,
                  trackId = cTrackId,
                  sceneIndex = cScene,
                  name = cName,
                  lengthBars = cBars,
                  lengthTicks = cTicks,
                  loopStartTicks = cStart,
                  loopEndTicks = cEnd,
                  notes = notesList,
                  color = cColor
                )
              )
            }
          }
        }

        tracksList.add(
          Track(
            id = tId,
            index = tIndex,
            name = tName,
            instrumentType = instType,
            presetId = presetId,
            presetName = presetName,
            color = color,
            volumeDb = vol,
            pan = pan,
            mute = mute,
            solo = solo,
            synthParams = synthParams,
            drumPads = padsList,
            delayParams = delayParams,
            clips = if (clipsList.size >= 8) clipsList else (clipsList + List(8 - clipsList.size) { null })
          )
        )
      }
    }

    val scenesList = mutableListOf<Scene>()
    val scenesArray = json.optJSONArray("scenes")
    if (scenesArray != null) {
      for (s in 0 until scenesArray.length()) {
        val sObj = scenesArray.getJSONObject(s)
        scenesList.add(
          Scene(
            index = sObj.optInt("index", s),
            name = sObj.optString("name", "Scene ${s + 1}"),
            color = sObj.optLong("color", 0xFF3C3F46)
          )
        )
      }
    } else {
      for (s in 0 until 8) scenesList.add(Scene(s))
    }

    return Project(
      id = id,
      schemaVersion = schemaVersion,
      name = name,
      createdAt = createdAt,
      modifiedAt = modifiedAt,
      tempoBpm = tempoBpm,
      timeSignatureNumerator = num,
      timeSignatureDenominator = den,
      rootNote = root,
      scaleType = scale,
      grooveAmount = groove,
      launchQuantization = launchQ,
      tracks = tracksList,
      scenes = scenesList,
      masterVolume = masterVol
    )
  }
}

class ProjectRepository(private val context: Context) {
  private val projectsDir: File
    get() = File(context.filesDir, "projects").apply { if (!exists()) mkdirs() }

  fun getAllProjects(): List<Project> {
    val files = projectsDir.listFiles { _, name -> name.endsWith(".json") } ?: return emptyList()
    val list = mutableListOf<Project>()
    for (file in files) {
      try {
        val content = file.readText()
        list.add(ProjectSerializer.fromJson(content))
      } catch (e: Exception) {
        android.util.Log.e("ProjectRepository", "Error reading project file ${file.name}", e)
      }
    }
    return list.sortedByDescending { it.modifiedAt }
  }

  fun getProject(id: String): Project? {
    val file = File(projectsDir, "$id.json")
    if (!file.exists()) return null
    return try {
      ProjectSerializer.fromJson(file.readText())
    } catch (e: Exception) {
      null
    }
  }

  @Synchronized
  fun saveProject(project: Project) {
    val updated = project.copy(modifiedAt = System.currentTimeMillis())
    val json = ProjectSerializer.toJson(updated)
    // Atomic safe write
    val tempFile = File(projectsDir, "${project.id}.tmp")
    val targetFile = File(projectsDir, "${project.id}.json")
    tempFile.writeText(json)
    if (tempFile.exists()) {
      tempFile.renameTo(targetFile)
    }
  }

  fun deleteProject(id: String): Boolean {
    val targetFile = File(projectsDir, "$id.json")
    return if (targetFile.exists()) targetFile.delete() else false
  }

  fun duplicateProject(id: String): Project? {
    val orig = getProject(id) ?: return null
    val newId = java.util.UUID.randomUUID().toString()
    val copy = orig.copy(
      id = newId,
      name = "${orig.name} (Copy)",
      createdAt = System.currentTimeMillis(),
      modifiedAt = System.currentTimeMillis()
    )
    saveProject(copy)
    return copy
  }

  /**
   * Generates a fully populated demonstration project containing playable 8 tracks,
   * drum beats, basslines, chord progressions, and lead melodies across multiple scenes.
   */
  fun createDefaultDemoProject(): Project {
    val pId = java.util.UUID.randomUUID().toString()

    // Track 1: Drum Kit
    val drumPads = listOf(
      DrumPadAssignment(0, "Kick", 36, "kick"),
      DrumPadAssignment(1, "Snare", 38, "snare"),
      DrumPadAssignment(2, "Clap", 39, "clap"),
      DrumPadAssignment(3, "Rim", 37, "rim"),
      DrumPadAssignment(4, "Closed Hat", 42, "hihat_closed", chokeGroup = 1),
      DrumPadAssignment(5, "Open Hat", 46, "hihat_open", chokeGroup = 1),
      DrumPadAssignment(6, "Low Tom", 41, "tom_low"),
      DrumPadAssignment(7, "Mid Tom", 45, "tom_mid"),
      DrumPadAssignment(8, "Hi Tom", 48, "tom_hi"),
      DrumPadAssignment(9, "Perc", 50, "perc"),
      DrumPadAssignment(10, "Kick Punch", 35, "kick"),
      DrumPadAssignment(11, "Snare Bright", 40, "snare"),
      DrumPadAssignment(12, "Tambourine", 54, "hihat_closed"),
      DrumPadAssignment(13, "Cabasa", 69, "hihat_closed"),
      DrumPadAssignment(14, "Cowbell", 56, "perc"),
      DrumPadAssignment(15, "Shaker", 70, "hihat_closed")
    )

    // Clip 1 for Drums: 2-bar energetic groove
    val drumNotes1 = mutableListOf<NoteEvent>()
    // 4-on-the-floor kick + snare on 2 and 4 + 8th note hi-hats
    for (bar in 0..1) {
      val barOffset = bar * 3840L
      // Kicks on beats 1, 2, 3, 4
      for (beat in 0..3) {
        drumNotes1.add(NoteEvent(pitch = 36, velocity = 0.95f, startTick = barOffset + beat * 960L, durationTicks = 240L))
      }
      // Snares on beat 2 and 4
      drumNotes1.add(NoteEvent(pitch = 38, velocity = 0.9f, startTick = barOffset + 960L, durationTicks = 240L))
      drumNotes1.add(NoteEvent(pitch = 38, velocity = 0.9f, startTick = barOffset + 2880L, durationTicks = 240L))
      // Hi-Hats every 8th note
      for (step in 0..7) {
        val tick = barOffset + step * 480L
        val pitch = if (step == 6) 46 else 42 // Open hat on upbeat of 4
        drumNotes1.add(NoteEvent(pitch = pitch, velocity = 0.75f, startTick = tick, durationTicks = 180L))
      }
    }

    val drumClips = MutableList<Clip?>(8) { null }
    drumClips[0] = Clip(
      trackId = "track_drums",
      sceneIndex = 0,
      name = "Main Beat",
      lengthBars = 2,
      notes = drumNotes1,
      color = 0xFFFF5722
    )

    // Track 2: Bass (Sub / Acid)
    val bassNotes1 = mutableListOf<NoteEvent>()
    // Funky syncopated bassline in C Minor (C2, Eb2, F2, G2, Bb2)
    val bassPitches = listOf(36, 36, 39, 41, 43, 36, 46, 43)
    val bassTicks = listOf(0L, 720L, 960L, 1680L, 1920L, 2640L, 2880L, 3360L)
    for (i in bassPitches.indices) {
      bassNotes1.add(
        NoteEvent(
          pitch = bassPitches[i],
          velocity = 0.9f,
          startTick = bassTicks[i],
          durationTicks = 220L
        )
      )
    }
    // Repeat bar 2 with octave bounce
    for (i in bassPitches.indices) {
      bassNotes1.add(
        NoteEvent(
          pitch = if (i == 4) 55 else bassPitches[i],
          velocity = 0.9f,
          startTick = 3840L + bassTicks[i],
          durationTicks = 220L
        )
      )
    }

    val bassClips = MutableList<Clip?>(8) { null }
    bassClips[0] = Clip(
      trackId = "track_bass",
      sceneIndex = 0,
      name = "Sub Groove",
      lengthBars = 2,
      notes = bassNotes1,
      color = 0xFF4CAF50
    )

    // Track 3: Warm Pad (Chords: Cm7 -> Abmaj7)
    val padNotes1 = listOf(
      // Bar 1: Cm7 (C3, Eb3, G3, Bb3)
      NoteEvent(pitch = 48, velocity = 0.8f, startTick = 0L, durationTicks = 3600L),
      NoteEvent(pitch = 51, velocity = 0.8f, startTick = 0L, durationTicks = 3600L),
      NoteEvent(pitch = 55, velocity = 0.8f, startTick = 0L, durationTicks = 3600L),
      NoteEvent(pitch = 58, velocity = 0.8f, startTick = 0L, durationTicks = 3600L),
      // Bar 2: Abmaj7 (Ab2, C3, Eb3, G3)
      NoteEvent(pitch = 44, velocity = 0.8f, startTick = 3840L, durationTicks = 3600L),
      NoteEvent(pitch = 48, velocity = 0.8f, startTick = 3840L, durationTicks = 3600L),
      NoteEvent(pitch = 51, velocity = 0.8f, startTick = 3840L, durationTicks = 3600L),
      NoteEvent(pitch = 55, velocity = 0.8f, startTick = 3840L, durationTicks = 3600L)
    )
    val padClips = MutableList<Clip?>(8) { null }
    padClips[0] = Clip(
      trackId = "track_pad",
      sceneIndex = 0,
      name = "Deep Chords",
      lengthBars = 2,
      notes = padNotes1,
      color = 0xFF2196F3
    )

    // Track 4: Lead Pluck
    val leadNotes1 = listOf(
      NoteEvent(pitch = 72, velocity = 0.85f, startTick = 480L, durationTicks = 200L),
      NoteEvent(pitch = 75, velocity = 0.85f, startTick = 960L, durationTicks = 200L),
      NoteEvent(pitch = 74, velocity = 0.85f, startTick = 1440L, durationTicks = 200L),
      NoteEvent(pitch = 72, velocity = 0.85f, startTick = 2400L, durationTicks = 200L),
      NoteEvent(pitch = 79, velocity = 0.85f, startTick = 4320L, durationTicks = 200L),
      NoteEvent(pitch = 77, velocity = 0.85f, startTick = 5280L, durationTicks = 200L)
    )
    val leadClips = MutableList<Clip?>(8) { null }
    leadClips[0] = Clip(
      trackId = "track_lead",
      sceneIndex = 0,
      name = "Neon Melody",
      lengthBars = 2,
      notes = leadNotes1,
      color = 0xFFFFC107
    )

    val tracks = listOf(
      Track(
        id = "track_drums",
        index = 0,
        name = "909 Core Kit",
        instrumentType = InstrumentType.DRUM_KIT,
        presetId = "drum_core_kit",
        presetName = "909 Core Kit",
        color = 0xFFFF5722,
        drumPads = drumPads,
        clips = drumClips
      ),
      Track(
        id = "track_bass",
        index = 1,
        name = "Deep Sub Bass",
        instrumentType = InstrumentType.SYNTH_BASS,
        presetId = "deep_sub_bass",
        presetName = "Deep Sub Bass",
        color = 0xFF4CAF50,
        synthParams = PresetDatabase.getPreset("deep_sub_bass").params,
        clips = bassClips
      ),
      Track(
        id = "track_pad",
        index = 2,
        name = "Warm Analog Pad",
        instrumentType = InstrumentType.SYNTH_PAD,
        presetId = "warm_pad",
        presetName = "Warm Analog Pad",
        color = 0xFF2196F3,
        synthParams = PresetDatabase.getPreset("warm_pad").params,
        clips = padClips
      ),
      Track(
        id = "track_lead",
        index = 3,
        name = "Organic Pluck",
        instrumentType = InstrumentType.SYNTH_LEAD,
        presetId = "pluck_synth",
        presetName = "Organic Pluck",
        color = 0xFFFFC107,
        synthParams = PresetDatabase.getPreset("pluck_synth").params,
        clips = leadClips
      ),
      Track(
        id = "track_5",
        index = 4,
        name = "Soft Dream Keys",
        instrumentType = InstrumentType.SYNTH_PAD,
        presetId = "soft_keys",
        presetName = "Soft Dream Keys",
        color = 0xFF9C27B0,
        synthParams = PresetDatabase.getPreset("soft_keys").params
      ),
      Track(
        id = "track_6",
        index = 5,
        name = "Acid Reso Bass",
        instrumentType = InstrumentType.SYNTH_BASS,
        presetId = "acid_bass",
        presetName = "Acid Reso Bass",
        color = 0xFFE91E63,
        synthParams = PresetDatabase.getPreset("acid_bass").params
      ),
      Track(
        id = "track_7",
        index = 6,
        name = "80s Punch Lead",
        instrumentType = InstrumentType.SYNTH_LEAD,
        presetId = "analog_lead",
        presetName = "80s Punch Lead",
        color = 0xFF00BCD4,
        synthParams = PresetDatabase.getPreset("analog_lead").params
      ),
      Track(
        id = "track_8",
        index = 7,
        name = "Perc & FX",
        instrumentType = InstrumentType.DRUM_KIT,
        presetId = "drum_core_kit",
        presetName = "Perc & FX",
        color = 0xFF8BC34A,
        drumPads = drumPads
      )
    )

    val demoProject = Project(
      id = pId,
      name = "Neon Nightfall",
      tempoBpm = 125.0,
      rootNote = RootNote.C,
      scaleType = ScaleType.NATURAL_MINOR,
      tracks = tracks
    )
    saveProject(demoProject)
    return demoProject
  }
}
