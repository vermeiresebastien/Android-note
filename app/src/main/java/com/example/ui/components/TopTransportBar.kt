package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCoral
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TopTransportBar(
  projectName: String,
  tempoBpm: Double,
  bar: Int,
  beat: Int,
  isPlaying: Boolean,
  isRecording: Boolean,
  peakL: Float,
  peakR: Float,
  onPlayToggle: () -> Unit,
  onRecordToggle: () -> Unit,
  onTempoClick: () -> Unit,
  onCaptureClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(Color(0xFF14161A))
      .padding(horizontal = 12.dp, vertical = 8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Left: Project Name + Musical Clock
      Column {
        Text(
          text = projectName,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = TextPrimary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "$bar.$beat.1",
            style = MaterialTheme.typography.labelSmall.copy(
              fontFamily = FontFamily.Monospace,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold
            ),
            color = if (isPlaying) NeonCyan else TextMuted
          )
          Spacer(modifier = Modifier.width(12.dp))
          // BPM chip
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(Color(0xFF22262C))
              .clickable { onTempoClick() }
              .padding(horizontal = 6.dp, vertical = 2.dp)
              .testTag("tempo_button")
          ) {
            Text(
              text = "${tempoBpm.toInt()} BPM",
              style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                color = NeonAmber,
                fontWeight = FontWeight.SemiBold
              )
            )
          }
        }
      }

      // Middle: Master Peak Meters
      Row(
        modifier = Modifier
          .height(28.dp)
          .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
      ) {
        val clampedL = peakL.coerceIn(0f, 1f)
        val clampedR = peakR.coerceIn(0f, 1f)
        Box(
          modifier = Modifier
            .width(4.dp)
            .height((28 * clampedL.coerceAtLeast(0.08f)).dp)
            .background(if (clampedL > 0.9f) NeonCoral else NeonCyan, RoundedCornerShape(1.dp))
        )
        Box(
          modifier = Modifier
            .width(4.dp)
            .height((28 * clampedR.coerceAtLeast(0.08f)).dp)
            .background(if (clampedR > 0.9f) NeonCoral else NeonCyan, RoundedCornerShape(1.dp))
        )
      }

      // Right: Capture, Record, Play Transport Controls
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Retrospective Capture Button
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF22252C))
            .border(1.dp, Color(0xFF383D46), RoundedCornerShape(6.dp))
            .clickable { onCaptureClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag("capture_button")
        ) {
          Text(
            text = "CAPTURE",
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              color = NeonAmber,
              fontSize = 11.sp
            )
          )
        }

        // Record Button
        IconButton(
          onClick = onRecordToggle,
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (isRecording) NeonCoral else Color(0xFF22252C))
            .testTag("record_button")
        ) {
          Icon(
            imageVector = Icons.Default.FiberManualRecord,
            contentDescription = "Record",
            tint = if (isRecording) Color.White else NeonCoral,
            modifier = Modifier.size(20.dp)
          )
        }

        // Play/Stop Button
        IconButton(
          onClick = onPlayToggle,
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (isPlaying) NeonCyan else Color(0xFF22252C))
            .testTag("play_button")
        ) {
          Icon(
            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Stop" else "Play",
            tint = if (isPlaying) Color.Black else NeonCyan,
            modifier = Modifier.size(22.dp)
          )
        }
      }
    }
  }
}
