package com.swent.polyloop.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// Sizes and weights from the Figma mockups, with the system font. Line height is 1.35x the size.
private fun polyLoopStyle(size: Int, weight: FontWeight, letterSpacing: TextUnit = 0.sp) =
    TextStyle(
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * 1.35f).sp,
        letterSpacing = letterSpacing,
    )

val Typography =
    Typography(
        headlineLarge = polyLoopStyle(36, FontWeight.ExtraBold, (-1).sp), // logo
        headlineMedium = polyLoopStyle(26, FontWeight.Bold, (-0.4).sp), // screen title
        headlineSmall = polyLoopStyle(24, FontWeight.Bold, (-0.4).sp), // listing title
        titleLarge = polyLoopStyle(21, FontWeight.Bold), // big price, empty state title
        titleMedium = polyLoopStyle(17, FontWeight.SemiBold), // card title
        titleSmall = polyLoopStyle(15, FontWeight.SemiBold), // tabs
        bodyLarge = polyLoopStyle(16, FontWeight.Normal), // inputs, subtitles
        bodyMedium = polyLoopStyle(15, FontWeight.Normal), // secondary text
        bodySmall = polyLoopStyle(13, FontWeight.Normal), // helper and error text
        labelLarge = polyLoopStyle(16, FontWeight.SemiBold), // buttons
        labelMedium = polyLoopStyle(14, FontWeight.SemiBold), // field labels
        labelSmall = polyLoopStyle(13, FontWeight.SemiBold), // chips
    )
