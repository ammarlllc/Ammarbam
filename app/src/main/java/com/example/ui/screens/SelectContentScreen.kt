package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Formatters
import com.example.model.InstalledAppInfo
import com.example.model.ShareCategory
import com.example.model.ShareableItem
import com.example.ui.components.AppIcons
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberMagenta
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh

@Composable
fun SelectContentScreen(
  selectedCategory: ShareCategory,
  onSelectCategory: (ShareCategory) -> Unit,
  installedApps: List<InstalledAppInfo>,
  appsLoading: Boolean,
  appSearchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  queuedItems: List<ShareableItem>,
  onToggleApp: (InstalledAppInfo) -> Unit,
  onAddMediaOrFiles: (List<Uri>, ShareCategory) -> Unit,
  onAddFolder: (Uri?) -> Unit,
  onRemoveQueueItem: (String) -> Unit,
  onClearQueue: () -> Unit,
  onProceedToSendRadar: () -> Unit,
) {
  val videoPicker =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
      if (uris.isNotEmpty()) onAddMediaOrFiles(uris, ShareCategory.VIDEOS)
    }

  val photoPicker =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
      if (uris.isNotEmpty()) onAddMediaOrFiles(uris, ShareCategory.PHOTOS)
    }

  val folderPicker =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree()) { uri ->
      onAddFolder(uri)
    }

  val filePicker =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) onAddMediaOrFiles(uris, ShareCategory.FILES)
    }

  val safVideoPicker =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) onAddMediaOrFiles(uris, ShareCategory.VIDEOS)
    }

  val queuedIds = remember(queuedItems) { queuedItems.map { it.id }.toHashSet() }
  val totalQueueBytes = remember(queuedItems) { queuedItems.sumOf { it.sizeBytes } }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.fillMaxSize().widthIn(max = 680.dp)) {
      // Category Tabs
      ScrollableTabRow(
        selectedTabIndex = ShareCategory.entries.indexOf(selectedCategory),
        containerColor = ObsidianBg,
        contentColor = CyberCyan,
        edgePadding = 12.dp,
      ) {
        ShareCategory.entries.forEach { cat ->
          val count = queuedItems.count { it.category == cat }
          Tab(
            selected = cat == selectedCategory,
            onClick = { onSelectCategory(cat) },
            modifier = Modifier.testTag("tab_${cat.name.lowercase()}"),
            text = {
              Text(
                text = if (count > 0) "${cat.label} ($count)" else cat.label,
                fontWeight = if (cat == selectedCategory) FontWeight.Bold else FontWeight.Medium,
              )
            },
          )
        }
      }

      // Main Content Area
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when (selectedCategory) {
          ShareCategory.APPS -> {
            val filtered =
              remember(installedApps, appSearchQuery) {
                if (appSearchQuery.isBlank()) installedApps
                else {
                  val q = appSearchQuery.trim().lowercase()
                  installedApps.filter {
                    it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
                  }
                }
              }

            Column(modifier = Modifier.fillMaxSize()) {
              OutlinedTextField(
                value = appSearchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                placeholder = { Text("Search ${installedApps.size} installed apps...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                  if (appSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                      Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                  }
                },
                shape = RoundedCornerShape(16.dp),
                modifier =
                  Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("app_search_input"),
              )

              if (appsLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator(color = CyberCyan)
                }
              } else {
                LazyColumn(
                  contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  items(filtered, key = { it.packageName }) { app ->
                    val isSelected = queuedIds.contains("app_${app.packageName}")
                    InstalledAppRow(
                      app = app,
                      selected = isSelected,
                      onClick = { onToggleApp(app) },
                    )
                  }
                }
              }
            }
          }

          ShareCategory.VIDEOS -> {
            val videoItems = queuedItems.filter { it.category == ShareCategory.VIDEOS }
            CategoryPickerList(
              title = "Share Videos at Full Quality",
              description =
                "Select one or more videos using Android Photo Picker or Browse Files (no storage permissions needed).",
              primaryActionText = "Pick Videos (Photo Picker)",
              primaryIcon = AppIcons.VideoLibrary,
              onPrimaryClick = {
                videoPicker.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
              },
              secondaryActionText = "Browse Video Files (SAF)",
              onSecondaryClick = { safVideoPicker.launch(arrayOf("video/*")) },
              items = videoItems,
              onRemoveItem = onRemoveQueueItem,
              primaryTestTag = "pick_videos_button",
            )
          }

          ShareCategory.PHOTOS -> {
            val photoItems = queuedItems.filter { it.category == ShareCategory.PHOTOS }
            CategoryPickerList(
              title = "Share Photos & Albums",
              description =
                "Select high-resolution photos with zero-permission Android Photo Picker.",
              primaryActionText = "Pick Photos (Photo Picker)",
              primaryIcon = AppIcons.PhotoLibrary,
              onPrimaryClick = {
                photoPicker.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              secondaryActionText = null,
              onSecondaryClick = null,
              items = photoItems,
              onRemoveItem = onRemoveQueueItem,
              primaryTestTag = "pick_photos_button",
            )
          }

          ShareCategory.FOLDERS -> {
            val folderItems = queuedItems.filter { it.category == ShareCategory.FOLDERS }
            CategoryPickerList(
              title = "Send Entire Folders",
              description =
                "Pick any folder on your phone. Subfolders and all nested files are preserved on the receiving phone.",
              primaryActionText = "Pick Folder (SAF Tree)",
              primaryIcon = AppIcons.CreateNewFolder,
              onPrimaryClick = { folderPicker.launch(null) },
              secondaryActionText = null,
              onSecondaryClick = null,
              items = folderItems,
              onRemoveItem = onRemoveQueueItem,
              primaryTestTag = "pick_folder_button",
            )
          }

          ShareCategory.FILES -> {
            val fileItems = queuedItems.filter { it.category == ShareCategory.FILES }
            CategoryPickerList(
              title = "Send APKs, ZIPs, Audio & Docs",
              description =
                "Select any document, archive, APK, or music file from device storage or Drive.",
              primaryActionText = "Pick Any Files",
              primaryIcon = AppIcons.InsertDriveFile,
              onPrimaryClick = { filePicker.launch(arrayOf("*/*")) },
              secondaryActionText = null,
              onSecondaryClick = null,
              items = fileItems,
              onRemoveItem = onRemoveQueueItem,
              primaryTestTag = "pick_files_button",
            )
          }
        }
      }
    }

    // Floating Send Queue Bar
    if (queuedItems.isNotEmpty()) {
      Surface(
        shape = RoundedCornerShape(22.dp),
        color = ObsidianSurfaceHigh,
        tonalElevation = 10.dp,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .border(
              1.5.dp,
              Brush.horizontalGradient(listOf(ElectricViolet, CyberCyan)),
              RoundedCornerShape(22.dp),
            ),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "${queuedItems.size} item(s) ready",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = Formatters.formatBytes(totalQueueBytes),
              style = MaterialTheme.typography.labelSmall,
              color = CyberCyan,
            )
          }

          TextButton(
            onClick = onClearQueue,
            modifier = Modifier.testTag("clear_queue_button"),
          ) {
            Text("Clear", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }

          Spacer(modifier = Modifier.width(6.dp))

          Button(
            onClick = onProceedToSendRadar,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = ElectricViolet,
                contentColor = Color.White,
              ),
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("send_now_queue_button"),
          ) {
            Text("SEND")
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Send,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun InstalledAppRow(
  app: InstalledAppInfo,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val context = LocalContext.current
  val iconBitmap =
    remember(app.packageName) {
      try {
        val drawable = context.packageManager.getApplicationIcon(app.packageName)
        drawableToBitmap(drawable)?.asImageBitmap()
      } catch (_: Exception) {
        null
      }
    }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = if (selected) ElectricViolet.copy(alpha = 0.2f) else ObsidianSurfaceHigh,
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .border(
          width = if (selected) 1.5.dp else 1.dp,
          color = if (selected) CyberCyan else ObsidianCardBorder.copy(alpha = 0.7f),
          shape = RoundedCornerShape(16.dp),
        )
        .clickable(onClick = onClick)
        .minimumInteractiveComponentSize()
        .testTag("app_row_${app.packageName}"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (iconBitmap != null) {
        Image(
          bitmap = iconBitmap,
          contentDescription = app.appName,
          modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
        )
      } else {
        Box(
          modifier =
            Modifier.size(44.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(ElectricViolet.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(AppIcons.Apps, contentDescription = app.appName, tint = NeonViolet)
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = app.appName,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "v${app.versionName} • ${Formatters.formatBytes(app.sizeBytes)}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
      }

      Icon(
        imageVector =
          if (selected) Icons.Rounded.CheckCircle else AppIcons.RadioButtonUnchecked,
        contentDescription = if (selected) "Selected" else "Not selected",
        tint = if (selected) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun CategoryPickerList(
  title: String,
  description: String,
  primaryActionText: String,
  primaryIcon: ImageVector,
  onPrimaryClick: () -> Unit,
  secondaryActionText: String?,
  onSecondaryClick: (() -> Unit)?,
  items: List<ShareableItem>,
  onRemoveItem: (String) -> Unit,
  primaryTestTag: String,
) {
  LazyColumn(
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Surface(
        shape = RoundedCornerShape(22.dp),
        color = ObsidianSurfaceHigh,
        modifier =
          Modifier.fillMaxWidth()
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(22.dp)),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Button(
            onClick = onPrimaryClick,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = ElectricViolet,
                contentColor = Color.White,
              ),
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag(primaryTestTag),
          ) {
            Icon(primaryIcon, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(primaryActionText)
          }

          if (secondaryActionText != null && onSecondaryClick != null) {
            OutlinedButton(
              onClick = onSecondaryClick,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
            ) {
              Text(secondaryActionText, color = CyberCyan)
            }
          }
        }
      }
    }

    if (items.isNotEmpty()) {
      item {
        Text(
          text = "Queued in this category (${items.size})",
          style = MaterialTheme.typography.titleMedium,
          color = CyberCyan,
        )
      }
      items(items, key = { it.id }) { item ->
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = ObsidianSurface,
          modifier =
            Modifier.fillMaxWidth()
              .border(1.dp, ObsidianCardBorder.copy(alpha = 0.7f), RoundedCornerShape(14.dp)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier.size(38.dp)
                  .clip(CircleShape)
                  .background(CyberMagenta.copy(alpha = 0.16f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector =
                  if (item.category == ShareCategory.FOLDERS) AppIcons.Folder
                  else primaryIcon,
                contentDescription = null,
                tint = NeonViolet,
                modifier = Modifier.size(20.dp),
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text =
                  if (item.relativePath != item.displayName)
                    "${item.relativePath} • ${Formatters.formatBytes(item.sizeBytes)}"
                  else Formatters.formatBytes(item.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            IconButton(onClick = { onRemoveItem(item.id) }) {
              Icon(
                imageVector = AppIcons.DeleteOutline,
                contentDescription = "Remove ${item.displayName}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap? {
  if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
  val width = drawable.intrinsicWidth.coerceIn(48, 144)
  val height = drawable.intrinsicHeight.coerceIn(48, 144)
  val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)
  drawable.setBounds(0, 0, canvas.width, canvas.height)
  drawable.draw(canvas)
  return bitmap
}
