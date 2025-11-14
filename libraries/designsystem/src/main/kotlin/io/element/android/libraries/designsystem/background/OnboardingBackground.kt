/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.utils.drawWithLayer

/**
 * Gradient background for FTUE (onboarding) screens.
 */
@Suppress("ModifierMissing")
@Composable
fun OnboardingBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ElementTheme.colors.bgCanvasDefault)
    ) {
        val isLightTheme = ElementTheme.isLightTheme
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
        ) {
            val gradientBrush = ShaderBrush(
                LinearGradientShader(
                    from = Offset(0f, size.height / 2f),
                    to = Offset(size.width, size.height / 2f),
                    colors = listOf(
                        Color(0xFF8A2BE2), // BlueViolet base color
                        if (isLightTheme) Color(0xCC6A0DAD) else Color(0xFF6A0DAD) // Semi-transparent violet for light mode, opaque for dark
                    )
                )
            )
            val eraseBrush = ShaderBrush(
                LinearGradientShader(
                    from = Offset(size.width / 2f, 0f),
                    to = Offset(size.width / 2f, size.height * 2f),
                    colors = listOf(
                        Color(0xFF000000),
                        Color(0x00000000)
                    )
                )
            )
            drawWithLayer {
                drawRect(brush = gradientBrush, size = size)
                drawRect(brush = gradientBrush, size = size, blendMode = BlendMode.Overlay)
                drawRect(brush = eraseBrush, size = size, blendMode = BlendMode.DstOut)
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun OnboardingBackgroundPreview() {
    ElementPreview {
        OnboardingBackground()
    }
}

/**
 * Gradient background for FTUE (onboarding call) screens.
 */
@Suppress("ModifierMissing")
@Composable
fun OnboardingBackgroundCall() {
    val isLightTheme = ElementTheme.isLightTheme

    val topBackgroundBrush = Brush.verticalGradient(
        colors = if (isLightTheme) {
            listOf(
                Color(0xFFB39DDB),
                Color(0xFFEDE7F6)
            )
        } else {
            listOf(
                Color(0xFF49369D),
                Color(0xFF9586B2)
            )
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = topBackgroundBrush)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
        ) {
            val gradientBrush = ShaderBrush(
                LinearGradientShader(
                    from = Offset(0f, size.height / 2f),
                    to = Offset(size.width, size.height / 2f),
                    colors = if (isLightTheme) {
                        listOf(
                            Color(0xFF8A2BE2),
                            Color(0xCC6A0DAD)
                        )
                    } else {
                        listOf(
                            Color(0xFF5D8DC0).copy(alpha = 0.7f),
                            Color(0xFF649FDE).copy(alpha = 0.7f)
                        )
                    }
                )
            )
            val eraseBrush = ShaderBrush(
                LinearGradientShader(
                    from = Offset(size.width / 2f, 0f),
                    to = Offset(size.width / 2f, size.height * 2f),
                    colors = listOf(
                        Color(0xFF000000),
                        Color(0x00000000)
                    )
                )
            )
            drawWithLayer {
                drawRect(brush = gradientBrush, size = size)
                drawRect(brush = gradientBrush, size = size, blendMode = BlendMode.Overlay)
                drawRect(brush = eraseBrush, size = size, blendMode = BlendMode.DstOut)
            }
        }
    }
}



@PreviewsDayNight
@Composable
internal fun OnboardingBackgroundCallPreview() {
    ElementPreview {
        OnboardingBackgroundCall()
    }
}
