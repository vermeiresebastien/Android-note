package com.example.feature.instrument

import android.view.MotionEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.DrumPadAssignment
import com.example.core.model.InstrumentType
import com.example.core.model.Project
import com.example.core.model.RootNote
import com.example.core.model.ScaleType
import com.example.core.model.SynthParams
import com.example.core.model.Track
import com.example.core.music.MusicalTimeUtils
import com.example.core.synthesis.PresetDatabase
import com.example.ui.components.TopTransportBar
import com.example.ui.theme.CharcoalBackground
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CharcoalSurfaceVariant
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InstrumentViewScreen(
  project: Project,
  track: Track,
  isPlaying: Boolean,
  isRecording: Boolean,
  currentBar: Int,
  currentBeat: Int,
  peakL: Float,
  peakR: Float,
  onPlayToggle: () -> Unit,
  onRecordToggle: () -> Unit,
  onCaptureClick: () -> Unit,
  onNoteOn: (pitch: Int, velocity: Float) -> Unit,
  onNoteOff: (pitch: Int) -> Unit,
  onDrumTrigger: (pad: DrumPadAssignment) -> Unit,
  onPresetSelected: (presetId: String) -> Unit,
  onSynthParamsChanged: (SynthParams) -> Unit,
  onOpenClipEditor: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var octaveOffset by remember { mutableIntStateOf(0) }
  var showPresetMenu by remember { mutableStateOf(false) }
  var showParamsSheet by remember { mutableStateOf(false) }

  val trackColor = Color(track.color)

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(CharcoalBackground)
  ) {
    // Top Transport Bar
    TopTransportBar(
      projectName = project.name,
      tempoBpm = project.tempoBpm,
      bar = currentBar,
      beat = currentBeat,
      isPlaying = isPlaying,
      isRecording = isRecording,
      peakL = peakL,
      peakR = peakR,
      onPlayToggle = onPlayToggle,
      onRecordToggle = onRecordToggle,
      onTempoClick = {},
      onCaptureClick = onCaptureClick
    )

    // Instrument Subheader: Track Name, Preset Selector, Parameters, Back
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF181B20))
        .padding(horizontal = 12.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp).testTag("instrument_back_button")) {
          Icon(Icons.Default.ArrowBack, contentDescription = "Back to Session", tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
        Column {
          Text(
            text = track.name,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
          )
          // Preset selector chip
          Box(
            modifier = Modifier
              .clickable { showPresetMenu = true }
              .testTag("preset_selector")
          ) {
            Text(
              text = "Preset: ${track.presetName} ▼",
              style = MaterialTheme.typography.labelSmall,
              color = NeonCyan
            )
            DropdownMenu(
              expanded = showPresetMenu,
              onDismissRequest = { showPresetMenu = false }
            ) {
              PresetDatabase.presets.forEach { preset ->
                DropdownMenuItem(
                  text = { Text("${preset.name} (${preset.category})") },
                  onClick = {
                    showPresetMenu = false
                    onPresetSelected(preset.id)
                  }
                )
              }
            }
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // Quick Piano Roll Button
        IconButton(onClick = onOpenClipEditor, modifier = Modifier.size(36.dp).testTag("open_clip_editor_button")) {
          Icon(Icons.Default.Edit, contentDescription = "Piano Roll", tint = NeonAmber, modifier = Modifier.size(20.dp))
        }
        // Synth Parameters Toggle
        if (track.instrumentType != InstrumentType.DRUM_KIT) {
          IconButton(onClick = { showParamsSheet = !showParamsSheet }, modifier = Modifier.size(36.dp).testTag("sound_params_button")) {
            Icon(Icons.Default.Tune, contentDescription = "Sound Parameters", tint = if (showParamsSheet) NeonCyan else TextSecondary, modifier = Modifier.size(20.dp))
          }
        }
      }
    }

    // Interactive Sound Parameter Controls Drawer (Cutoff, Resonance, Attack, Release)
    if (showParamsSheet && track.instrumentType != InstrumentType.DRUM_KIT) {
      SoundParametersPanel(
        params = track.synthParams,
        onParamsChanged = onSynthParamsChanged,
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF14161A))
          .border(0.5.dp, CharcoalBorder)
          .padding(12.dp)
      )
    }

    // Melodic Octave Controls (for Synth Pads / Bass / Leads)
    if (track.instrumentType != InstrumentType.DRUM_KIT) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "OCTAVE: ",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = TextMuted
          )
          Text(
            text = if (octaveOffset >= 0) "+$octaveOffset" else "$octaveOffset",
            style = MaterialTheme.typography.labelSmall.copy(
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = NeonCyan
            )
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(CharcoalSurfaceVariant)
              .clickable { octaveOffset = (octaveOffset - 1).coerceAtLeast(-2) }
              .testTag("octave_down"),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Octave Down", tint = TextPrimary, modifier = Modifier.size(20.dp))
          }
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(CharcoalSurfaceVariant)
              .clickable { octaveOffset = (octaveOffset + 1).coerceAtMost(2) }
              .testTag("octave_up"),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Octave Up", tint = TextPrimary, modifier = Modifier.size(20.dp))
          }
        }
      }
    }

    // Main 16-Pad Performance Matrix (4x4 Drum Pads or Melodic Scale Grid)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(12.dp),
      contentAlignment = Alignment.Center
    ) {
      if (track.instrumentType == InstrumentType.DRUM_KIT) {
        // 4x4 Drum Pads Matrix (16 Pads)
        DrumPadsMatrix(
          drumPads = track.drumPads,
          onPadTrigger = onDrumTrigger,
          trackColor = trackColor
        )
      } else {
        // Melodic Scale Matrix (4x4 scale-constrained playable notes)
        MelodicPadsMatrix(
          rootNote = project.rootNote,
          scaleType = project.scaleType,
          octaveOffset = octaveOffset,
          baseOctave = if (track.instrumentType == InstrumentType.SYNTH_BASS) 2 else 4,
          trackColor = trackColor,
          onNoteOn = onNoteOn,
          onNoteOff = onNoteOff
        )
      }
    }
  }
}

