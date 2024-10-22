package com.nenfuat.getheartrateapplication.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

@Composable
fun GetHeartRateApplicationTheme(
    content: @Composable () -> Unit
) {
    /**
     * Empty theme to customize for your app.
     * See: https://developer.android.com/jetpack/compose/designsystems/custom
     */
    MaterialTheme(
        colors = Colors(
            primary = Color.White, // テキストの色を黒
            onPrimary = Color.Black, // ボタン上の文字を白
            background = Color(0xFFFFC0CB),  // 背景色を薄いピンク
            surface = Color(0xFFFFC0CB),     // ボタンやカードの背景もピンク
            onBackground = Color.White,  // 背景上のテキストを白
            onSurface = Color.White      // 表面上の文字も白
        ),
        content = content
    )
}