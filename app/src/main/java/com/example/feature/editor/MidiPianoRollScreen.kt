package com.example.feature.editor

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Clip
import com.example.core.model.NoteEvent
import com.example.core.model.NoteQuantization
import com.example.core.model.Track
import com.example.core.music.MusicalTimeUtils
import com.example.ui.theme.CharcoalBackground
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCoral
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.UUID

@Composable
fun MidiPianoRollScreen(
  track: Track,
  clip: Clip,
  isPlaying: Boolean,
  currentTick: Long,
  onPlayToggle: () -> Unit,
  onNotesChanged: (List<NoteEvent>) -> Unit,
  onClipLengthChanged: (bars: Int) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var selectedNoteId by remember { mutableStateOf<String?>(null) }
  var gridQuantization by remember { mutableStateOf(NoteQuantization.SIXTEENTH) }
  var showGridMenu by remember { mutableStateOf(false) }

  // Undo / Redo history stacks
  var undoStack by remember { mutableStateOf(listOf<List<NoteEvent>>()) }
  var redoStack by remember { mutableStateOf(listOf<List<NoteEvent>>()) }

  fun applyNotesChange(newNotes: List<NoteEvent>) {
    undoStack = undoStack + listOf(clip.notes)
    redoStack = emptyList()
    onNotesChanged(newNotes)
  }

  fun performUndo() {
    if (undoStack.isNotEmpty()) {
      val prev = undoStack.last()
      undoStack = undoStack.dropLast(1)
      redoStack = redoStack + listOf(clip.notes)
      onNotesChanged(prev)
    }
  }

  fun performRedo() {
    if (redoStack.isNotEmpty()) {
      val next = redoStack.last()
      redoStack = redoStack.dropLast(1)
      undoStack = undoStack + listOf(clip.notes)
      onNotesChanged(next)
    }
  }

  // Pitch range for piano roll
  val minPitch = if (track.instrumentType == com.example.core.model.InstrumentType.DRUM_KIT) 35 else 36 // C2
  val maxPitch = if (track.instrumentType == com.example.core.model.InstrumentType.DRUM_KIT) 56 else 84 // C6
  val pitchCount = maxPitch - minPitch + 1

  // Visual dimensions
  val rowHeightDp = 24.dp
  val totalBars = clip.lengthBars
  val ticksPerBar = MusicalTimeUtils.TICKS_PER_BAR
  val totalTicks = totalBars * ticksPerBar
  val pxPerTick = 0.25f // Scale factor for horizontal time grid

  val horizontalScroll = rememberScrollState()
  val verticalScroll = rememberScrollState(initial = (pitchCount / 3) * 24)

  val trackColor = Color(track.color)

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(CharcoalBackground)
  ) {
    // Header Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF181B20))
        .padding(horizontal = 12.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp).testTag("piano_roll_back")) {
          Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
        Column {
          Text(
            text = "${track.name} · ${clip.name}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
          )
          Text(
            text = "${clip.lengthBars} Bars · ${clip.notes.size} Notes",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        // Grid Snap Selector
        Box(modifier = Modifier.clickable { showGridMenu = true }) {
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(Color(0xFF22262C))
              .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.GridOn, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = gridQuantization.displayName,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = NeonAmber
            )
          }
          DropdownMenu(expanded = showGridMenu, onDismissRequest = { showGridMenu = false }) {
            NoteQuantization.values().forEach { q ->
              DropdownMenuItem(
                text = { Text("Grid: ${q.displayName}") },
                onClick = {
                  gridQuantization = q
                  showGridMenu = false
                }
              )
            }
          }
        }

        // Undo Button
        IconButton(
          onClick = { performUndo() },
          enabled = undoStack.isNotEmpty(),
          modifier = Modifier.size(36.dp).testTag("piano_roll_undo")
        ) {
          Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (undoStack.isNotEmpty()) TextPrimary else TextMuted, modifier = Modifier.size(18.dp))
        }

        // Redo Button
        IconButton(
          onClick = { performRedo() },
          enabled = redoStack.isNotEmpty(),
          modifier = Modifier.size(36.dp).testTag("piano_roll_redo")
        ) {
          Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (redoStack.isNotEmpty()) TextPrimary else TextMuted, modifier = Modifier.size(18.dp))
        }

        // Playhead Toggle
        IconButton(
          onClick = onPlayToggle,
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isPlaying) NeonCyan else Color(0xFF22252C))
            .testTag("piano_roll_play")
        ) {
          Icon(
            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = if (isPlaying) Color.Black else NeonCyan,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }

    // Secondary Tools Bar: Delete Note, Quantize, Duplicate, Clear
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF14161A))
        .padding(horizontal = 12.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (selectedNoteId != null) {
        val selectedNote = clip.notes.firstOrNull { it.id == selectedNoteId }
        // Delete selected note
        ToolChip(
          text = "DELETE NOTE",
          color = NeonCoral,
          icon = Icons.Default.Delete,
          onClick = {
            applyNotesChange(clip.notes.filter { it.id != selectedNoteId })
            selectedNoteId = null
          }
        )
        // Quantize selected note
        ToolChip(
          text = "QUANTIZE",
          color = NeonAmber,
          icon = Icons.Default.GridOn,
          onClick = {
            if (selectedNote != null) {
              val gridTicks = if (gridQuantization.ticks > 0) gridQuantization.ticks else 240L
              val qTick = MusicalTimeUtils.quantizeTick(selectedNote.startTick, gridTicks)
              val updated = clip.notes.map { if (it.id == selectedNoteId) it.copy(startTick = qTick) else it }
              applyNotesChange(updated)
            }
          }
        )
      } else {
        // Quantize all notes
        ToolChip(
          text = "QUANTIZE ALL",
          color = NeonAmber,
          icon = Icons.Default.GridOn,
          onClick = {
            val gridTicks = if (gridQuantization.ticks > 0) gridQuantization.ticks else 240L
            val updated = clip.notes.map { it.copy(startTick = MusicalTimeUtils.quantizeTick(it.startTick, gridTicks)) }
            applyNotesChange(updated)
          }
        )
        // Clear all notes
        ToolChip(
          text = "CLEAR ALL",
          color = TextMuted,
          icon = Icons.Default.Clear,
          onClick = {
            applyNotesChange(emptyList())
            selectedNoteId = null
          }
        )
      }
    }

    // Piano Roll Workspace: Piano Key Labels on Left + Interactive Note Canvas on Right
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      // Left: Piano Keys / Pitch Labels
      Column(
        modifier = Modifier
          .width(52.dp)
          .fillMaxHeight()
          .verticalScroll(verticalScroll)
          .background(Color(0xFF16181D))
      ) {
        for (pitch in maxPitch downTo minPitch) {
          val isBlackKey = MusicalTimeUtils.NOTE_NAMES[Math.floorMod(pitch, 12)].contains("#")
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(rowHeightDp)
              .background(if (isBlackKey) Color(0xFF20232A) else Color(0xFF2C3038))
              .border(0.5.dp, CharcoalBorder),
            contentAlignment = Alignment.CenterEnd
          ) {
            Text(
              text = MusicalTimeUtils.midiPitchToString(pitch),
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = if (isBlackKey) TextMuted else TextPrimary
              ),
              modifier = Modifier.padding(end = 4.dp)
            )
          }
        }
      }

      // Right: Interactive Note Grid Canvas
      val gridWidthDp = (totalTicks * pxPerTick).dp
      val gridHeightDp = (pitchCount * 24).dp

      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .horizontalScroll(horizontalScroll)
          .verticalScroll(verticalScroll)
      ) {
        Canvas(
          modifier = Modifier
            .size(width = gridWidthDp, height = gridHeightDp)
            .pointerInput(clip, gridQuantization) {
              detectTapGestures { offset ->
                val tickAtTap = (offset.x / pxPerTick).toLong().coerceIn(0L, totalTicks - 1)
                val pitchIndexAtTap = (offset.y / 24.dp.toPx()).toInt().coerceIn(0, pitchCount - 1)
                val pitchAtTap = maxPitch - pitchIndexAtTap

                // Check if tapped an existing note
                val hitNote = clip.notes.firstOrNull { note ->
                  val noteStartPx = note.startTick * pxPerTick
                  val noteEndPx = (note.startTick + note.durationTicks) * pxPerTick
                  note.pitch == pitchAtTap && offset.x in noteStartPx..noteEndPx
                }

                if (hitNote != null) {
                  // Toggle note selection
                  selectedNoteId = if (selectedNoteId == hitNote.id) null else hitNote.id
                } else {
                  // Insert new note with grid quantization snap
                  val gridTicks = if (gridQuantization.ticks > 0) gridQuantization.ticks else 240L
                  val snappedTick = MusicalTimeUtils.quantizeTick(tickAtTap, gridTicks).coerceIn(0L, totalTicks - gridTicks)

                  val newNote = NoteEvent(
                    id = UUID.randomUUID().toString(),
                    pitch = pitchAtTap,
                    velocity = 0.85f,
                    startTick = snappedTick,
                    durationTicks = gridTicks
                  )
                  applyNotesChange(clip.notes + newNote)
                  selectedNoteId = newNote.id
                }
              }
            }
        ) {
          val rowHeightPx = 24.dp.toPx()
          val barWidthPx = ticksPerBar * pxPerTick
          val beatWidthPx = MusicalTimeUtils.TICKS_PER_BEAT * pxPerTick
          val stepWidthPx = (gridQuantization.ticks.takeIf { it > 0 } ?: 240L) * pxPerTick

          // 1. Draw horizontal pitch grid rows
          for (p in 0 until pitchCount) {
            val y = p * rowHeightPx
            val pitch = maxPitch - p
            val isBlackKey = MusicalTimeUtils.NOTE_NAMES[Math.floorMod(pitch, 12)].contains("#")
            drawRect(
              color = if (isBlackKey) Color(0xFF14161A) else Color(0xFF1A1C22),
              topLeft = Offset(0f, y),
              size = Size(size.width, rowHeightPx)
            )
            drawLine(
              color = Color(0xFF262930),
              start = Offset(0f, y),
              end = Offset(size.width, y),
              strokeWidth = 1f
            )
          }

          // 2. Draw vertical time subdivisions (16ths / beats / bars)
          var x = 0f
          while (x < size.width) {
            val tick = (x / pxPerTick).toLong()
            val isBar = (tick % ticksPerBar) == 0L
            val isBeat = (tick % MusicalTimeUtils.TICKS_PER_BEAT) == 0L

            val lineColor = when {
              isBar -> Color(0xFF555B68)
              isBeat -> Color(0xFF353942)
              else -> Color(0xFF22252B)
            }
            val stroke = if (isBar) 2f else 1f

            drawLine(
              color = lineColor,
              start = Offset(x, 0f),
              end = Offset(x, size.height),
              strokeWidth = stroke
            )
            x += stepWidthPx
          }

          // 3. Render MIDI Notes as modern rounded rectangles
          clip.notes.forEach { note ->
            val notePitchIndex = maxPitch - note.pitch
            if (notePitchIndex in 0 until pitchCount) {
              val noteX = note.startTick * pxPerTick
              val noteY = notePitchIndex * rowHeightPx + 2f
              val noteW = (note.durationTicks * pxPerTick).coerceAtLeast(6f)
              val noteH = rowHeightPx - 4f

              val isSelected = note.id == selectedNoteId

              drawRoundRect(
                color = if (isSelected) NeonAmber else trackColor,
                topLeft = Offset(noteX, noteY),
                size = Size(noteW, noteH),
                cornerRadius = CornerRadius(4f, 4f)
              )

              if (isSelected) {
                // Glow border for selected note
                drawRoundRect(
                  color = Color.White,
                  topLeft = Offset(noteX, noteY),
                  size = Size(noteW, noteH),
                  cornerRadius = CornerRadius(4f, 4f),
                  style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
              }
            }
          }

          // 4. Synchronized Playhead Line
          if (isPlaying) {
            val clipLoopTick = currentTick % clip.lengthTicks
            val playheadX = clipLoopTick * pxPerTick
            drawLine(
              color = Color.White,
              start = Offset(playheadX, 0f),
              end = Offset(playheadX, size.height),
              strokeWidth = 2.5f
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ToolChip(
  text: String,
  color: Color,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(color.copy(alpha = 0.15f))
      .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
      .clickable { onClick() }
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.Bold,
          color = color,
          fontSize = 10.sp
        )
      )
    }
  }
}
