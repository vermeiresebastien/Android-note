package com.example.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.LaunchQuantization
import com.example.core.model.Project
import com.example.core.model.RootNote
import com.example.core.model.ScaleType
import com.example.ui.theme.CharcoalBackground
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CharcoalSurfaceVariant
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ProjectSettingsScreen(
  project: Project,
  onBpmChanged: (Double) -> Unit,
  onRootNoteChanged: (RootNote) -> Unit,
  onScaleTypeChanged: (ScaleType) -> Unit,
  onLaunchQuantizationChanged: (LaunchQuantization) -> Unit,
  onGrooveChanged: (Float) -> Unit,
  onExportLiveBundle: () -> Unit,
  onExportWav: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  var tapTimes by remember { mutableStateOf(listOf<Long>()) }
  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(CharcoalBackground)
  ) {
    // Top Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF181B20))
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(onClick = onBack, modifier = Modifier.size(36.dp).testTag("settings_back")) {
        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary, modifier = Modifier.size(20.dp))
      }
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "SESSION & MUSICAL SETTINGS",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = TextPrimary
      )
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      // 1. TEMPO & TAP TEMPO
      SettingsCard(title = "TEMPO (BPM)", icon = Icons.Default.Speed) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "${project.tempoBpm.toInt()} BPM",
              style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
              color = NeonAmber
            )

            // Tap Tempo Button
            Button(
              onClick = {
                val now = System.currentTimeMillis()
                val recent = tapTimes.filter { now - it < 2500 } + listOf(now)
                tapTimes = recent
                if (recent.size >= 2) {
                  val diffs = recent.zipWithNext { a, b -> b - a }
                  val avgDiffMs = diffs.average()
                  if (avgDiffMs > 100) {
                    val calcBpm = (60000.0 / avgDiffMs).coerceIn(40.0, 240.0)
                    onBpmChanged(calcBpm)
                  }
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E333D)),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.testTag("tap_tempo_button")
            ) {
              Text("TAP TEMPO", color = NeonAmber, fontWeight = FontWeight.Bold)
            }
          }

          // Tempo Slider
          Slider(
            value = project.tempoBpm.toFloat(),
            onValueChange = { onBpmChanged(it.toDouble()) },
            valueRange = 50f..200f,
            colors = SliderDefaults.colors(thumbColor = NeonAmber, activeTrackColor = NeonAmber)
          )
        }
      }

      // 2. ROOT KEY & MUSICAL SCALE
      SettingsCard(title = "KEY & SCALE CONSTRAINTS", icon = Icons.Default.MusicNote) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Root Key", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          // Root notes grid
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            RootNote.values().take(6).forEach { root ->
              KeyPill(
                label = root.displayName,
                isSelected = project.rootNote == root,
                onClick = { onRootNoteChanged(root) },
                modifier = Modifier.weight(1f)
              )
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            RootNote.values().drop(6).forEach { root ->
              KeyPill(
                label = root.displayName,
                isSelected = project.rootNote == root,
                onClick = { onRootNoteChanged(root) },
                modifier = Modifier.weight(1f)
              )
            }
          }

          Spacer(modifier = Modifier.height(4.dp))
          Text("Scale Mode", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            ScaleType.values().forEach { scale ->
              KeyPill(
                label = scale.displayName,
                isSelected = project.scaleType == scale,
                onClick = { onScaleTypeChanged(scale) },
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }

      // 3. LAUNCH QUANTIZATION & SWING
      SettingsCard(title = "LAUNCH QUANTIZATION & GROOVE", icon = Icons.Default.Info) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("Clip Launch Quantization", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            LaunchQuantization.values().forEach { lq ->
              KeyPill(
                label = lq.displayName,
                isSelected = project.launchQuantization == lq,
                onClick = { onLaunchQuantizationChanged(lq) },
                modifier = Modifier.weight(1f)
              )
            }
          }

          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Groove / Swing", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text("${(project.grooveAmount * 100).toInt()}%", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = NeonCyan)
          }
          Slider(
            value = project.grooveAmount,
            onValueChange = onGrooveChanged,
            valueRange = 0f..0.75f,
            colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
          )
        }
      }

      // 4. EXPORT ACTIONS (Live Transfer Bundle & WAV Mixdown)
      SettingsCard(title = "ABLETON LIVE 12 & AUDIO EXPORT", icon = Icons.Default.Share) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Export editable project bundles (.pocketbundle) with SMF multitrack MIDI, device mapping manifest (live-transfer.json), and master WAV mixdowns.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
          )

          Button(
            onClick = onExportLiveBundle,
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("export_live_bundle_button")
          ) {
            Text("EXPORT ABLETON LIVE 12 BUNDLE", color = Color.Black, fontWeight = FontWeight.Bold)
          }

          OutlinedButton(
            onClick = onExportWav,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAmber),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("export_wav_mixdown_button")
          ) {
            Text("EXPORT STEREO WAV MIXDOWN (44.1 kHz, 16-BIT)")
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsCard(
  title: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  content: @Composable () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(CharcoalSurface)
      .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
      .padding(16.dp)
  ) {
    Column {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
          color = TextPrimary
        )
      }
      Spacer(modifier = Modifier.height(12.dp))
      content()
    }
  }
}

@Composable
private fun KeyPill(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .height(34.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(if (isSelected) NeonCyan else CharcoalSurfaceVariant)
      .border(1.dp, if (isSelected) NeonCyan else CharcoalBorder, RoundedCornerShape(6.dp))
      .clickable { onClick() },
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp
      ),
      color = if (isSelected) Color.Black else TextPrimary
    )
  }
}
