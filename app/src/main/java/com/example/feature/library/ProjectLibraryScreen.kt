package com.example.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.core.model.Project
import com.example.ui.theme.CharcoalBackground
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectLibraryScreen(
  projects: List<Project>,
  onOpenProject: (Project) -> Unit,
  onCreateProject: (name: String) -> Unit,
  onDuplicateProject: (id: String) -> Unit,
  onDeleteProject: (id: String) -> Unit,
  onExportProject: (Project) -> Unit,
  modifier: Modifier = Modifier
) {
  var showNewDialog by remember { mutableStateOf(false) }
  var newProjectName by remember { mutableStateOf("") }
  var projectToDelete by remember { mutableStateOf<Project?>(null) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = CharcoalBackground,
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          newProjectName = "Session ${projects.size + 1}"
          showNewDialog = true
        },
        containerColor = NeonCyan,
        contentColor = Color.Black,
        shape = CircleShape,
        modifier = Modifier.testTag("new_project_fab")
      ) {
        Icon(Icons.Default.Add, contentDescription = "New Session", modifier = Modifier.size(28.dp))
      }
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "POCKET SESSION",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 26.sp),
            color = TextPrimary
          )
          Text(
            text = "SKETCHES & WORKSTATION",
            style = MaterialTheme.typography.labelSmall.copy(
              letterSpacing = 1.2.sp,
              fontWeight = FontWeight.Bold
            ),
            color = NeonAmber
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Projects List
      if (projects.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 64.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              Icons.Default.MusicNote,
              contentDescription = null,
              tint = TextMuted,
              modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              "No sessions yet",
              style = MaterialTheme.typography.titleMedium,
              color = TextSecondary
            )
            Text(
              "Tap + to start a new sketch",
              style = MaterialTheme.typography.bodyMedium,
              color = TextMuted
            )
          }
        }
      } else {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(bottom = 80.dp)
        ) {
          items(projects, key = { it.id }) { project ->
            ProjectCard(
              project = project,
              onOpen = { onOpenProject(project) },
              onDuplicate = { onDuplicateProject(project.id) },
              onDelete = { projectToDelete = project },
              onExport = { onExportProject(project) }
            )
          }
        }
      }
    }
  }

  // New Project Dialog
  if (showNewDialog) {
    AlertDialog(
      onDismissRequest = { showNewDialog = false },
      title = { Text("New Session") },
      text = {
        OutlinedTextField(
          value = newProjectName,
          onValueChange = { newProjectName = it },
          label = { Text("Session Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("project_name_input")
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            if (newProjectName.isNotBlank()) {
              onCreateProject(newProjectName.trim())
              showNewDialog = false
            }
          },
          modifier = Modifier.testTag("confirm_create_project")
        ) {
          Text("Create", color = NeonCyan)
        }
      },
      dismissButton = {
        TextButton(onClick = { showNewDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      },
      containerColor = CharcoalSurface
    )
  }

  // Delete Confirmation Dialog
  projectToDelete?.let { proj ->
    AlertDialog(
      onDismissRequest = { projectToDelete = null },
      title = { Text("Delete Session?") },
      text = { Text("Are you sure you want to delete '${proj.name}'? This cannot be undone.") },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteProject(proj.id)
            projectToDelete = null
          },
          modifier = Modifier.testTag("confirm_delete_project")
        ) {
          Text("Delete", color = Color(0xFFFF5252))
        }
      },
      dismissButton = {
        TextButton(onClick = { projectToDelete = null }) {
          Text("Keep", color = TextSecondary)
        }
      },
      containerColor = CharcoalSurface
    )
  }
}

@Composable
fun ProjectCard(
  project: Project,
  onOpen: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
  onExport: () -> Unit,
  modifier: Modifier = Modifier
) {
  val dateFormatted = remember(project.modifiedAt) {
    SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(project.modifiedAt))
  }

  val activeClipCount = remember(project) {
    project.tracks.sumOf { track -> track.clips.count { it != null && it.hasNotes } }
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(CharcoalSurface)
      .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
      .clickable { onOpen() }
      .padding(16.dp)
      .testTag("project_card_${project.name}")
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = project.name,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = dateFormatted,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
          )
        }

        // Action Buttons
        Row {
          IconButton(onClick = onExport, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Share, contentDescription = "Export Live Transfer", tint = NeonCyan, modifier = Modifier.size(18.dp))
          }
          IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = TextSecondary, modifier = Modifier.size(18.dp))
          }
          IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Badges: BPM, Key, Active Clips, 8 Tracks
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Badge("${project.tempoBpm.toInt()} BPM", NeonAmber)
        Badge("${project.rootNote.displayName} ${project.scaleType.displayName}", NeonCyan)
        Badge("$activeClipCount clips", Color(0xFF00E676))
        Badge("8 Tracks", TextSecondary)
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Mini 8-track grid visual preview
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        project.tracks.forEach { track ->
          val hasNotes = track.clips.any { it != null && it.hasNotes }
          Box(
            modifier = Modifier
              .weight(1f)
              .height(6.dp)
              .clip(RoundedCornerShape(3.dp))
              .background(if (hasNotes) Color(track.color) else Color(0xFF262A30))
          )
        }
      }
    }
  }
}

@Composable
private fun Badge(text: String, color: Color) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(color.copy(alpha = 0.15f))
      .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
      .padding(horizontal = 6.dp, vertical = 2.dp)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall.copy(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        color = color
      )
    )
  }
}
