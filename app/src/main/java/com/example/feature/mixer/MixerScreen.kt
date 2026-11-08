package com.example.feature.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.Project
import com.example.core.model.Track
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
fun MixerScreen(
  project: Project,
  trackPeaks: FloatArray,
  masterPeakL: Float,
  masterPeakR: Float,
  onTrackVolumeChanged: (trackIndex: Int, volumeDb: Float) -> Unit,
  onTrackPanChanged: (trackIndex: Int, pan: Float) -> Unit,
  onTrackMuteToggle: (trackIndex: Int) -> Unit,
  onTrackSoloToggle: (trackIndex: Int) -> Unit,
  onMasterVolumeChanged: (volume: Float) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val horizontalScroll = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(CharcoalBackground)
  ) {
    // Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF181B20))
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(onClick = onBack, modifier = Modifier.size(36.dp).testTag("mixer_back")) {
        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecondary, modifier = Modifier.size(20.dp))
      }
      Spacer(modifier = Modifier.width(8.dp))
      Column {
        Text(
          text = "TRACK MIXER",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = TextPrimary
        )
        Text(
          text = "8 Channels + Master Bus",
          style = MaterialTheme.typography.labelSmall,
          color = NeonCyan
        )
      }
    }

    // Mixer Channels Row: 8 Channels + Master Channel on right
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      // 8 Scrollable Channels
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .horizontalScroll(horizontalScroll)
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          project.tracks.forEach { track ->
            val peak = trackPeaks.getOrElse(track.index) { 0f }
            MixerChannelStrip(
              track = track,
              peakLevel = peak,
              onVolumeChanged = { onTrackVolumeChanged(track.index, it) },
              onPanChanged = { onTrackPanChanged(track.index, it) },
              onMuteToggle = { onTrackMuteToggle(track.index) },
              onSoloToggle = { onTrackSoloToggle(track.index) }
            )
          }
        }
      }

      // Master Channel Strip (Fixed on Right)
      MasterChannelStrip(
        masterVolume = project.masterVolume,
        peakL = masterPeakL,
        peakR = masterPeakR,
        onVolumeChanged = onMasterVolumeChanged,
        modifier = Modifier
          .width(88.dp)
          .fillMaxHeight()
          .background(Color(0xFF14161A))
          .border(0.5.dp, CharcoalBorder)
          .padding(8.dp)
      )
    }
  }
}

@Composable
fun MixerChannelStrip(
  track: Track,
  peakLevel: Float,
  onVolumeChanged: (Float) -> Unit,
  onPanChanged: (Float) -> Unit,
  onMuteToggle: () -> Unit,
  onSoloToggle: () -> Unit,
  modifier: Modifier = Modifier
) {
  val trackColor = Color(track.color)

  Column(
    modifier = modifier
      .width(76.dp)
      .fillMaxHeight()
      .clip(RoundedCornerShape(8.dp))
      .background(CharcoalSurface)
      .border(1.dp, CharcoalBorder, RoundedCornerShape(8.dp))
      .padding(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    // Channel Header
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Box(
        modifier = Modifier
          .size(10.dp)
          .clip(CircleShape)
          .background(trackColor)
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = track.name,
        style = MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 10.sp
        ),
        color = TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
      )
    }

    // Pan Slider
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = if (track.pan == 0f) "C" else if (track.pan < 0f) "L${(-track.pan * 50).toInt()}" else "R${(track.pan * 50).toInt()}",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontFamily = FontFamily.Monospace),
        color = TextMuted
      )
      Slider(
        value = track.pan,
        onValueChange = onPanChanged,
        valueRange = -1f..1f,
        colors = SliderDefaults.colors(thumbColor = NeonAmber, activeTrackColor = NeonAmber),
        modifier = Modifier.height(20.dp)
      )
    }

    // Volume Slider + Peak Level Meter
    Row(
      modifier = Modifier
        .weight(1f)
        .padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Gain Slider (-36 dB to +6 dB)
      Slider(
        value = track.volumeDb,
        onValueChange = onVolumeChanged,
        valueRange = -36f..6f,
        colors = SliderDefaults.colors(thumbColor = trackColor, activeTrackColor = trackColor),
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
      )

      // Peak Level Meter
      Box(
        modifier = Modifier
          .width(6.dp)
          .fillMaxHeight()
          .clip(RoundedCornerShape(2.dp))
          .background(Color(0xFF22262C)),
        contentAlignment = Alignment.BottomCenter
      ) {
        val meterHeight = (peakLevel.coerceIn(0f, 1f) * 100).toInt()
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(meterHeight.dp)
            .background(if (peakLevel > 0.9f) NeonCoral else NeonCyan)
        )
      }
    }

    // dB Reading
    Text(
      text = String.format("%.1f dB", track.volumeDb),
      style = MaterialTheme.typography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp
      ),
      color = TextSecondary
    )

    Spacer(modifier = Modifier.height(6.dp))

    // Mute / Solo Buttons
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      // Mute
      Box(
        modifier = Modifier
          .weight(1f)
          .height(28.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(if (track.mute) NeonCoral else CharcoalSurfaceVariant)
          .clickable { onMuteToggle() }
          .testTag("mute_${track.index}"),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "M",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = if (track.mute) Color.White else TextMuted
        )
      }

      // Solo
      Box(
        modifier = Modifier
          .weight(1f)
          .height(28.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(if (track.solo) NeonAmber else CharcoalSurfaceVariant)
          .clickable { onSoloToggle() }
          .testTag("solo_${track.index}"),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "S",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = if (track.solo) Color.Black else TextMuted
        )
      }
    }
  }
}

@Composable
fun MasterChannelStrip(
  masterVolume: Float,
  peakL: Float,
  peakR: Float,
  onVolumeChanged: (Float) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = "MASTER",
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
      color = NeonCyan
    )

    // Master Stereo Meters + Master Volume Fader
    Row(
      modifier = Modifier
        .weight(1f)
        .padding(vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Slider(
        value = masterVolume,
        onValueChange = onVolumeChanged,
        valueRange = 0f..1.2f,
        colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan),
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
      )

      // Stereo Peak Meters (L + R)
      Row(
        modifier = Modifier
          .width(10.dp)
          .fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(Color(0xFF22262C)),
          contentAlignment = Alignment.BottomCenter
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height((peakL.coerceIn(0f, 1f) * 120).dp)
              .background(if (peakL > 0.95f) NeonCoral else NeonCyan)
          )
        }
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(Color(0xFF22262C)),
          contentAlignment = Alignment.BottomCenter
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height((peakR.coerceIn(0f, 1f) * 120).dp)
              .background(if (peakR > 0.95f) NeonCoral else NeonCyan)
          )
        }
      }
    }

    Text(
      text = "${(masterVolume * 100).toInt()}%",
      style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
      color = TextPrimary
    )
  }
}
