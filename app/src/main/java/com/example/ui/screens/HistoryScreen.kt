package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Formatters
import com.example.model.ShareCategory
import com.example.model.TransferDirection
import com.example.model.TransferRecord
import com.example.ui.components.AppIcons
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh

enum class HistoryFilter(val label: String) {
  ALL("All"),
  RECEIVED("Received"),
  SENT("Sent"),
}

@Composable
fun HistoryScreen(
  records: List<TransferRecord>,
  onOpenRecord: (TransferRecord) -> Unit,
  onShareRecord: (TransferRecord) -> Unit,
  onClearHistory: () -> Unit,
) {
  var filter by remember { mutableStateOf(HistoryFilter.ALL) }

  val filtered =
    remember(records, filter) {
      when (filter) {
        HistoryFilter.ALL -> records
        HistoryFilter.RECEIVED -> records.filter { it.direction == TransferDirection.RECEIVE }
        HistoryFilter.SENT -> records.filter { it.direction == TransferDirection.SEND }
      }
    }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.fillMaxSize().widthIn(max = 680.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          HistoryFilter.entries.forEach { f ->
            FilterChip(
              selected = filter == f,
              onClick = { filter = f },
              label = { Text(f.label) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = ElectricViolet.copy(alpha = 0.28f),
                  selectedLabelColor = CyberCyan,
                ),
              modifier = Modifier.testTag("history_filter_${f.name.lowercase()}"),
            )
          }
        }

        if (records.isNotEmpty()) {
          TextButton(
            onClick = onClearHistory,
            modifier = Modifier.testTag("clear_history_button"),
          ) {
            Icon(AppIcons.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Clear")
          }
        }
      }

      if (filtered.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize().padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
              modifier =
                Modifier.size(72.dp)
                  .clip(CircleShape)
                  .background(ElectricViolet.copy(alpha = 0.16f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = AppIcons.History,
                contentDescription = null,
                tint = NeonViolet,
                modifier = Modifier.size(34.dp),
              )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
              text = "No transfers yet",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text =
                "Received apps, videos, and folders are saved directly to Downloads/AmmarShare on your device.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(filtered, key = { it.id }) { record ->
            TransferRecordRow(
              record = record,
              onOpen = { onOpenRecord(record) },
              onShare = { onShareRecord(record) },
              compact = false,
            )
          }
        }
      }
    }
  }
}

@Composable
fun TransferRecordRow(
  record: TransferRecord,
  onOpen: () -> Unit,
  onShare: () -> Unit,
  compact: Boolean,
) {
  val catIcon =
    when (record.category) {
      ShareCategory.APPS -> AppIcons.Apps
      ShareCategory.VIDEOS -> AppIcons.VideoLibrary
      ShareCategory.PHOTOS -> AppIcons.PhotoLibrary
      ShareCategory.FOLDERS -> AppIcons.Folder
      ShareCategory.FILES -> AppIcons.InsertDriveFile
    }
  val isReceive = record.direction == TransferDirection.RECEIVE

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = if (compact) ObsidianSurface else ObsidianSurfaceHigh,
    modifier =
      Modifier.fillMaxWidth()
        .border(1.dp, ObsidianCardBorder.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
              if (isReceive) CyberCyan.copy(alpha = 0.16f)
              else ElectricViolet.copy(alpha = 0.20f)
            ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = catIcon,
          contentDescription = null,
          tint = if (isReceive) CyberCyan else NeonViolet,
          modifier = Modifier.size(22.dp),
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = record.fileName,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = if (isReceive) AppIcons.Download else AppIcons.Upload,
            contentDescription = null,
            tint = if (isReceive) EmeraldSuccess else NeonViolet,
            modifier = Modifier.size(13.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text =
              "${Formatters.formatBytes(record.sizeBytes)} • ${Formatters.formatSpeed(record.speedMBps)} • ${record.peerName}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      if (!compact && isReceive) {
        IconButton(onClick = onOpen) {
          Icon(
            imageVector = AppIcons.OpenInNew,
            contentDescription = "Open ${record.fileName}",
            tint = CyberCyan,
          )
        }
        IconButton(onClick = onShare) {
          Icon(
            imageVector = Icons.Rounded.Share,
            contentDescription = "Share ${record.fileName}",
            tint = NeonViolet,
          )
        }
      }
    }
  }
}
