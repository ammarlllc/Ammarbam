package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.AppIcons
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberMagenta
import com.example.ui.theme.DeepRoyalPurple
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import kotlinx.coroutines.delay

@Composable
fun AnimatedDevCreditsCard(
  modifier: Modifier = Modifier,
  compact: Boolean = false,
) {
  var stage by remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    stage = 1
    delay(160L)
    stage = 2
    delay(180L)
    stage = 3
    delay(200L)
    stage = 4
  }

  val infinite = rememberInfiniteTransition(label = "creditsAnim")
  val orbitAngle by
    infinite.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
      label = "orbitAngle",
    )
  val glowPulse by
    infinite.animateFloat(
      initialValue = 0.72f,
      targetValue = 1.0f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(1200, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "glowPulse",
    )

  Surface(
    shape = RoundedCornerShape(22.dp),
    color = ObsidianSurfaceHigh.copy(alpha = 0.94f),
    tonalElevation = 8.dp,
    modifier =
      modifier
        .fillMaxWidth()
        .border(
          width = 1.6.dp,
          brush =
            Brush.linearGradient(
              listOf(
                CyberCyan.copy(alpha = glowPulse),
                ElectricViolet,
                CyberMagenta.copy(alpha = glowPulse),
              )
            ),
          shape = RoundedCornerShape(22.dp),
        )
        .testTag("credits_card"),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 18.dp, vertical = if (compact) 14.dp else 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
      // Animated CREDITS header
      AnimatedVisibility(
        visible = stage >= 1,
        enter = fadeIn(tween(350)) + slideInVertically { -it / 2 },
      ) {
        Text(
          text = stringResource(R.string.credits_title),
          style = MaterialTheme.typography.titleMedium,
          fontFamily = JetBrainsMonoFamily,
          fontWeight = FontWeight.ExtraBold,
          color = CyberCyan,
          letterSpacing = 5.sp,
          modifier = Modifier.testTag("credits_title_text"),
        )
      }

      // Animated Rotating Dev Icon Badge
      AnimatedVisibility(
        visible = stage >= 2,
        enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.75f),
      ) {
        Box(
          modifier = Modifier.size(if (compact) 68.dp else 88.dp),
          contentAlignment = Alignment.Center,
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
              color = ElectricViolet.copy(alpha = 0.25f),
              radius = r * 0.94f,
              style = Stroke(width = 2.dp.toPx()),
            )
            rotate(orbitAngle) {
              drawArc(
                brush = Brush.sweepGradient(listOf(CyberCyan, ElectricViolet, CyberMagenta, CyberCyan)),
                startAngle = 0f,
                sweepAngle = 220f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
              )
            }
          }
          Box(
            modifier =
              Modifier.size(if (compact) 48.dp else 62.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(DeepRoyalPurple, ObsidianBg)))
                .border(1.5.dp, CyberCyan.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = AppIcons.Terminal,
              contentDescription = "Developer Icon",
              tint = CyberCyan,
              modifier = Modifier.size(if (compact) 26.dp else 34.dp),
            )
          }
        }
      }

      // Staggered Sub-roles
      AnimatedVisibility(
        visible = stage >= 3,
        enter = fadeIn(tween(420)) + slideInVertically { it / 3 },
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          CreditSubPill("DEV ARCHITECT • 6x TURBO P2P")
          if (!compact) {
            CreditSubPill("AUTO-HOTSPOT • APK & DATA ENGINE")
            CreditSubPill("MOVIEBOX & PUBLIC STORAGE")
          }
        }
      }

      // Prominent Glowing AMMAR H4CK34 Badge
      AnimatedVisibility(
        visible = stage >= 4,
        enter = fadeIn(tween(450)) + scaleIn(initialScale = 0.85f),
      ) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = ObsidianSurface,
          modifier =
            Modifier.fillMaxWidth()
              .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(listOf(CyberCyan, CyberMagenta)),
                shape = RoundedCornerShape(14.dp),
              ),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = stringResource(R.string.credits_dev_name),
              style =
                if (compact) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.headlineMedium,
              fontFamily = JetBrainsMonoFamily,
              fontWeight = FontWeight.Black,
              color = CyberCyan.copy(alpha = glowPulse),
              letterSpacing = 2.sp,
              textAlign = TextAlign.Center,
              modifier = Modifier.testTag("credits_dev_name_text"),
            )
            Text(
              text = stringResource(R.string.splash_credit),
              style = MaterialTheme.typography.labelSmall,
              color = NeonViolet,
              letterSpacing = 1.sp,
              textAlign = TextAlign.Center,
              modifier = Modifier.testTag("splash_credit_text"),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CreditSubPill(text: String) {
  Surface(
    shape = RoundedCornerShape(50),
    color = ElectricViolet.copy(alpha = 0.16f),
    modifier = Modifier.border(1.dp, ElectricViolet.copy(alpha = 0.45f), RoundedCornerShape(50)),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      fontFamily = JetBrainsMonoFamily,
      color = Color.White.copy(alpha = 0.9f),
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun CreditsOverlay(onDismiss: () -> Unit) {
  BackHandler(onBack = onDismiss)

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(ObsidianBg.copy(alpha = 0.94f))
        .safeDrawingPadding()
        .padding(20.dp)
        .testTag("credits_overlay"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(AppIcons.Bolt, contentDescription = null, tint = EmeraldSuccess)
          Text(
            text = "FAST SHARE TEAM",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(48.dp).testTag("close_credits_button"),
        ) {
          Icon(Icons.Rounded.Close, contentDescription = "Close credits", tint = Color.White)
        }
      }

      AnimatedDevCreditsCard(compact = false)

      Button(
        onClick = onDismiss,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = CyberCyan,
            contentColor = ObsidianBg,
          ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
      ) {
        Text("Back to Fast Share", fontWeight = FontWeight.ExtraBold)
      }
    }
  }
}
