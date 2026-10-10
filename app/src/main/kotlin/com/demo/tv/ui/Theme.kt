package com.demo.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF0B0F1A)
val Panel = Color(0xFF151B2B)
val PanelFocused = Color(0xFF26324F)
val Accent = Color(0xFF4F8CFF)
val TextDim = Color(0xFF9AA4BD)

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, background = Bg, surface = Panel,
            onPrimary = Color.White, onBackground = Color.White, onSurface = Color.White,
        ),
        content = content,
    )
}

/**
 * A D-pad focusable row. [selected] marks the current choice (e.g. active category)
 * even when focus has moved elsewhere.
 */
@Composable
fun FocusRow(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    secondary: String? = null,
    onFocus: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.hasFocus; if (it.hasFocus) onFocus?.invoke() }
            .background(
                when {
                    focused -> PanelFocused
                    selected -> Panel
                    else -> Color.Transparent
                }, shape,
            )
            .border(if (focused) BorderStroke(2.dp, Accent) else BorderStroke(0.dp, Color.Transparent), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        androidx.compose.foundation.layout.Column {
            Text(
                text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (selected || focused) Color.White else TextDim,
            )
            if (secondary != null) Text(secondary, fontSize = 13.sp, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun TvButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .onFocusChanged { focused = it.hasFocus }
            .background(if (focused) Accent else Panel, shape)
            .border(BorderStroke(2.dp, if (focused) Color.White else Color.Transparent), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 18.sp, color = Color.White) }
}
