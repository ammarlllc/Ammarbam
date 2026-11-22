package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight 24dp Material vector icons used by Ammar Share without bundling the 12MB+
 * material-icons-extended artifact.
 */
object AppIcons {

  private fun icon(name: String, block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
      )
      .apply(block)
      .build()

  val Bolt: ImageVector by lazy {
    icon("Bolt") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(11f, 21f)
        horizontalLineToRelative(-1f)
        lineToRelative(1f, -7f)
        horizontalLineTo(7.5f)
        curveToRelative(-0.88f, 0f, -0.33f, -0.75f, -0.31f, -0.78f)
        curveTo(8.48f, 10.94f, 10.42f, 7.54f, 13.01f, 3f)
        horizontalLineToRelative(1f)
        lineToRelative(-1f, 7f)
        horizontalLineToRelative(3.51f)
        curveToRelative(0.4f, 0f, 0.62f, 0.19f, 0.4f, 0.66f)
        curveTo(12.97f, 17.55f, 11f, 21f, 11f, 21f)
        close()
      }
    }
  }

  val Upload: ImageVector by lazy {
    icon("Upload") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(5f, 20f)
        horizontalLineToRelative(14f)
        verticalLineToRelative(-2f)
        horizontalLineTo(5f)
        verticalLineToRelative(2f)
        close()
        moveTo(5f, 10f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(6f)
        horizontalLineToRelative(6f)
        verticalLineToRelative(-6f)
        horizontalLineToRelative(4f)
        lineToRelative(-7f, -7f)
        lineToRelative(-7f, 7f)
        close()
      }
    }
  }

  val Download: ImageVector by lazy {
    icon("Download") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(5f, 20f)
        horizontalLineToRelative(14f)
        verticalLineToRelative(-2f)
        horizontalLineTo(5f)
        verticalLineToRelative(2f)
        close()
        moveTo(19f, 9f)
        horizontalLineToRelative(-4f)
        verticalLineTo(3f)
        horizontalLineTo(9f)
        verticalLineToRelative(6f)
        horizontalLineTo(5f)
        lineToRelative(7f, 7f)
        lineToRelative(7f, -7f)
        close()
      }
    }
  }

  val Apps: ImageVector by lazy {
    icon("Apps") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(4f, 8f)
        horizontalLineToRelative(4f)
        verticalLineTo(4f)
        horizontalLineTo(4f)
        verticalLineToRelative(4f)
        close()
        moveTo(10f, 20f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(-4f)
        horizontalLineToRelative(-4f)
        verticalLineToRelative(4f)
        close()
        moveTo(4f, 20f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(-4f)
        horizontalLineTo(4f)
        verticalLineToRelative(4f)
        close()
        moveTo(4f, 14f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(-4f)
        horizontalLineTo(4f)
        verticalLineToRelative(4f)
        close()
        moveTo(10f, 14f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(-4f)
        horizontalLineToRelative(-4f)
        verticalLineToRelative(4f)
        close()
        moveTo(16f, 4f)
        verticalLineToRelative(4f)
        horizontalLineToRelative(4f)
        verticalLineTo(4f)
        horizontalLineToRelative(-4f)
        close()
        moveTo(10f, 8f)
        horizontalLineToRelative(4f)
        verticalLineTo(4f)
        horizontalLineToRelative(-4f)
        verticalLineToRelative(4f)
        close()
        moveTo(16f, 14f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(-4f)
        horizontalLineToRelative(-4f)
        verticalLineToRelative(4f)
        close()
        moveTo(16f, 20f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(-4f)
        horizontalLineToRelative(-4f)
        verticalLineToRelative(4f)
        close()
      }
    }
  }

  val VideoLibrary: ImageVector by lazy {
    icon("VideoLibrary") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(4f, 6f)
        horizontalLineTo(2f)
        verticalLineToRelative(14f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(14f)
        verticalLineToRelative(-2f)
        horizontalLineTo(4f)
        verticalLineTo(6f)
        close()
        moveTo(20f, 2f)
        horizontalLineTo(8f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(12f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(12f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(4f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        close()
        moveTo(12f, 14.5f)
        verticalLineToRelative(-9f)
        lineToRelative(6f, 4.5f)
        lineToRelative(-6f, 4.5f)
        close()
      }
    }
  }

  val PhotoLibrary: ImageVector by lazy {
    icon("PhotoLibrary") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(22f, 16f)
        verticalLineTo(4f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        horizontalLineTo(8f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(12f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(12f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        close()
        moveTo(11f, 12f)
        lineToRelative(2.03f, 2.71f)
        lineTo(16f, 11f)
        lineToRelative(4f, 5f)
        horizontalLineTo(8f)
        lineToRelative(3f, -4f)
        close()
        moveTo(2f, 6f)
        verticalLineToRelative(14f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(14f)
        verticalLineToRelative(-2f)
        horizontalLineTo(4f)
        verticalLineTo(6f)
        horizontalLineTo(2f)
        close()
      }
    }
  }

  val Folder: ImageVector by lazy {
    icon("Folder") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(10f, 4f)
        horizontalLineTo(4f)
        curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
        lineTo(2f, 18f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(16f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(8f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        horizontalLineToRelative(-8f)
        lineToRelative(-2f, -2f)
        close()
      }
    }
  }

  val CreateNewFolder: ImageVector by lazy {
    icon("CreateNewFolder") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(20f, 6f)
        horizontalLineToRelative(-8f)
        lineToRelative(-2f, -2f)
        horizontalLineTo(4f)
        curveTo(2.89f, 4f, 2.01f, 4.89f, 2.01f, 6f)
        lineTo(2f, 18f)
        curveToRelative(0f, 1.11f, 0.89f, 2f, 2f, 2f)
        horizontalLineToRelative(16f)
        curveToRelative(1.11f, 0f, 2f, -0.89f, 2f, -2f)
        verticalLineTo(8f)
        curveToRelative(0f, -1.11f, -0.89f, -2f, -2f, -2f)
        close()
        moveTo(19f, 14f)
        horizontalLineToRelative(-3f)
        verticalLineToRelative(3f)
        horizontalLineToRelative(-2f)
        verticalLineToRelative(-3f)
        horizontalLineToRelative(-3f)
        verticalLineToRelative(-2f)
        horizontalLineToRelative(3f)
        verticalLineTo(9f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(3f)
        horizontalLineToRelative(3f)
        verticalLineToRelative(2f)
        close()
      }
    }
  }

  val FolderCopy: ImageVector by lazy {
    icon("FolderCopy") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 6f)
        horizontalLineTo(1f)
        verticalLineToRelative(13f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(17f)
        verticalLineToRelative(-2f)
        horizontalLineTo(3f)
        verticalLineTo(6f)
        close()
        moveTo(21f, 4f)
        horizontalLineToRelative(-7f)
        lineToRelative(-2f, -2f)
        horizontalLineTo(7f)
        curveTo(5.9f, 2f, 5.01f, 2.9f, 5.01f, 4f)
        lineTo(5f, 15f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(14f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(6f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        close()
      }
    }
  }

  val InsertDriveFile: ImageVector by lazy {
    icon("InsertDriveFile") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(6f, 2f)
        curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
        lineTo(4f, 20f)
        curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f)
        horizontalLineTo(18f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(8f)
        lineToRelative(-6f, -6f)
        horizontalLineTo(6f)
        close()
        moveTo(13f, 9f)
        verticalLineTo(3.5f)
        lineTo(18.5f, 9f)
        horizontalLineTo(13f)
        close()
      }
    }
  }

  val Speed: ImageVector by lazy {
    icon("Speed") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(20.38f, 8.57f)
        lineToRelative(-1.23f, 1.85f)
        arcToRelative(8f, 8f, 0f, false, true, -0.22f, 7.58f)
        horizontalLineTo(5.07f)
        arcTo(8f, 8f, 0f, false, true, 15.58f, 6.85f)
        lineToRelative(1.85f, -1.23f)
        arcTo(10f, 10f, 0f, false, false, 3.35f, 19f)
        arcToRelative(2f, 2f, 0f, false, false, 1.72f, 1f)
        horizontalLineToRelative(13.85f)
        arcToRelative(2f, 2f, 0f, false, false, 1.74f, -1f)
        arcToRelative(10f, 10f, 0f, false, false, -0.27f, -10.44f)
        close()
        moveTo(10.59f, 15.41f)
        arcToRelative(2f, 2f, 0f, false, false, 2.83f, 0f)
        lineToRelative(5.66f, -8.49f)
        lineToRelative(-8.49f, 5.66f)
        arcToRelative(2f, 2f, 0f, false, false, 0f, 2.83f)
        close()
      }
    }
  }

  val SwapVert: ImageVector by lazy {
    icon("SwapVert") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(16f, 17.01f)
        verticalLineTo(10f)
        horizontalLineToRelative(-2f)
        verticalLineToRelative(7.01f)
        horizontalLineToRelative(-3f)
        lineTo(15f, 21f)
        lineToRelative(4f, -3.99f)
        horizontalLineToRelative(-3f)
        close()
        moveTo(9f, 3f)
        lineTo(5f, 6.99f)
        horizontalLineToRelative(3f)
        verticalLineTo(14f)
        horizontalLineToRelative(2f)
        verticalLineTo(6.99f)
        horizontalLineToRelative(3f)
        lineTo(9f, 3f)
        close()
      }
    }
  }

  val History: ImageVector by lazy {
    icon("History") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(13f, 3f)
        curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
        horizontalLineTo(1f)
        lineToRelative(3.89f, 3.89f)
        lineToRelative(0.07f, 0.14f)
        lineTo(9f, 12f)
        horizontalLineTo(6f)
        curveToRelative(0f, -3.87f, 3.13f, -7f, 7f, -7f)
        reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
        reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
        curveToRelative(-1.93f, 0f, -3.68f, -0.79f, -4.94f, -2.06f)
        lineToRelative(-1.42f, 1.42f)
        curveTo(8.27f, 19.99f, 10.51f, 21f, 13f, 21f)
        curveToRelative(4.97f, 0f, 9f, -4.03f, 9f, -9f)
        reflectiveCurveToRelative(-4.03f, -9f, -9f, -9f)
        close()
        moveTo(12f, 8f)
        verticalLineToRelative(5f)
        lineToRelative(4.28f, 2.54f)
        lineToRelative(0.72f, -1.21f)
        lineToRelative(-3.5f, -2.08f)
        verticalLineTo(8f)
        horizontalLineTo(12f)
        close()
      }
    }
  }

  val QrCodeScanner: ImageVector by lazy {
    icon("QrCodeScanner") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(9.5f, 6.5f)
        verticalLineToRelative(3f)
        horizontalLineToRelative(-3f)
        verticalLineToRelative(-3f)
        horizontalLineTo(9.5f)
        moveTo(11f, 5f)
        horizontalLineTo(5f)
        verticalLineToRelative(6f)
        horizontalLineToRelative(6f)
        verticalLineTo(5f)
        close()
        moveTo(9.5f, 14.5f)
        verticalLineToRelative(3f)
        horizontalLineToRelative(-3f)
        verticalLineToRelative(-3f)
        horizontalLineTo(9.5f)
        moveTo(11f, 13f)
        horizontalLineTo(5f)
        verticalLineToRelative(6f)
        horizontalLineToRelative(6f)
        verticalLineTo(13f)
        close()
        moveTo(17.5f, 6.5f)
        verticalLineToRelative(3f)
        horizontalLineToRelative(-3f)
        verticalLineToRelative(-3f)
        horizontalLineTo(17.5f)
        moveTo(19f, 5f)
        horizontalLineToRelative(-6f)
        verticalLineToRelative(6f)
        horizontalLineToRelative(6f)
        verticalLineTo(5f)
        close()
        moveTo(13f, 13f)
        horizontalLineToRelative(1.5f)
        verticalLineToRelative(1.5f)
        horizontalLineTo(13f)
        verticalLineTo(13f)
        close()
        moveTo(14.5f, 14.5f)
        horizontalLineTo(16f)
        verticalLineTo(16f)
        horizontalLineToRelative(-1.5f)
        verticalLineTo(14.5f)
        close()
        moveTo(16f, 13f)
        horizontalLineToRelative(1.5f)
        verticalLineToRelative(1.5f)
        horizontalLineTo(16f)
        verticalLineTo(13f)
        close()
        moveTo(13f, 16f)
        horizontalLineToRelative(1.5f)
        verticalLineToRelative(1.5f)
        horizontalLineTo(13f)
        verticalLineTo(16f)
        close()
        moveTo(14.5f, 17.5f)
        horizontalLineTo(16f)
        verticalLineTo(19f)
        horizontalLineToRelative(-1.5f)
        verticalLineTo(17.5f)
        close()
        moveTo(16f, 16f)
        horizontalLineToRelative(1.5f)
        verticalLineToRelative(1.5f)
        horizontalLineTo(16f)
        verticalLineTo(16f)
        close()
        moveTo(17.5f, 14.5f)
        horizontalLineTo(19f)
        verticalLineTo(16f)
        horizontalLineToRelative(-1.5f)
        verticalLineTo(14.5f)
        close()
        moveTo(17.5f, 17.5f)
        horizontalLineTo(19f)
        verticalLineTo(19f)
        horizontalLineToRelative(-1.5f)
        verticalLineTo(17.5f)
        close()
        moveTo(22f, 7f)
        horizontalLineToRelative(-2f)
        verticalLineTo(4f)
        horizontalLineToRelative(-3f)
        verticalLineTo(2f)
        horizontalLineToRelative(5f)
        verticalLineTo(7f)
        close()
        moveTo(22f, 22f)
        verticalLineToRelative(-5f)
        horizontalLineToRelative(-2f)
        verticalLineToRelative(3f)
        horizontalLineToRelative(-3f)
        verticalLineToRelative(2f)
        horizontalLineTo(22f)
        close()
        moveTo(2f, 22f)
        horizontalLineToRelative(5f)
        verticalLineToRelative(-2f)
        horizontalLineTo(4f)
        verticalLineToRelative(-3f)
        horizontalLineTo(2f)
        verticalLineTo(22f)
        close()
        moveTo(2f, 2f)
        verticalLineToRelative(5f)
        horizontalLineToRelative(2f)
        verticalLineTo(4f)
        horizontalLineToRelative(3f)
        verticalLineTo(2f)
        horizontalLineTo(2f)
        close()
      }
    }
  }

  val Wifi: ImageVector by lazy {
    icon("Wifi") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(1f, 9f)
        lineToRelative(2f, 2f)
        curveToRelative(4.97f, -4.97f, 13.03f, -4.97f, 18f, 0f)
        lineToRelative(2f, -2f)
        curveTo(16.93f, 2.93f, 7.08f, 2.93f, 1f, 9f)
        close()
        moveTo(9f, 17f)
        lineToRelative(3f, 3f)
        lineToRelative(3f, -3f)
        curveToRelative(-1.65f, -1.66f, -4.34f, -1.66f, -6f, 0f)
        close()
        moveTo(5f, 13f)
        lineToRelative(2f, 2f)
        curveToRelative(2.76f, -2.76f, 7.24f, -2.76f, 10f, 0f)
        lineToRelative(2f, -2f)
        curveTo(15.14f, 9.14f, 8.87f, 9.14f, 5f, 13f)
        close()
      }
    }
  }

  val PhoneAndroid: ImageVector by lazy {
    icon("PhoneAndroid") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(16f, 1f)
        horizontalLineTo(8f)
        curveTo(6.34f, 1f, 5f, 2.34f, 5f, 4f)
        verticalLineToRelative(16f)
        curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
        horizontalLineToRelative(8f)
        curveToRelative(1.66f, 0f, 3f, -1.34f, 3f, -3f)
        verticalLineTo(4f)
        curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
        close()
        moveTo(14f, 21f)
        horizontalLineToRelative(-4f)
        verticalLineToRelative(-1f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(1f)
        close()
        moveTo(17.25f, 18f)
        horizontalLineTo(6.75f)
        verticalLineTo(4f)
        horizontalLineToRelative(10.5f)
        verticalLineToRelative(14f)
        close()
      }
    }
  }

  val Radar: ImageVector by lazy {
    icon("Radar") {
      path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
        reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
        reflectiveCurveTo(17.52f, 2f, 12f, 2f)
        close()
        moveTo(12f, 20f)
        curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
        reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
        reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
        reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
        close()
        moveTo(12f, 6f)
        curveToRelative(-3.31f, 0f, -6f, 2.69f, -6f, 6f)
        reflectiveCurveToRelative(2.69f, 6f, 6f, 6f)
        reflectiveCurveToRelative(6f, -2.69f, 6f, -6f)
        reflectiveCurveToRelative(-2.69f, -6f, -6f, -6f)
        close()
        moveTo(12f, 16f)
        curveToRelative(-2.21f, 0f, -4f, -1.79f, -4f, -4f)
        reflectiveCurveToRelative(1.79f, -4f, 4f, -4f)
        reflectiveCurveToRelative(4f, 1.79f, 4f, 4f)
        reflectiveCurveToRelative(-1.79f, 4f, -4f, 4f)
        close()
        moveTo(12f, 10f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
        reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
        reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
        close()
      }
    }
  }

  val RadioButtonUnchecked: ImageVector by lazy {
    icon("RadioButtonUnchecked") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
        reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
        reflectiveCurveTo(17.52f, 2f, 12f, 2f)
        close()
        moveTo(12f, 20f)
        curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
        reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
        reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
        reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
        close()
      }
    }
  }

  val DeleteOutline: ImageVector by lazy {
    icon("DeleteOutline") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(6f, 19f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(8f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(7f)
        horizontalLineTo(6f)
        verticalLineToRelative(12f)
        close()
        moveTo(8f, 9f)
        horizontalLineToRelative(8f)
        verticalLineToRelative(10f)
        horizontalLineTo(8f)
        verticalLineTo(9f)
        close()
        moveTo(15.5f, 4f)
        lineToRelative(-1f, -1f)
        horizontalLineToRelative(-5f)
        lineToRelative(-1f, 1f)
        horizontalLineTo(5f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(14f)
        verticalLineTo(4f)
        horizontalLineToRelative(-3.5f)
        close()
      }
    }
  }

  val DeleteSweep: ImageVector by lazy {
    icon("DeleteSweep") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(15f, 16f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(-4f)
        close()
        moveTo(15f, 8f)
        horizontalLineToRelative(7f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(-7f)
        close()
        moveTo(15f, 12f)
        horizontalLineToRelative(6f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(-6f)
        close()
        moveTo(3f, 18f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(6f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(8f)
        horizontalLineTo(3f)
        verticalLineToRelative(10f)
        close()
        moveTo(14f, 5f)
        horizontalLineToRelative(-3f)
        lineToRelative(-1f, -1f)
        horizontalLineTo(6f)
        lineTo(5f, 5f)
        horizontalLineTo(2f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(12f)
        close()
      }
    }
  }

  val ErrorOutline: ImageVector by lazy {
    icon("ErrorOutline") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(11f, 15f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(-2f)
        close()
        moveTo(11f, 7f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(6f)
        horizontalLineToRelative(-2f)
        close()
        moveTo(11.99f, 2f)
        curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
        reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f)
        curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
        reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
        close()
        moveTo(12f, 20f)
        curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
        reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
        reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
        reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
        close()
      }
    }
  }

  val OpenInNew: ImageVector by lazy {
    icon("OpenInNew") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(19f, 19f)
        horizontalLineTo(5f)
        verticalLineTo(5f)
        horizontalLineToRelative(7f)
        verticalLineTo(3f)
        horizontalLineTo(5f)
        curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(14f)
        curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
        horizontalLineToRelative(14f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineToRelative(-7f)
        horizontalLineToRelative(-2f)
        verticalLineToRelative(7f)
        close()
        moveTo(14f, 3f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(3.59f)
        lineToRelative(-9.83f, 9.83f)
        lineToRelative(1.41f, 1.41f)
        lineTo(19f, 6.41f)
        verticalLineTo(10f)
        horizontalLineToRelative(2f)
        verticalLineTo(3f)
        horizontalLineToRelative(-7f)
        close()
      }
    }
  }

  val ContentCopy: ImageVector by lazy {
    icon("ContentCopy") {
      path(fill = SolidColor(Color.Black)) {
        moveTo(16f, 1f)
        horizontalLineTo(4f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(14f)
        horizontalLineToRelative(2f)
        verticalLineTo(3f)
        horizontalLineToRelative(12f)
        verticalLineTo(1f)
        close()
        moveTo(19f, 5f)
        horizontalLineTo(8f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(14f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(11f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(7f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        close()
        moveTo(19f, 21f)
        horizontalLineTo(8f)
        verticalLineTo(7f)
        horizontalLineToRelative(11f)
        verticalLineToRelative(14f)
        close()
      }
    }
  }
}
