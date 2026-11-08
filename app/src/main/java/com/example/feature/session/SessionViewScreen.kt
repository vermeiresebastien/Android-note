package com.example.feature.session

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Clip
import com.example.core.model.Project
import com.example.core.model.Track
import com.example.ui.components.TopTransportBar
import com.example.ui.theme.CharcoalBackground
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CharcoalSurfaceVariant
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCoral
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SessionViewScreen(
  project: Project,
  selectedTrackIndex: Int,
  activeClips: Map<Int, Clip?>,
  pendingClips: Map<Int, Clip?>,
  isPlaying: Boolean,
  isRecording: Boolean,
  currentBar: Int,
  currentBeat: Int,
  peakL: Float,
  peakR: Float,
  onPlayToggle: () -> Unit,
  onRecordToggle: () -> Unit,
  onCaptureClick: () -> Unit,
  onLaunchClip: (trackIndex: Int, sceneIndex: Int) -> Unit,
  onStopTrack: (trackIndex: Int) -> Unit,
  onLaunchScene: (sceneIndex: Int) -> Unit,
  onSelectTrack: (trackIndex: Int) -> Unit,
  onOpenInstrument: (trackIndex: Int) -> Unit,
  onOpenClipEditor: (trackIndex: Int, sceneIndex: Int) -> Unit,
  onCreateClip: (trackIndex: Int, sceneIndex: Int) -> Unit,
  onClearClip: (trackIndex: Int, sceneIndex: Int) -> Unit,
  onDuplicateClip: (trackIndex: Int, sceneIndex: Int) -> Unit,
  onOpenMixer: () -> Unit,
  onOpenSettings: () -> Unit,
  onExport: () -> Unit,
  onBackToLibrary: () -> Unit,
  modifier: Modifier = Modifier
) {
  val horizontalScrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(CharcoalBackground)
  ) {
    // Top Bar with Transport, Playhead, Peak Meters
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
      onTempoClick = onOpenSettings,
      onCaptureClick = onCaptureClick
    )

    // Contextual Action Row: Back to Library, Mixer, Settings, Export
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF181A1F))
        .padding(horizontal = 12.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBackToLibrary, modifier = Modifier.size(36.dp).testTag("back_to_library_button")) {
          Icon(Icons.Default.ArrowBack, contentDescription = "Library", tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
        Text(
          text = "Session Grid",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          color = TextPrimary
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // Mixer Toggle
        IconButton(onClick = onOpenMixer, modifier = Modifier.size(36.dp).testTag("open_mixer_button")) {
          Icon(Icons.Default.VolumeUp, contentDescription = "Mixer", tint = NeonCyan, modifier = Modifier.size(20.dp))
        }
        // Export
        IconButton(onClick = onExport, modifier = Modifier.size(36.dp).testTag("export_button")) {
          Icon(Icons.Default.Share, contentDescription = "Export Live Transfer", tint = NeonAmber, modifier = Modifier.size(20.dp))
        }
        // Settings
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp).testTag("settings_button")) {
          Icon(Icons.Default.Settings, contentDescription = "Project Settings", tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
      }
    }

    // Main 8-Track x 8-Scene Clip Launching Grid
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      // Horizontally scrollable track columns
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .horizontalScroll(horizontalScrollState)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          project.tracks.forEach { track ->
            TrackColumn(
              track = track,
              isSelected = track.index == selectedTrackIndex,
              activeClip = activeClips[track.index],
              pendingClip = pendingClips[track.index],
              isPlaying = isPlaying,
              onSelectTrack = { onSelectTrack(track.index) },
              onOpenInstrument = { onOpenInstrument(track.index) },
              onLaunchClip = { sceneIdx -> onLaunchClip(track.index, sceneIdx) },
              onOpenClipEditor = { sceneIdx -> onOpenClipEditor(track.index, sceneIdx) },
              onCreateClip = { sceneIdx -> onCreateClip(track.index, sceneIdx) },
              onClearClip = { sceneIdx -> onClearClip(track.index, sceneIdx) },
              onDuplicateClip = { sceneIdx -> onDuplicateClip(track.index, sceneIdx) },
              onStopTrack = { onStopTrack(track.index) }
            )
          }
        }
      }

      // Fixed Scene Launch Column on the Right (Ableton Note style)
      SceneLaunchColumn(
        scenes = project.scenes,
        onLaunchScene = onLaunchScene,
        modifier = Modifier
          .width(56.dp)
          .fillMaxHeight()
          .background(Color(0xFF14161A))
          .border(0.5.dp, CharcoalBorder)
          .padding(vertical = 6.dp)
      )
    }

    // Bottom Selected Track Quick Performance Bar
    val currentTrack = project.tracks.getOrNull(selectedTrackIndex)
    if (currentTrack != null) {
      SelectedTrackFooter(
        track = currentTrack,
        onOpenInstrument = { onOpenInstrument(currentTrack.index) },
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF181B20))
          .border(0.5.dp, CharcoalBorder)
          .padding(horizontal = 16.dp, vertical = 10.dp)
      )
    }
  }
}

