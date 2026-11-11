package com.movexa.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movexa.app.core.designsystem.MovexaColors
import com.movexa.app.core.designsystem.MovexaPrimaryButton
import com.movexa.app.core.designsystem.MovexaType
import kotlinx.coroutines.launch

private data class OnboardingSlide(val title: String, val body: String)

private val slides = listOf(
    OnboardingSlide("Edit video, fast", "Trim, filter, and animate clips right on your phone."),
    OnboardingSlide("Edit photos too", "Crop, retouch, and style your shots in seconds."),
    OnboardingSlide("Go Pro when you're ready", "Unlock premium filters, templates, and more."),
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(MovexaColors.bgBase).statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TextButton(
                onClick = onFinish,
                modifier = Modifier.align(Alignment.End).padding(16.dp),
            ) {
                Text("Skip", style = MovexaType.label, color = MovexaColors.textSecondary)
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                val slide = slides[page]
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    OnboardingArt(
                        variant = page,
                        modifier = Modifier.fillMaxWidth(0.82f),
                    )
                    Text(
                        text = slide.title,
                        style = MovexaType.display,
                        color = MovexaColors.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 36.dp),
                    )
                    Text(
                        text = slide.body,
                        style = MovexaType.body,
                        color = MovexaColors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            Row(pageCount = slides.size, currentPage = pagerState.currentPage)

            val isLast = pagerState.currentPage == slides.lastIndex
            MovexaPrimaryButton(
                text = if (isLast) "Get started" else "Next",
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                onClick = {
                    if (isLast) {
                        onFinish()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun Row(pageCount: Int, currentPage: Int) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(if (index == currentPage) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) MovexaColors.brandPrimary else MovexaColors.divider
                    ),
            )
        }
    }
}
