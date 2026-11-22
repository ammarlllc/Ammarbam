package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BenchmarkResult
import com.example.model.Formatters
import com.example.model.ShareCategory
import com.example.model.ShareableItem
import com.example.model.TransferRecord
import com.example.ui.components.AppIcons
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberMagenta
import com.example.ui.theme.DeepCyan
import com.example.ui.theme.DeepRoyalPurple
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.ObsidianSurfaceHighest

@Composable
fun TransferHubScreen(
  deviceName: String,
  turboMode: Boolean,
  queuedItems: List<ShareableItem>,
  recentHistory: List<TransferRecord>,
  benchmarkResult: BenchmarkResult?,
  isBenchmarkRunning: Boolean,
  onRenameDevice: (String) -> Unit,
  onToggleTurbo: (Boolean) -> Unit,
  onOpenSendFlow: () -> Unit,
  onOpenReceiveFlow: () -> Unit,
  onOpenCategory: (ShareCategory) -> Unit,
  onRunBenchmark: () -> Unit,
  onOpenHistoryTab: () -> Unit,
) {
  var showRenameDialog by remember { mutableStateOf(false) }

  if (showRenameDialog) {
    var draft by remember(deviceName) { mutableStateOf(deviceName) }
    AlertDialog(
      onDismissRequest = { showRenameDialog = false },
      title = { Text("Device Broadcast Name") },
      text = {
        OutlinedTextField(
          value = draft,
          onValueChange = { draft = it },
          singleLine = true,
          label = { Text("Visible to nearby phones") },
          modifier = Modifier.fillMaxWidth().testTag("device_name_input"),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onRenameDevice(draft)
            showRenameDialog = false
          },
          modifier = Modifier.testTag("save_device_name_button"),
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
      },
    )
  }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().widthIn(max = 680.dp),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Top Brand & Device Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Image(
              painter = painterResource(id = R.drawable.ammar_brand),
              contentDescription = stringResource(R.string.cd_ammar_brand_logo),
              contentScale = ContentScale.Crop,
              modifier =
                Modifier.size(48.dp)
                  .clip(CircleShape)
                  .border(2.dp, ElectricViolet, CircleShape),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "AMMAR SHARE",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                  Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { showRenameDialog = true }
                    .padding(vertical = 2.dp)
                    .testTag("rename_device_chip"),
              ) {
                Text(
                  text = deviceName,
                  style = MaterialTheme.typography.bodyMedium,
                  color = CyberCyan,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                  imageVector = Icons.Rounded.Edit,
                  contentDescription = "Edit device name",
                  tint = CyberCyan,
                  modifier = Modifier.size(14.dp),
                )
              }
            }
          }

          Surface(
            shape = RoundedCornerShape(50),
            color = ObsidianSurfaceHigh,
            modifier = Modifier.border(1.dp, ObsidianCardBorder, RoundedCornerShape(50)),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = AppIcons.Bolt,
                contentDescription = null,
                tint = if (turboMode) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (turboMode) "6x TURBO" else "1x STD",
                style = MaterialTheme.typography.labelSmall,
                color = if (turboMode) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      // Hero SEND & RECEIVE Cards
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          HeroTransferActionCard(
            title = stringResource(R.string.action_send),
            subtitle =
              if (queuedItems.isNotEmpty()) "${queuedItems.size} queued • Tap to send"
              else stringResource(R.string.send_subtitle),
            icon = AppIcons.Upload,
            gradientColors = listOf(ElectricViolet, CyberMagenta),
            accentColor = Color.White,
            modifier = Modifier.weight(1f).testTag("home_send_button"),
            onClick = onOpenSendFlow,
          )

          HeroTransferActionCard(
            title = stringResource(R.string.action_receive),
            subtitle = stringResource(R.string.receive_subtitle),
            icon = AppIcons.Download,
            gradientColors = listOf(DeepCyan, DeepRoyalPurple),
            accentColor = CyberCyan,
            modifier = Modifier.weight(1f).testTag("home_receive_button"),
            onClick = onOpenReceiveFlow,
          )
        }
      }

      // Quick Share Categories Strip
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Quick Select & Share",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ShareCategory.entries) { category ->
              val icon =
                when (category) {
                  ShareCategory.APPS -> AppIcons.Apps
                  ShareCategory.VIDEOS -> AppIcons.VideoLibrary
                  ShareCategory.PHOTOS -> AppIcons.PhotoLibrary
                  ShareCategory.FOLDERS -> AppIcons.Folder
                  ShareCategory.FILES -> AppIcons.InsertDriveFile
                }
              CategoryQuickCard(
                label = category.label,
                icon = icon,
                onClick = { onOpenCategory(category) },
                modifier = Modifier.testTag("quick_cat_${category.name.lowercase()}"),
              )
            }
          }
        }
      }

      // Turbo Multi-Stream Socket Engine & Benchmark Card
      item {
        Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceHigh),
          modifier =
            Modifier.fillMaxWidth()
              .border(1.dp, ObsidianCardBorder, RoundedCornerShape(22.dp)),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                  modifier =
                    Modifier.size(42.dp)
                      .clip(RoundedCornerShape(12.dp))
                      .background(ElectricViolet.copy(alpha = 0.2f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = AppIcons.Speed,
                    contentDescription = null,
                    tint = CyberCyan,
                  )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = stringResource(R.string.turbo_engine_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = stringResource(R.string.turbo_engine_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Switch(
                checked = turboMode,
                onCheckedChange = onToggleTurbo,
                colors =
                  SwitchDefaults.colors(
                    checkedThumbColor = ObsidianSurface,
                    checkedTrackColor = CyberCyan,
                  ),
                modifier = Modifier.testTag("turbo_mode_switch"),
              )
            }

            if (benchmarkResult != null) {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianSurface,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column {
                    Text(
                      text = "Verified TCP Socket Speed",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                      text = Formatters.formatSpeed(benchmarkResult.throughputMBps),
                      style = MaterialTheme.typography.headlineMedium,
                      fontFamily = JetBrainsMonoFamily,
                      color = EmeraldSuccess,
                    )
                  }
                  Column(horizontalAlignment = Alignment.End) {
                    Text(
                      text = "Peak ${Formatters.formatSpeed(benchmarkResult.peakMBps)}",
                      style = MaterialTheme.typography.labelSmall,
                      color = CyberCyan,
                    )
                    Text(
                      text = "${benchmarkResult.payloadMegabytes} MB in ${benchmarkResult.durationMs} ms",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }

            Button(
              onClick = onRunBenchmark,
              enabled = !isBenchmarkRunning,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = ObsidianSurfaceHighest,
                  contentColor = CyberCyan,
                ),
              shape = RoundedCornerShape(14.dp),
              modifier =
                Modifier.fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .testTag("run_benchmark_button"),
            ) {
              if (isBenchmarkRunning) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                  color = CyberCyan,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Measuring 6-Stream TCP Speed...")
              } else {
                Icon(AppIcons.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Run 6-Stream TCP Speed Test")
              }
            }
          }
        }
      }

      // Recent Transfers Preview
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Recent Transfers",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          TextButton(
            onClick = onOpenHistoryTab,
            modifier = Modifier.testTag("view_all_history_button"),
          ) {
            Text("View All (${recentHistory.size})")
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }

      if (recentHistory.isEmpty()) {
        item {
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = ObsidianSurface,
            modifier =
              Modifier.fillMaxWidth()
                .border(1.dp, ObsidianCardBorder.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
          ) {
            Column(
              modifier = Modifier.padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = "Ready for your first high-speed transfer",
                style = MaterialTheme.typography.titleMedium,
                color = NeonViolet,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Tap SEND to pick Apps, Videos, or Folders, or tap RECEIVE on the other phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(recentHistory.take(4), key = { it.id }) { record ->
          TransferRecordRow(record = record, onOpen = {}, onShare = {}, compact = true)
        }
      }
    }
  }
}

@Composable
private fun HeroTransferActionCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  gradientColors: List<Color>,
  accentColor: Color,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      modifier
        .height(164.dp)
        .clip(RoundedCornerShape(26.dp))
        .background(Brush.linearGradient(gradientColors))
        .clickable(onClick = onClick)
        .padding(18.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Box(
        modifier =
          Modifier.size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.24f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = accentColor,
          modifier = Modifier.size(28.dp),
        )
      }

      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.headlineLarge,
          fontWeight = FontWeight.ExtraBold,
          color = Color.White,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = Color.White.copy(alpha = 0.88f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun CategoryQuickCard(
  label: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = ObsidianSurfaceHigh,
    modifier =
      modifier
        .width(96.dp)
        .clip(RoundedCornerShape(18.dp))
        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(18.dp))
        .clickable(onClick = onClick)
        .minimumInteractiveComponentSize(),
  ) {
    Column(
      modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(CircleShape)
            .background(ElectricViolet.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(imageVector = icon, contentDescription = label, tint = NeonViolet)
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
    }
  }
}
