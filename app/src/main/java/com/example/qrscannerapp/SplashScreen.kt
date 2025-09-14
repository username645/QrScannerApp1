package com.example.qrscannerapp

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

// --- Цветовая палитра остается той же ---
val SplashScreenBackgroundColor = Color.Black
val SplashScreenLogoColor = Color(0xFF7B61FF)
val SplashScreenTextColor = Color(0xFFEAEAF0)
val SplashScreenStudioTextColor = Color(0xFFFFFBEB)
val SplashScreenStudioGlowColor = Color(0xFFFFC107)
val BeamColor = Color(0x1AFFFFFF) // Цвет луча проектора

// --- Настройка шрифта остается той же ---
val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)
val InterFont = FontFamily(
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider)
)

private val BeamShape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            moveTo(size.width * 0.35f, 0f)
            lineTo(size.width * 0.65f, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun Particles(modifier: Modifier) {
    val particles = remember {
        List(50) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 2f + 1f,
                alpha = Random.nextFloat() * 0.5f + 0.2f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "particleTransition")
    val animationProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing)
        ), label = "particleProgress"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val yOffset = (particle.y + animationProgress) % 1.0f
            drawCircle(
                color = Color.White,
                radius = particle.size,
                center = Offset(particle.x * size.width, yOffset * size.height),
                alpha = particle.alpha
            )
        }
    }
}

data class Particle(val x: Float, val y: Float, val size: Float, val alpha: Float)


@Composable
fun SplashScreen(onAnimationFinished: () -> Unit) {

    var startAnimation by remember { mutableStateOf(false) }

    // --- АНИМАЦИИ ---
    val lampAlpha by animateFloatAsState(targetValue = if (startAnimation) 1f else 0f, animationSpec = tween(1000), label = "lampAlpha")
    val beamScaleY by animateFloatAsState(targetValue = if (startAnimation) 1f else 0f, animationSpec = tween(2000, 500), label = "beamScaleY")
    val logoReveal by animateFloatAsState(targetValue = if (startAnimation) 1f else 0f, animationSpec = tween(2000, 1000), label = "logoReveal")
    val textAlpha by animateFloatAsState(targetValue = if (startAnimation) 1f else 0f, animationSpec = tween(1500, 3000), label = "textAlpha")
    val studioAlpha by animateFloatAsState(targetValue = if (startAnimation) 1f else 0f, animationSpec = tween(1500, 3500), label = "studioAlpha")

    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(5000)
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashScreenBackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        // --- ПРОЕКТОР И ЛУЧ ---
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .width(800.dp)
                    .fillMaxHeight(0.6f)
                    .graphicsLayer {
                        scaleY = beamScaleY
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        clip = true
                        shape = BeamShape
                    }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(BeamColor, Color.Transparent)
                        )
                    )
            )
            Particles(modifier = Modifier
                .width(800.dp)
                .fillMaxHeight(0.6f)
                .graphicsLayer {
                    alpha = beamScaleY
                    clip = true
                    shape = BeamShape
                }
            )
            Box(
                modifier = Modifier
                    .padding(top = (LocalConfiguration.current.screenHeightDp.dp * 0.15f))
                    .width(250.dp)
                    .height(4.dp)
                    .alpha(lampAlpha)
                    .background(SplashScreenLogoColor, shape = RoundedCornerShape(2.dp))
            )
        }

        // --- ЛОГОТИП И НАЗВАНИЕ ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // --- ИСПРАВЛЕНИЕ ЗДЕСЬ: Правильная анимация проявления ---
            Image(
                painter = painterResource(id = R.drawable.ic_qr_logo),
                contentDescription = "QR Scan Logo",
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = 0f,
                                endY = size.height * logoReveal // Анимируем конечную точку градиента
                            ),
                            blendMode = BlendMode.DstOut // Используем DstOut, чтобы "стирать" черную маску
                        )
                    },
                colorFilter = ColorFilter.tint(SplashScreenLogoColor)
            )
            // --- КОНЕЦ ИСПРАВЛЕНИЯ ---

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "QR SCAN",
                modifier = Modifier.alpha(textAlpha),
                fontFamily = InterFont,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                color = SplashScreenTextColor,
                letterSpacing = 4.sp
            )
        }

        // --- ПОДПИСЬ СТУДИИ ---
        Text(
            text = "A LUCIUS STUDIO PROJECT",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .alpha(studioAlpha),
            fontFamily = InterFont,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = SplashScreenStudioTextColor,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(
                shadow = Shadow(
                    color = SplashScreenStudioGlowColor.copy(alpha = 0.7f),
                    blurRadius = 15f
                )
            )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun SplashScreenPreview() {
    SplashScreen(onAnimationFinished = {})
}