@Composable
fun TrackColumn(
  track: Track,
  isSelected: Boolean,
  activeClip: Clip?,
  pendingClip: Clip?,
  isPlaying: Boolean,
  onSelectTrack: () -> Unit,
  onOpenInstrument: () -> Unit,
  onLaunchClip: (sceneIndex: Int) -> Unit,
  onOpenClipEditor: (sceneIndex: Int) -> Unit,
  onCreateClip: (sceneIndex: Int) -> Unit,
  onClearClip: (sceneIndex: Int) -> Unit,
  onDuplicateClip: (sceneIndex: Int) -> Unit,
  onStopTrack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val trackColor = Color(track.color)

  Column(
    modifier = modifier
      .width(96.dp)
      .fillMaxHeight(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Track Header: Name + Instrument Type Indicator
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(if (isSelected) trackColor.copy(alpha = 0.25f) else CharcoalSurface)
        .border(
          width = if (isSelected) 2.dp else 1.dp,
          color = if (isSelected) trackColor else CharcoalBorder,
          shape = RoundedCornerShape(8.dp)
        )
        .clickable {
          onSelectTrack()
          onOpenInstrument()
        }
        .padding(6.dp)
        .testTag("track_header_${track.index}")
    ) {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = track.name,
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
          ),
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(trackColor)
          )
          Text(
            text = track.presetName,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // 8 Clip Slots for this track
    LazyColumn(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      items(8) { sceneIdx ->
        val clip = track.clips.getOrNull(sceneIdx)
        val isSlotActive = activeClip != null && activeClip.sceneIndex == sceneIdx && isPlaying
        val isSlotPending = pendingClip != null && pendingClip.sceneIndex == sceneIdx

        ClipSlotCell(
          clip = clip,
          trackColor = trackColor,
          isActive = isSlotActive,
          isPending = isSlotPending,
          onLaunch = { onLaunchClip(sceneIdx) },
          onEdit = { onOpenClipEditor(sceneIdx) },
          onCreate = { onCreateClip(sceneIdx) },
          onClear = { onClearClip(sceneIdx) },
          onDuplicate = { onDuplicateClip(sceneIdx) },
          testTag = "clip_slot_${track.index}_$sceneIdx"
        )
      }

      // Stop Track Slot at bottom
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(CharcoalSurfaceVariant)
            .clickable { onStopTrack() }
            .testTag("stop_track_${track.index}"),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Stop, contentDescription = "Stop Track", tint = TextMuted, modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}

@Composable
fun ClipSlotCell(
  clip: Clip?,
  trackColor: Color,
  isActive: Boolean,
  isPending: Boolean,
  onLaunch: () -> Unit,
  onEdit: () -> Unit,
  onCreate: () -> Unit,
  onClear: () -> Unit,
  onDuplicate: () -> Unit,
  testTag: String
) {
  var showMenu by remember { mutableStateOf(false) }

  val targetBg = when {
    isActive -> trackColor.copy(alpha = 0.85f)
    isPending -> NeonAmber.copy(alpha = 0.5f)
    clip != null && clip.hasNotes -> trackColor.copy(alpha = 0.35f)
    else -> CharcoalSurface
  }
  val animatedBg by animateColorAsState(targetValue = targetBg, label = "clip_bg")

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(58.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(animatedBg)
      .border(
        width = if (isActive || isPending) 1.5.dp else 1.dp,
        color = if (isActive) Color.White else if (isPending) NeonAmber else CharcoalBorder,
        shape = RoundedCornerShape(8.dp)
      )
      .pointerInput(Unit) {
        detectTapGestures(
          onTap = {
            if (clip != null && clip.hasNotes) {
              onLaunch()
            } else {
              onCreate()
            }
          },
          onLongPress = {
            if (clip != null) {
              showMenu = true
            }
          }
        )
      }
      .padding(6.dp)
      .testTag(testTag)
  ) {
    if (clip != null && clip.hasNotes) {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "${clip.lengthBars}B",
            style = MaterialTheme.typography.labelSmall.copy(
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp
            ),
            color = if (isActive) Color.Black else TextPrimary
          )
          if (isActive) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Playing", tint = Color.Black, modifier = Modifier.size(14.dp))
          }
        }

        // Mini note density visualizer
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
          verticalAlignment = Alignment.Bottom
        ) {
          val noteCount = clip.notes.size.coerceAtMost(6)
          for (i in 0 until 6) {
            val barH = if (i < noteCount) ((i + 2) * 2).dp else 2.dp
            Box(
              modifier = Modifier
                .weight(1f)
                .height(barH)
                .background(if (isActive) Color.Black.copy(alpha = 0.7f) else trackColor.copy(alpha = 0.8f))
            )
          }
        }
      }
    } else {
      // Empty slot with + affordance
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Add, contentDescription = "Add Clip", tint = TextMuted.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
      }
    }

    // Context Dropdown Menu on Long Press
    DropdownMenu(
      expanded = showMenu,
      onDismissRequest = { showMenu = false }
    ) {
      DropdownMenuItem(
        text = { Text("Edit in Piano Roll") },
        onClick = {
          showMenu = false
          onEdit()
        }
      )
      DropdownMenuItem(
        text = { Text("Duplicate Clip") },
        onClick = {
          showMenu = false
          onDuplicate()
        }
      )
      DropdownMenuItem(
        text = { Text("Clear Notes", color = NeonCoral) },
        onClick = {
          showMenu = false
          onClear()
        }
      )
    }
  }
}

