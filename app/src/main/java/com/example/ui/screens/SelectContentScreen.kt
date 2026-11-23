package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Formatters
import com.example.model.InstalledAppInfo
import com.example.model.LocalMediaEntry
import com.example.model.ShareCategory
import com.example.model.ShareableItem
import com.example.ui.components.AppIcons
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberMagenta
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SelectContentScreen(
  selectedCategory: ShareCategory,
  onSelectCategory: (ShareCategory) -> Unit,
  installedApps: List<InstalledAppInfo>,
  appsLoading: Boolean,
  appSearchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  localMedia: List<LocalMediaEntry>,
  localMediaLoading: Boolean,
  hasPublicStorageAccess: Boolean,
  queuedItems: List<ShareableItem>,
  onToggleApp: (InstalledAppInfo) -> Unit,
  onToggleLocalMedia: (LocalMediaEntry) -> Unit,
  onRefreshLocalMedia: () -> Unit,
  onRequestPublicStorage: () -> Unit,
  onInstallLocalApk: (String) -> Unit,
  onAddMediaOrFiles: (List<Uri>, ShareCategory) -> Unit,
  onAddFolder: (Uri?) -> Unit,
  onRemoveQueueItem: (String) -> Unit,
  onClearQueue: () -> Unit,
  onProceedToSendRadar: () -> Unit,
) {
  val context = LocalContext.current

  // Removed the artificial maxItems = 50 limit; uses system unlimited max + SAF multi-select
  val videoPicker =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
      if (uris.isNotEmpty()) onAddMediaOrFiles(uris, ShareCategory.VIDEOS)
    }

  val photoPicker =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickMultipleVisualMedia()
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

  val safPhotoPicker =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) onAddMediaOrFiles(uris, ShareCategory.PHOTOS)
    }

  val queuedPackages = remember(queuedItems) { queuedItems.mapNotNull { it.packageName }.toHashSet() }
  val queuedPaths = remember(queuedItems) { queuedItems.mapNotNull { it.localFilePath }.toHashSet() }
  val totalQueueBytes = remember(queuedItems) { queuedItems.sumOf { it.sizeBytes } }

  var mediaSourceFilter by remember { mutableStateOf("All") }

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
                placeholder = { Text("Search ${installedApps.size} installed apps (no limit)...") },
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

              Surface(
                shape = RoundedCornerShape(12.dp),
                color = ElectricViolet.copy(alpha = 0.14f),
                modifier =
                  Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .border(1.dp, ElectricViolet.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
              ) {
                Text(
                  text =
                    "Includes Base APK + Split APKs + accessible OBB data. Note: Android OS protects private /data/data without root.",
                  style = MaterialTheme.typography.labelSmall,
                  color = CyberCyan,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
              }

              if (appsLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                  CircularProgressIndicator(color = CyberCyan)
                }
              } else {
                LazyColumn(
                  contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 104.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  items(filtered, key = { it.packageName }) { app ->
                    val isSelected = queuedPackages.contains(app.packageName)
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
            CategoryPickerWithMovieBoxList(
              title = "Videos & MovieBox Downloads",
              description =
                "Select unlimited videos, or pick directly from MovieBox, Downloads, and /storage/emulated/0/Ammarshare below.",
              primaryActionText = "Pick Videos (Unlimited)",
              primaryIcon = AppIcons.VideoLibrary,
              onPrimaryClick = {
                try {
                  videoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                  )
                } catch (_: Throwable) {
                  try {
                    safVideoPicker.launch(arrayOf("video/*"))
                  } catch (_: Throwable) {
                    Toast.makeText(context, "Unable to open video picker", Toast.LENGTH_SHORT).show()
                  }
                }
              },
              secondaryActionText = "Browse MovieBox / Storage (SAF Unlimited)",
              onSecondaryClick = {
                try {
                  safVideoPicker.launch(arrayOf("video/*", "application/octet-stream"))
                } catch (_: Throwable) {
                  Toast.makeText(context, "Unable to open file browser", Toast.LENGTH_SHORT).show()
                }
              },
              items = videoItems,
              localEntries = localMedia,
              localLoading = localMediaLoading,
              hasPublicStorageAccess = hasPublicStorageAccess,
              mediaSourceFilter = mediaSourceFilter,
              onSelectSourceFilter = { mediaSourceFilter = it },
              queuedPaths = queuedPaths,
              onToggleLocalMedia = onToggleLocalMedia,
              onRefreshLocalMedia = onRefreshLocalMedia,
              onRequestPublicStorage = onRequestPublicStorage,
              onInstallLocalApk = onInstallLocalApk,
              onRemoveItem = onRemoveQueueItem,
              primaryTestTag = "pick_videos_button",
            )
          }

          ShareCategory.PHOTOS -> {
            val photoItems = queuedItems.filter { it.category == ShareCategory.PHOTOS }
            CategoryPickerWithMovieBoxList(
              title = "Share Photos & Albums (Unlimited)",
              description =
                "Select high-resolution photos with zero limit via Android Photo Picker or SAF.",
              primaryActionText = "Pick Photos (Unlimited)",
              primaryIcon = AppIcons.PhotoLibrary,
              onPrimaryClick = {
                try {
                  photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                } catch (_: Throwable) {
                  try {
                    safPhotoPicker.launch(arrayOf("image/*"))
                  } catch (_: Throwable) {
                    Toast.makeText(context, "Unable to open photo picker", Toast.LENGTH_SHORT).show()
                  }
                }
              },
              secondaryActionText = "Browse Image Files (SAF Unlimited)",
              onSecondaryClick = {
                try {
                  safPhotoPicker.launch(arrayOf("image/*"))
                } catch (_: Throwable) {}
              },
              items = photoItems,
              localEntries = emptyList(),
              localLoading = false,
              hasPublicStorageAccess = hasPublicStorageAccess,
              mediaSourceFilter = mediaSourceFilter,
              onSelectSourceFilter = { mediaSourceFilter = it },
              queuedPaths = queuedPaths,
              onToggleLocalMedia = onToggleLocalMedia,
              onRefreshLocalMedia = onRefreshLocalMedia,
              onRequestPublicStorage = onRequestPublicStorage,
              onInstallLocalApk = onInstallLocalApk,
              onRemoveItem = onRemoveQueueItem,
              primaryTestTag = "pick_photos_button",
              showLocalSection = false,
            )
          }

          ShareCategory.FOLDERS -> {
            val folderItems = queuedItems.filter { it.category == ShareCategory.FOLDERS }
            CategoryPickerWithMovieBoxList(
              title = "Send Entire Folders (e.g. MovieBox / OBB)",
              description =
                "Pick any folder on your phone (no file count limit). Subfolders and nested files are preserved in /storage/emulated/0/Ammarshare.",
              primaryActionText = "Pick Folder (SAF Tree)",
              primaryIcon = AppIcons.CreateNewFolder,
              onPrimaryClick = {
                try {
                  folderPicker.launch(null)
                } catch (_: Throwable) {
                  Toast.makeText(context, "Unable to open folder picker", Toast.LENGTH_SHORT).show()
                }
              },
              secondaryActionText = null,
              onSecondaryClick = null,
              items = folderItems,
              localEntries = emptyList(),
              localLoading = false,
              hasPublicStorageAccess = hasPublicStorageAccess,
              mediaSourceFilter = mediaSourceFilter,
              onSelectSourceFilter = { mediaSourceFilter = it },
              queuedPaths = queuedPaths,
              onToggleLocalMedia = onToggleLocalMedia,
              onRefreshLocalMedia = onRefreshLocalMedia,
              onRequestPublicStorage = onRequestPublicStorage,
              onInstallLocalApk = onInstallLocalApk,
              onRemoveItem = onRemoveQueueItem,
              primaryTestTag = "pick_folder_button",
              showLocalSection = false,
            )
          }

          ShareCategory.FILES -> {
            val fileItems = queuedItems.filter { it.category == ShareCategory.FILES }
            CategoryPickerWithMovieBoxList(
              title = "Send APKs, ZIPs, MovieBox & Public Files",
              description =
                "Select unlimited files, or share/install items from MovieBox, Downloads, and /storage/emulated/0/Ammarshare below.",
              primaryActionText = "Pick Any Files (Unlimited)",
              primaryIcon = AppIcons.InsertDriveFile,
              onPrimaryClick = {
                try {
                  filePicker.launch(arrayOf("*/*"))
                } catch (_: Throwable) {
                  Toast.makeText(context, "Unable to open file picker", Toast.LENGTH_SHORT).show()
                }
              },
              secondaryActionText = null,
              onSecondaryClick = null,
              items = fileItems,
              localEntries = localMedia,
              localLoading = localMediaLoading,
              hasPublicStorageAccess = hasPublicStorageAccess,
              mediaSourceFilter = mediaSourceFilter,
              onSelectSourceFilter = { mediaSourceFilter = it },
              queuedPaths = queuedPaths,
              onToggleLocalMedia = onToggleLocalMedia,
              onRefreshLocalMedia = onRefreshLocalMedia,
              onRequestPublicStorage = onRequestPublicStorage,
              onInstallLocalApk = onInstallLocalApk,
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
  val iconBitmap by
    produceState<ImageBitmap?>(initialValue = null, app.packageName) {
      value =
        withContext(Dispatchers.Default) {
          try {
            val drawable = context.packageManager.getApplicationIcon(app.packageName)
            drawableToBitmap(drawable)?.asImageBitmap()
          } catch (_: Throwable) {
            null
          }
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
          bitmap = iconBitmap!!,
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
        val bundleLabel =
          buildString {
            append("v${app.versionName} • ${Formatters.formatBytes(app.totalBundleBytes)}")
            if (app.splitPaths.isNotEmpty()) append(" • +${app.splitPaths.size} splits")
            if (app.extraDataPaths.isNotEmpty()) append(" • +OBB")
          }
        Text(
          text = bundleLabel,
          style = MaterialTheme.typography.labelSmall,
          color =
            if (app.hasSplitsOrData) CyberCyan
            else MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
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
private fun CategoryPickerWithMovieBoxList(
  title: String,
  description: String,
  primaryActionText: String,
  primaryIcon: ImageVector,
  onPrimaryClick: () -> Unit,
  secondaryActionText: String?,
  onSecondaryClick: (() -> Unit)?,
  items: List<ShareableItem>,
  localEntries: List<LocalMediaEntry>,
  localLoading: Boolean,
  hasPublicStorageAccess: Boolean,
  mediaSourceFilter: String,
  onSelectSourceFilter: (String) -> Unit,
  queuedPaths: HashSet<String>,
  onToggleLocalMedia: (LocalMediaEntry) -> Unit,
  onRefreshLocalMedia: () -> Unit,
  onRequestPublicStorage: () -> Unit,
  onInstallLocalApk: (String) -> Unit,
  onRemoveItem: (String) -> Unit,
  primaryTestTag: String,
  showLocalSection: Boolean = true,
) {
  val filteredLocal =
    remember(localEntries, mediaSourceFilter) {
      if (mediaSourceFilter == "All") localEntries
      else localEntries.filter { it.sourceTag == mediaSourceFilter }
    }

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

    if (showLocalSection) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "MovieBox & Public Folders",
            style = MaterialTheme.typography.titleMedium,
            color = CyberCyan,
          )
          IconButton(onClick = onRefreshLocalMedia) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Rescan folders", tint = NeonViolet)
          }
        }
      }

      if (!hasPublicStorageAccess) {
        item {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = AmberWarning.copy(alpha = 0.12f),
            modifier =
              Modifier.fillMaxWidth()
                .border(1.dp, AmberWarning.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "Allow All Files Access to auto-list MovieBox & /storage/emulated/0/Ammarshare",
                style = MaterialTheme.typography.bodyMedium,
                color = AmberWarning,
              )
              Button(
                onClick = onRequestPublicStorage,
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = AmberWarning,
                    contentColor = ObsidianBg,
                  ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
              ) {
                Text("Unlock MovieBox & Public Storage", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          val filters = listOf("All", "MovieBox", "Ammarshare", "Downloads", "Movies")
          items(filters) { f ->
            FilterChip(
              selected = mediaSourceFilter == f,
              onClick = { onSelectSourceFilter(f) },
              label = { Text(f) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = ElectricViolet.copy(alpha = 0.28f),
                  selectedLabelColor = CyberCyan,
                ),
            )
          }
        }
      }

      if (localLoading) {
        item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(color = CyberCyan)
          }
        }
      } else if (filteredLocal.isEmpty()) {
        item {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = ObsidianSurface,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text =
                "No files found in $mediaSourceFilter yet. Download in MovieBox or receive files into /storage/emulated/0/Ammarshare, or use Browse SAF above.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      } else {
        items(filteredLocal, key = { it.id }) { entry ->
          val isQueued = queuedPaths.contains(entry.filePath)
          val isApk = entry.displayName.endsWith(".apk", ignoreCase = true)
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isQueued) ElectricViolet.copy(alpha = 0.2f) else ObsidianSurfaceHigh,
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(
                  1.dp,
                  if (isQueued) CyberCyan else ObsidianCardBorder.copy(alpha = 0.7f),
                  RoundedCornerShape(14.dp),
                )
                .clickable { onToggleLocalMedia(entry) }
                .minimumInteractiveComponentSize(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier.size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                      if (entry.sourceTag == "MovieBox") CyberMagenta.copy(alpha = 0.2f)
                      else CyberCyan.copy(alpha = 0.16f)
                    ),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector =
                    when {
                      entry.sourceTag == "MovieBox" -> AppIcons.Movie
                      isApk -> AppIcons.Apps
                      entry.category == ShareCategory.VIDEOS -> AppIcons.VideoLibrary
                      else -> AppIcons.InsertDriveFile
                    },
                  contentDescription = null,
                  tint = if (entry.sourceTag == "MovieBox") CyberMagenta else CyberCyan,
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = entry.displayName,
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${entry.sourceTag} • ${Formatters.formatBytes(entry.sizeBytes)}",
                  style = MaterialTheme.typography.labelSmall,
                  fontFamily = JetBrainsMonoFamily,
                  color =
                    if (entry.sourceTag == "Ammarshare") EmeraldSuccess
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                )
              }
              if (isApk) {
                TextButton(onClick = { onInstallLocalApk(entry.filePath) }) {
                  Text("Install", color = EmeraldSuccess, fontWeight = FontWeight.Bold)
                }
              }
              Icon(
                imageVector =
                  if (isQueued) Icons.Rounded.CheckCircle else AppIcons.RadioButtonUnchecked,
                contentDescription = if (isQueued) "Queued" else "Tap to queue",
                tint = if (isQueued) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap? {
  return try {
    if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
    val width = drawable.intrinsicWidth.coerceIn(48, 96)
    val height = drawable.intrinsicHeight.coerceIn(48, 96)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    bitmap
  } catch (_: Throwable) {
    null
  }
}