@Composable
fun DrumPadsMatrix(
  drumPads: List<DrumPadAssignment>,
  onPadTrigger: (DrumPadAssignment) -> Unit,
  trackColor: Color,
  modifier: Modifier = Modifier
) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    userScrollEnabled = false
  ) {
    items(16) { padIndex ->
      // Invert row ordering so Pad 1 (Kick) is bottom-left (standard MPC / Ableton Note layout)
      val row = padIndex / 4
      val col = padIndex % 4
      val mpcIndex = (3 - row) * 4 + col

      val pad = drumPads.getOrNull(mpcIndex) ?: DrumPadAssignment(
        padIndex = mpcIndex,
        name = "Pad ${mpcIndex + 1}",
        midiNote = 36 + mpcIndex,
        sampleId = "kick"
      )

      var isPressed by remember { mutableStateOf(false) }

      Box(
        modifier = Modifier
          .aspectRatio(1f)
          .clip(RoundedCornerShape(12.dp))
          .background(
            if (isPressed) trackColor else CharcoalSurface
          )
          .border(
            width = if (isPressed) 2.dp else 1.dp,
            color = if (isPressed) Color.White else CharcoalBorder,
            shape = RoundedCornerShape(12.dp)
          )
          .pointerInput(pad) {
            detectTapGestures(
              onPress = {
                isPressed = true
                onPadTrigger(pad)
                tryAwaitRelease()
                isPressed = false
              }
            )
          }
          .padding(8.dp)
          .testTag("drum_pad_$mpcIndex")
      ) {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "${mpcIndex + 1}",
            style = MaterialTheme.typography.labelSmall.copy(
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp
            ),
            color = if (isPressed) Color.Black else TextMuted
          )
          Text(
            text = pad.name,
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.SemiBold,
              fontSize = 12.sp
            ),
            color = if (isPressed) Color.Black else TextPrimary,
            maxLines = 1
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MelodicPadsMatrix(
  rootNote: RootNote,
  scaleType: ScaleType,
  octaveOffset: Int,
  baseOctave: Int,
  trackColor: Color,
  onNoteOn: (pitch: Int, velocity: Float) -> Unit,
  onNoteOff: (pitch: Int) -> Unit,
  modifier: Modifier = Modifier
) {
  // Generate 16 scale notes starting from baseOctave + octaveOffset
  val scaleNotes = remember(rootNote, scaleType, octaveOffset, baseOctave) {
    val notes = mutableListOf<Int>()
    val baseMidi = (baseOctave + octaveOffset + 1) * 12 + rootNote.semitone
    var oct = 0
    while (notes.size < 16) {
      for (interval in scaleType.intervals) {
        if (notes.size < 16) {
          notes.add(baseMidi + oct * 12 + interval)
        }
      }
      oct++
    }
    notes
  }

  LazyVerticalGrid(
    columns = GridCells.Fixed(4),
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    userScrollEnabled = false
  ) {
    items(16) { padIndex ->
      // Invert row ordering so lower notes start at bottom
      val row = padIndex / 4
      val col = padIndex % 4
      val noteIndex = (3 - row) * 4 + col
      val pitch = scaleNotes.getOrElse(noteIndex) { 60 }
      val noteName = MusicalTimeUtils.midiPitchToString(pitch)
      val isRoot = (pitch % 12) == rootNote.semitone

      var isPressed by remember { mutableStateOf(false) }

      Box(
        modifier = Modifier
          .aspectRatio(1f)
          .clip(RoundedCornerShape(12.dp))
          .background(
            when {
              isPressed -> trackColor
              isRoot -> trackColor.copy(alpha = 0.35f)
              else -> CharcoalSurface
            }
          )
          .border(
            width = if (isPressed) 2.dp else if (isRoot) 1.5.dp else 1.dp,
            color = if (isPressed) Color.White else if (isRoot) trackColor else CharcoalBorder,
            shape = RoundedCornerShape(12.dp)
          )
          .pointerInteropFilter { motionEvent ->
            when (motionEvent.action) {
              MotionEvent.ACTION_DOWN -> {
                isPressed = true
                onNoteOn(pitch, 0.85f)
                true
              }
              MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                onNoteOff(pitch)
                true
              }
              else -> false
            }
          }
          .padding(8.dp)
          .testTag("melodic_pad_$noteIndex")
      ) {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = noteName,
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
              ),
              color = if (isPressed) Color.Black else if (isRoot) NeonCyan else TextPrimary
            )
            if (isRoot) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(if (isPressed) Color.Black else NeonCyan)
              )
            }
          }
          Text(
            text = "MIDI $pitch",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (isPressed) Color.Black else TextMuted
          )
        }
      }
    }
  }
}