@Composable
fun SceneLaunchColumn(
  scenes: List<com.example.core.model.Scene>,
  onLaunchScene: (sceneIndex: Int) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Header
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = "SCENE",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
        color = TextMuted
      )
    }

    Spacer(modifier = Modifier.height(6.dp))

    // 8 Scene Launch Buttons
    LazyColumn(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      items(scenes.size) { sceneIdx ->
        Box(
          modifier = Modifier
            .size(width = 46.dp, height = 58.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CharcoalSurfaceVariant)
            .border(1.dp, CharcoalBorder, RoundedCornerShape(8.dp))
            .clickable { onLaunchScene(sceneIdx) }
            .testTag("launch_scene_$sceneIdx"),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Launch Scene", tint = NeonCyan, modifier = Modifier.size(18.dp))
            Text(
              text = "${sceneIdx + 1}",
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
              ),
              color = TextPrimary
            )
          }
        }
      }
    }
  }
}

@Composable
fun SelectedTrackFooter(
  track: Track,
  onOpenInstrument: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(12.dp)
          .clip(CircleShape)
          .background(Color(track.color))
      )
      Column {
        Text(
          text = track.name,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = TextPrimary
        )
        Text(
          text = "${track.instrumentType.name.replace("_", " ")} · ${track.presetName}",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary
        )
      }
    }

    // Direct button to open instrument pads
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(NeonCyan)
        .clickable { onOpenInstrument() }
        .padding(horizontal = 14.dp, vertical = 8.dp)
        .testTag("open_instrument_pads")
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
        Text(
          text = "PLAY PADS",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            color = Color.Black
          )
        )
      }
    }
  }
}
