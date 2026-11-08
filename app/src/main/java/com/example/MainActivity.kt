package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import com.example.core.audio.RealtimeAudioEngine
import com.example.core.interop.AbletonLiveTransferExporter
import com.example.core.model.Clip
import com.example.core.model.DrumPadAssignment
import com.example.core.model.InstrumentType
import com.example.core.model.LaunchQuantization
import com.example.core.model.NoteEvent
import com.example.core.model.Project
import com.example.core.model.RootNote
import com.example.core.model.ScaleType
import com.example.core.model.SynthParams
import com.example.core.model.Track
import com.example.core.persistence.ProjectRepository
import com.example.core.scheduler.MusicalScheduler
import com.example.core.synthesis.PresetDatabase
import com.example.feature.editor.MidiPianoRollScreen
import com.example.feature.instrument.InstrumentViewScreen
import com.example.feature.library.ProjectLibraryScreen
import com.example.feature.mixer.MixerScreen
import com.example.feature.session.SessionViewScreen
import com.example.feature.settings.ProjectSettingsScreen
import com.example.ui.theme.CharcoalBackground
import com.example.ui.theme.PocketSessionTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class ScreenContext {
  LIBRARY,
  SESSION,
  INSTRUMENT,
  PIANO_ROLL,
  MIXER,
  SETTINGS
}

class MainActivity : ComponentActivity() {

  private val audioEngine by lazy { RealtimeAudioEngine(44100) }
  private val scheduler by lazy { MusicalScheduler(audioEngine) }
  private val projectRepo by lazy { ProjectRepository(this) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Start Realtime Audio streaming
    audioEngine.start()

    setContent {
      PocketSessionTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = CharcoalBackground) {
          PocketSessionApp(
            audioEngine = audioEngine,
            scheduler = scheduler,
            projectRepo = projectRepo,
            onShareFile = { file, mimeType -> shareFile(file, mimeType) }
          )
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    scheduler.stop()
    audioEngine.stop()
  }

  private fun shareFile(file: File, mimeType: String) {
    try {
      val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", file)
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      startActivity(Intent.createChooser(intent, "Share ${file.name}"))
    } catch (e: Exception) {
      // Fallback intent if FileProvider not in manifest
      val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_TEXT, "Exported: ${file.name} (${file.length()} bytes)")
      }
      startActivity(Intent.createChooser(intent, "Share Session"))
    }
  }
}

