package com.example.timeapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val R = FontFamily.Default   // Roboto is the platform default

/** Sizes and line heights taken literally from the mock. */
val TimeTypography = Typography(
    displaySmall = TextStyle(fontFamily = R, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 39.sp),
    headlineSmall = TextStyle(fontFamily = R, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontFamily = R, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontFamily = R, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 26.sp),
    titleSmall = TextStyle(fontFamily = R, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontFamily = R, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = R, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = R, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = R, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 15.sp),
    labelMedium = TextStyle(fontFamily = R, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 13.sp),
    labelSmall = TextStyle(
        fontFamily = R, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 12.sp,
        letterSpacing = 0.6.sp,
    ),
)

/** Not M3 roles — used where the mock departs from the scale. */
val NavLabel = TextStyle(fontFamily = R, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
val Overline = TextStyle(
    fontFamily = R, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 12.sp,
    letterSpacing = 0.8.sp,
)
val Wordmark = TextStyle(
    fontFamily = R, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 15.sp,
    letterSpacing = 1.2.sp,
)
