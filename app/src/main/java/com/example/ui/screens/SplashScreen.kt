package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberMagenta
import com.example.ui.theme.DeepRoyalPurple
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.ObsidianBg

@Composable
fun SplashScreen(onSkip: () -> Unit) {
  var revealed by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { revealed = true }

  val scale by
    animateFloatAsState(
      targetValue = if (revealed) 1f else 0.86f,
      animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
      label = "splashScale",
    )
  val alpha by
    animateFloatAsState(
      targetValue = if (revealed) 1f else 0f,
      animationSpec = tween(durationMillis = 500),
      label = "splashAlpha",
    )

  val infinite = rememberInfiniteTransition(label = "auraPulse")
  val pulse by
    infinite.animateFloat(
      initialValue = 0.88f,
      targetValue = 1.22f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(1400, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "pulse",
    )

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.radialGradient(
            colors = listOf(DeepRoyalPurple.copy(alpha = 0.65f), ObsidianBg),
            radius = 950f,
          )
        )
        .safeDrawingPadding()
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onSkip,
        )
        .testTag("splash_screen"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 480.dp).padding(24.dp).alpha(alpha).scale(scale),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val baseRadius = size.minDimension * 0.38f
          drawCircle(
            color = CyberCyan.copy(alpha = 0.30f),
            radius = baseRadius * pulse * 1.18f,
            style = Stroke(width = 3.dp.toPx()),
          )
          drawCircle(
            color = CyberMagenta.copy(alpha = 0.22f),
            radius = baseRadius * pulse * 1.32f,
            style = Stroke(width = 2.dp.toPx()),
          )
        }

        Image(
          painter = painterResource(id = R.drawable.fast_share_logo),
          contentDescription = stringResource(R.string.cd_ammar_brand_logo),
          contentScale = ContentScale.Crop,
          modifier =
            Modifier.size(132.dp)
              .clip(CircleShape)
              .border(
                width = 3.dp,
                brush = Brush.linearGradient(listOf(CyberCyan, ElectricViolet, CyberMagenta)),
                shape = CircleShape,
              ),
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "FAST SHARE",
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.onBackground,
        letterSpacing = 2.4.sp,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = stringResource(R.string.splash_tagline),
        style = MaterialTheme.typography.labelSmall,
        color = CyberCyan,
        letterSpacing = 1.6.sp,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(20.dp))

      AnimatedDevCreditsCard(compact = true)

      Spacer(modifier = Modifier.height(14.dp))

      LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
        color = CyberCyan,
        trackColor = DeepRoyalPurple.copy(alpha = 0.4f),
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "Tap anywhere to continue",
        style = MaterialTheme.typography.labelSmall,
        color = NeonViolet.copy(alpha = 0.7f),
      )
    }
  }
}