@Composable
fun PocketSessionApp(
  audioEngine: RealtimeAudioEngine,
  scheduler: MusicalScheduler,
  projectRepo: ProjectRepository,
  onShareFile: (File, String) -> Unit
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val scope = rememberCoroutineScope()

  // Navigation State
  var currentScreen by remember { mutableStateOf(ScreenContext.SESSION) }
  var selectedTrackIndex by remember { mutableIntStateOf(0) }
  var selectedClipSceneIndex by remember { mutableIntStateOf(0) }

  // Projects State
  var projectsList by remember { mutableStateOf(projectRepo.getAllProjects()) }
  var currentProject by remember {
    mutableStateOf(
      projectsList.firstOrNull() ?: projectRepo.createDefaultDemoProject()
    )
  }

  // Active / Pending clips state for UI reflection
  val activeClipsMap = remember { mutableStateMapOf<Int, Clip?>() }
  val pendingClipsMap = remember { mutableStateMapOf<Int, Clip?>() }

  // Transport state
  var isPlaying by remember { mutableStateOf(scheduler.isPlaying) }
  var isRecording by remember { mutableStateOf(scheduler.isRecording) }
  var currentBar by remember { mutableIntStateOf(1) }
  var currentBeat by remember { mutableIntStateOf(1) }
  var currentTick by remember { mutableLongStateOf(0L) }

  // Meter levels
  var peakL by remember { mutableStateOf(0f) }
  var peakR by remember { mutableStateOf(0f) }

  // Connect AudioEngine to Current Project
  LaunchedEffect(currentProject) {
    audioEngine.currentProject = currentProject
    // Populate activeClipsMap initially if any clips are pre-assigned
    for (t in currentProject.tracks) {
      if (activeClipsMap[t.index] == null) {
        val initialClip = t.clips.firstOrNull { it != null && it.hasNotes }
        activeClipsMap[t.index] = initialClip
      }
    }
  }

  // Connect Scheduler Transport Listener
  DisposableEffect(scheduler) {
    val listener = object : MusicalScheduler.TransportListener {
      override fun onPositionChanged(bar: Int, beat: Int, tickInBeat: Long) {
        currentBar = bar
        currentBeat = beat
        currentTick = scheduler.currentTick
      }

      override fun onClipLaunch(trackIndex: Int, clip: Clip?) {
        activeClipsMap[trackIndex] = clip
        pendingClipsMap[trackIndex] = null
      }

      override fun onPlayStateChanged(playing: Boolean) {
        isPlaying = playing
      }
    }
    scheduler.transportListener = listener
    onDispose {
      scheduler.transportListener = null
    }
  }

  // Periodic UI peak meter & autosave updates
  LaunchedEffect(Unit) {
    while (isActive) {
      peakL = audioEngine.masterPeakL
      peakR = audioEngine.masterPeakR
      isPlaying = scheduler.isPlaying
      isRecording = scheduler.isRecording
      delay(50)
    }
  }

  fun updateAndSaveProject(updated: Project) {
    currentProject = updated
    audioEngine.currentProject = updated
    scope.launch(Dispatchers.IO) {
      projectRepo.saveProject(updated)
      val list = projectRepo.getAllProjects()
      withContext(Dispatchers.Main) {
        projectsList = list
      }
    }
  }

  // Back navigation handling
  BackHandler(enabled = currentScreen != ScreenContext.SESSION) {
    when (currentScreen) {
      ScreenContext.LIBRARY -> currentScreen = ScreenContext.SESSION
      ScreenContext.INSTRUMENT -> currentScreen = ScreenContext.SESSION
      ScreenContext.PIANO_ROLL -> currentScreen = ScreenContext.SESSION
      ScreenContext.MIXER -> currentScreen = ScreenContext.SESSION
      ScreenContext.SETTINGS -> currentScreen = ScreenContext.SESSION
      ScreenContext.SESSION -> {}
    }
  }

  when (currentScreen) {
    ScreenContext.LIBRARY -> {
      ProjectLibraryScreen(
        projects = projectsList,
        onOpenProject = { proj ->
          currentProject = proj
          audioEngine.currentProject = proj
          currentScreen = ScreenContext.SESSION
        },
        onCreateProject = { name ->
          val newP = Project(
            id = UUID.randomUUID().toString(),
            name = name,
            tempoBpm = 120.0,
            tracks = List(8) { i ->
              Track(
                id = UUID.randomUUID().toString(),
                index = i,
                name = if (i == 0) "Drums" else if (i == 1) "Bass" else "Track ${i + 1}",
                instrumentType = if (i == 0) InstrumentType.DRUM_KIT else if (i == 1) InstrumentType.SYNTH_BASS else InstrumentType.SYNTH_PAD,
                presetId = if (i == 0) "drum_core_kit" else if (i == 1) "deep_sub_bass" else "warm_pad",
                presetName = if (i == 0) "909 Core Kit" else if (i == 1) "Deep Sub Bass" else "Warm Pad",
                color = when (i) {
                  0 -> 0xFFFF5722
                  1 -> 0xFF4CAF50
                  2 -> 0xFF2196F3
                  3 -> 0xFFFFC107
                  4 -> 0xFF9C27B0
                  5 -> 0xFFE91E63
                  6 -> 0xFF00BCD4
                  else -> 0xFF8BC34A
                },
                drumPads = if (i == 0) {
                  listOf(
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
                } else emptyList()
              )
            }
          )
          updateAndSaveProject(newP)
          currentScreen = ScreenContext.SESSION
        },
        onDuplicateProject = { id ->
          val copy = projectRepo.duplicateProject(id)
          if (copy != null) {
            projectsList = projectRepo.getAllProjects()
          }
        },
        onDeleteProject = { id ->
          projectRepo.deleteProject(id)
          val list = projectRepo.getAllProjects()
          projectsList = list
          if (currentProject.id == id && list.isNotEmpty()) {
            currentProject = list.first()
          }
        },
        onExportProject = { proj ->
          scope.launch(Dispatchers.IO) {
            val bundleFile = AbletonLiveTransferExporter.exportBundle(context, proj)
            withContext(Dispatchers.Main) {
              onShareFile(bundleFile, "application/zip")
            }
          }
        }
      )
    }

    ScreenContext.SESSION -> {
      SessionViewScreen(
        project = currentProject,
        selectedTrackIndex = selectedTrackIndex,
        activeClips = activeClipsMap,
        pendingClips = pendingClipsMap,
        isPlaying = isPlaying,
        isRecording = isRecording,
        currentBar = currentBar,
        currentBeat = currentBeat,
        peakL = peakL,
        peakR = peakR,
        onPlayToggle = { scheduler.togglePlay() },
        onRecordToggle = { scheduler.toggleRecord() },
        onCaptureClick = {
          // Retrospective MIDI Capture into selected track's current clip
          val captured = scheduler.retrospectiveCapture(selectedTrackIndex, 4)
          if (captured.isNotEmpty()) {
            val track = currentProject.tracks[selectedTrackIndex]
            val existingClip = track.clips[selectedClipSceneIndex]
            val newClip = Clip(
              id = existingClip?.id ?: UUID.randomUUID().toString(),
              trackId = track.id,
              sceneIndex = selectedClipSceneIndex,
              name = "Captured ${selectedClipSceneIndex + 1}",
              lengthBars = 4,
              notes = captured,
              color = track.color
            )
            val updatedClips = track.clips.toMutableList()
            updatedClips[selectedClipSceneIndex] = newClip
            val updatedTrack = track.copy(clips = updatedClips)
            val updatedTracks = currentProject.tracks.toMutableList()
            updatedTracks[selectedTrackIndex] = updatedTrack
            updateAndSaveProject(currentProject.copy(tracks = updatedTracks))
            scheduler.queueClipLaunch(selectedTrackIndex, newClip)
          }
        },
        onLaunchClip = { trackIdx, sceneIdx ->
          selectedTrackIndex = trackIdx
          selectedClipSceneIndex = sceneIdx
          val clip = currentProject.tracks[trackIdx].clips.getOrNull(sceneIdx)
          scheduler.queueClipLaunch(trackIdx, clip, currentProject.launchQuantization)
        },
        onStopTrack = { trackIdx ->
          scheduler.stopTrack(trackIdx, currentProject.launchQuantization)
        },
        onLaunchScene = { sceneIdx ->
          selectedClipSceneIndex = sceneIdx
          scheduler.queueSceneLaunch(sceneIdx, currentProject)
        },
        onSelectTrack = { trackIdx ->
          selectedTrackIndex = trackIdx
        },
        onOpenInstrument = { trackIdx ->
          selectedTrackIndex = trackIdx
          currentScreen = ScreenContext.INSTRUMENT
        },
        onOpenClipEditor = { trackIdx, sceneIdx ->
          selectedTrackIndex = trackIdx
          selectedClipSceneIndex = sceneIdx
          currentScreen = ScreenContext.PIANO_ROLL
        },
        onCreateClip = { trackIdx, sceneIdx ->
          selectedTrackIndex = trackIdx
          selectedClipSceneIndex = sceneIdx
          val track = currentProject.tracks[trackIdx]
          val newClip = Clip(
            id = UUID.randomUUID().toString(),
            trackId = track.id,
            sceneIndex = sceneIdx,
            name = "Clip ${sceneIdx + 1}",
            lengthBars = 4,
            notes = emptyList(),
            color = track.color
          )
          val updatedClips = track.clips.toMutableList()
          updatedClips[sceneIdx] = newClip
          val updatedTrack = track.copy(clips = updatedClips)
          val updatedTracks = currentProject.tracks.toMutableList()
          updatedTracks[trackIdx] = updatedTrack
          updateAndSaveProject(currentProject.copy(tracks = updatedTracks))
          currentScreen = ScreenContext.PIANO_ROLL
        },
        onClearClip = { trackIdx, sceneIdx ->
          val track = currentProject.tracks[trackIdx]
          val updatedClips = track.clips.toMutableList()
          updatedClips[sceneIdx] = null
          val updatedTrack = track.copy(clips = updatedClips)
          val updatedTracks = currentProject.tracks.toMutableList()
          updatedTracks[trackIdx] = updatedTrack
          updateAndSaveProject(currentProject.copy(tracks = updatedTracks))
          scheduler.stopTrack(trackIdx, LaunchQuantization.NONE)
        },
        onDuplicateClip = { trackIdx, sceneIdx ->
          val track = currentProject.tracks[trackIdx]
          val clipToDup = track.clips[sceneIdx]
          if (clipToDup != null) {
            val emptySlot = track.clips.indexOfFirst { it == null }
            if (emptySlot != -1) {
              val dup = clipToDup.copy(
                id = UUID.randomUUID().toString(),
                sceneIndex = emptySlot,
                name = "${clipToDup.name} (Copy)"
              )
              val updatedClips = track.clips.toMutableList()
              updatedClips[emptySlot] = dup
              val updatedTrack = track.copy(clips = updatedClips)
              val updatedTracks = currentProject.tracks.toMutableList()
              updatedTracks[trackIdx] = updatedTrack
              updateAndSaveProject(currentProject.copy(tracks = updatedTracks))
            }
          }
        },
        onOpenMixer = { currentScreen = ScreenContext.MIXER },
        onOpenSettings = { currentScreen = ScreenContext.SETTINGS },
        onExport = {
          scope.launch(Dispatchers.IO) {
            val bundle = AbletonLiveTransferExporter.exportBundle(context, currentProject)
            withContext(Dispatchers.Main) {
              onShareFile(bundle, "application/zip")
            }
          }
        },
        onBackToLibrary = { currentScreen = ScreenContext.LIBRARY }
      )
    }

    ScreenContext.INSTRUMENT -> {
      val track = currentProject.tracks.getOrNull(selectedTrackIndex) ?: currentProject.tracks[0]

      InstrumentViewScreen(
        project = currentProject,
        track = track,
        isPlaying = isPlaying,
        isRecording = isRecording,
        currentBar = currentBar,
        currentBeat = currentBeat,
        peakL = peakL,
        peakR = peakR,
        onPlayToggle = { scheduler.togglePlay() },
        onRecordToggle = { scheduler.toggleRecord() },
        onCaptureClick = {
          val captured = scheduler.retrospectiveCapture(selectedTrackIndex, 4)
          if (captured.isNotEmpty()) {
            val existingClip = track.clips[selectedClipSceneIndex]
            val newClip = Clip(
              id = existingClip?.id ?: UUID.randomUUID().toString(),
              trackId = track.id,
              sceneIndex = selectedClipSceneIndex,
              name = "Captured ${selectedClipSceneIndex + 1}",
              lengthBars = 4,
              notes = captured,
              color = track.color
            )
            val updatedClips = track.clips.toMutableList()
            updatedClips[selectedClipSceneIndex] = newClip
            val updatedTrack = track.copy(clips = updatedClips)
            val updatedTracks = currentProject.tracks.toMutableList()
            updatedTracks[selectedTrackIndex] = updatedTrack
            updateAndSaveProject(currentProject.copy(tracks = updatedTracks))
            scheduler.queueClipLaunch(selectedTrackIndex, newClip)
          }
        },
        onNoteOn = { pitch, velocity ->
          audioEngine.noteOn(track.index, pitch, velocity, track.synthParams)
          scheduler.recordLiveNoteOn(track.index, pitch, velocity)
        },
        onNoteOff = { pitch ->
          audioEngine.noteOff(track.index, pitch)
          scheduler.recordLiveNoteOff(track.index, pitch)
        },
        onDrumTrigger = { pad ->
          audioEngine.triggerSample(track.index, pad.sampleId, pad.gain, pad.pitchShiftSemitones, pad.chokeGroup)
          scheduler.recordLiveNoteOn(track.index, pad.midiNote, pad.gain)
          scheduler.recordLiveNoteOff(track.index, pad.midiNote)
        },
        onPresetSelected = { presetId ->
          val preset = PresetDatabase.getPreset(presetId)
          val updatedTrack = track.copy(
            presetId = preset.id,
            presetName = preset.name,
            synthParams = preset.params
          )
          val updatedTracks = currentProject.tracks.toMutableList()
          updatedTracks[selectedTrackIndex] = updatedTrack
          updateAndSaveProject(currentProject.copy(tracks = updatedTracks))
        },
        onSynthParamsChanged = { newParams ->
          val updatedTrack = track.copy(synthParams = newParams)
          val updatedTracks = currentProject.tracks.toMutableList()
          updatedTracks[selectedTrackIndex] = updatedTrack
          updateAndSaveProject(currentProject.copy(tracks = updatedTracks))
        },
        onOpenClipEditor = { currentScreen = ScreenContext.PIANO_ROLL },
        onBack = { currentScreen = ScreenContext.SESSION }
      )
    }

    ScreenContext.PIANO_ROLL -> {
      val track = currentProject.tracks.getOrNull(selectedTrackIndex) ?: currentProject.tracks[0]
      var clip = track.clips.getOrNull(selectedClipSceneIndex)
      if (clip == null) {
        clip = Clip(
          id = UUID.randomUUID().toString(),
          trackId = track.id,
          sceneIndex = selectedClipSceneIndex,
          name = "Clip ${selectedClipSceneIndex + 1}",
          lengthBars = 4,
          notes = emptyList(),
          color = track.color
        )
        val updatedClips = track.clips.toMutableList()
        updatedClips[selectedClipSceneIndex] = clip
        val updatedTrack = track.copy(clips = updatedClips)
        val updatedTracks = currentProject.tracks.toMutableList()
        updatedTracks[selectedTrackIndex] = updatedTrack
        updateAndSaveProject(currentProject.copy(tracks = updatedTracks))
      }

      MidiPianoRollScreen(
        track = track,
        clip = clip,
        isPlaying = isPlaying,
        currentTick = currentTick,
        onPlayToggle = { scheduler.togglePlay() },
        onNotesChanged = { newNotes ->
          val updatedClip = clip.copy(notes = newNotes)
          val updatedClips = track.clips.toMutableList()
          updatedClips[selectedClipSceneIndex] = updatedClip
          val updatedTrack = track.copy(clips = updatedClips)
          val updatedTracks = currentProject.tracks.toMutableList()
          updatedTracks[selectedTrackIndex] = updatedTrack
          updateAndSaveProject(currentProject.copy(tracks = updatedTracks))
          // If currently playing, update scheduler
          if (scheduler.getActiveClip(track.index)?.id == clip.id) {
            scheduler.queueClipLaunch(track.index, updatedClip, LaunchQuantization.NONE)
          }
        },
        onClipLengthChanged = { bars ->
          val updatedClip = clip.copy(lengthBars = bars, lengthTicks = bars * 3840L)
          val updatedClips = track.clips.toMutableList()
          updatedClips[selectedClipSceneIndex] = updatedClip
          val updatedTrack = track.copy(clips = updatedClips)
          val updatedTracks = currentProject.tracks.toMutableList()
          updatedTracks[selectedTrackIndex] = updatedTrack
          updateAndSaveProject(currentProject.copy(tracks = updatedTracks))
        },
        onBack = { currentScreen = ScreenContext.SESSION }
      )
    }

    ScreenContext.MIXER -> {
      MixerScreen(
        project = currentProject,
        trackPeaks = audioEngine.trackPeakLevels,
        masterPeakL = peakL,
        masterPeakR = peakR,
        onTrackVolumeChanged = { tIdx, volDb ->
          val track = currentProject.tracks[tIdx]
          val updatedTrack = track.copy(volumeDb = volDb)
          val updatedTracks = currentProject.tracks.toMutableList()
          updatedTracks[tIdx] = updatedTrack
          updateAndSaveProject(currentProject.copy(tracks = updatedTracks))
        },
        onTrackPanChanged = { tIdx, pan ->
          val track = currentProject.tracks[tIdx]
          val updatedTrack = track.copy(pan = pan)
          val updatedTracks = currentProject.tracks.toMutableList()
          updatedTracks[tIdx] = updatedTrack
          updateAndSaveProject(currentProject.copy(tracks = updatedTracks))
        },
        onTrackMuteToggle = { tIdx ->
          val track = currentProject.tracks[tIdx]
          val updatedTrack = track.copy(mute = !track.mute)
          val updatedTracks = currentProject.tracks.toMutableList()
          updatedTracks[tIdx] = updatedTrack
          updateAndSaveProject(currentProject.copy(tracks = updatedTracks))
        },
        onTrackSoloToggle = { tIdx ->
          val track = currentProject.tracks[tIdx]
          val updatedTrack = track.copy(solo = !track.solo)
          val updatedTracks = currentProject.tracks.toMutableList()
          updatedTracks[tIdx] = updatedTrack
          updateAndSaveProject(currentProject.copy(tracks = updatedTracks))
        },
        onMasterVolumeChanged = { vol ->
          updateAndSaveProject(currentProject.copy(masterVolume = vol))
        },
        onBack = { currentScreen = ScreenContext.SESSION }
      )
    }

    ScreenContext.SETTINGS -> {
      ProjectSettingsScreen(
        project = currentProject,
        onBpmChanged = { bpm ->
          updateAndSaveProject(currentProject.copy(tempoBpm = bpm))
        },
        onRootNoteChanged = { root ->
          updateAndSaveProject(currentProject.copy(rootNote = root))
        },
        onScaleTypeChanged = { scale ->
          updateAndSaveProject(currentProject.copy(scaleType = scale))
        },
        onLaunchQuantizationChanged = { lq ->
          updateAndSaveProject(currentProject.copy(launchQuantization = lq))
        },
        onGrooveChanged = { groove ->
          updateAndSaveProject(currentProject.copy(grooveAmount = groove))
        },
        onExportLiveBundle = {
          scope.launch(Dispatchers.IO) {
            val bundle = AbletonLiveTransferExporter.exportBundle(context, currentProject)
            withContext(Dispatchers.Main) {
              onShareFile(bundle, "application/zip")
            }
          }
        },
        onExportWav = {
          scope.launch(Dispatchers.IO) {
            val wavBytes = audioEngine.renderOffline(currentProject, 8)
            val exportDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "exports").apply { if (!exists()) mkdirs() }
            val wavFile = File(exportDir, "${currentProject.name.replace(" ", "_")}_mixdown.wav")
            FileOutputStream(wavFile).use { it.write(wavBytes) }
            withContext(Dispatchers.Main) {
              onShareFile(wavFile, "audio/wav")
            }
          }
        },
        onBack = { currentScreen = ScreenContext.SESSION }
      )
    }
  }
}