@Composable
fun SoundParametersPanel(
  params: SynthParams,
  onParamsChanged: (SynthParams) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text(
      text = "SOUND SHAPING CONTROLS",
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
      color = NeonCyan
    )

    // Cutoff Slider
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("Filter Cutoff", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
      Text("${params.cutoffHz.toInt()} Hz", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
    }
    Slider(
      value = params.cutoffHz,
      onValueChange = { onParamsChanged(params.copy(cutoffHz = it)) },
      valueRange = 100f..8000f,
      colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan),
      modifier = Modifier.height(28.dp).testTag("cutoff_slider")
    )

    // Attack Slider
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("Attack Time", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
      Text("${params.attackMs.toInt()} ms", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
    }
    Slider(
      value = params.attackMs,
      onValueChange = { onParamsChanged(params.copy(attackMs = it)) },
      valueRange = 2f..500f,
      colors = SliderDefaults.colors(thumbColor = NeonAmber, activeTrackColor = NeonAmber),
      modifier = Modifier.height(28.dp).testTag("attack_slider")
    )

    // Release Slider
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("Release Time", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
      Text("${params.releaseMs.toInt()} ms", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
    }
    Slider(
      value = params.releaseMs,
      onValueChange = { onParamsChanged(params.copy(releaseMs = it)) },
      valueRange = 20f..1000f,
      colors = SliderDefaults.colors(thumbColor = NeonEmerald, activeTrackColor = NeonEmerald),
      modifier = Modifier.height(28.dp).testTag("release_slider")
    )
  }
}
