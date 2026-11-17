package com.wuming.tinkerbox.ui.terminal

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.wuming.tinkerbox.core.terminal.EmulatorSession
import com.wuming.tinkerbox.core.terminal.em.TextStyle

/**
 * 用 Compose 渲染 termux terminal-emulator 的「屏幕缓冲」。
 *
 * 逐格读取 [EmulatorSession] 内 emulator 的屏幕缓冲：
 * - 字符取自 TerminalRow.mText；
 * - 样式由 TextStyle.decodeForeColor / decodeBackColor / decodeEffect 解出前景、背景、加粗/斜体；
 * - 光标格做反显。合成一段 AnnotatedString 整屏绘制，得到类 termux 的彩色回显。
 */
@Composable
fun EmulatorView(session: EmulatorSession, modifier: Modifier = Modifier) {
    val version by session.version.collectAsState()

    val annotated = remember(session, version) {
        buildScreen(session) ?: buildAnnotatedString { append("") }
    }

    SelectionContainer(modifier = modifier) {
        androidx.compose.material3.Text(
            text = annotated,
            fontFamily = FontFamily.Monospace,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
        )
    }
}

/** 读取屏幕缓冲并构建带样式的整屏 AnnotatedString；仿真器未就绪时返回 null。 */
private fun buildScreen(session: EmulatorSession): AnnotatedString? {
    val emulator = session.emulatorRef() ?: return null
    val buffer = emulator.getScreen()
    val rows = emulator.getRows()
    val cols = emulator.getColumns()
    val activeRows = buffer.getActiveRows()
    val startRow = maxOf(0, activeRows - rows)
    val cursorRow = emulator.getCursorRow()
    val cursorCol = emulator.getCursorCol()
    val cursorVisible = emulator.shouldCursorBeVisible()

    val sb = buildAnnotatedString {
        for (ty in 0 until rows) {
            if (ty > 0) append('\n')
            val rowIdx = startRow + ty
            val line = buffer.allocateFullLineIfNecessary(rowIdx)
            val text = line.mText
            for (cx in 0 until cols) {
                val ch: Char =
                    if (cx < text.size && text[cx] != '\u0000') text[cx] else ' '
                val style = line.getStyle(cx)
                var fgIndex = TextStyle.decodeForeColor(style)
                var bgIndex = TextStyle.decodeBackColor(style)
                val effect = TextStyle.decodeEffect(style)

                if ((effect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE) != 0) {
                    val t = fgIndex; fgIndex = bgIndex; bgIndex = t
                }
                // 光标格反显（模拟 termux 光标色块）。
                if (cursorVisible && ty == cursorRow && cx == cursorCol) {
                    val t = fgIndex; fgIndex = bgIndex; bgIndex = t
                }

                val fg = session.indexedColor(fgIndex)
                val bg = session.indexedColor(bgIndex)

                val span = SpanStyle(
                    color = Color(fg),
                    // 直接给背景色；透明 alpha 的单元格天然不显示，无需特判。
                    background = Color(bg),
                    fontWeight = if ((effect and TextStyle.CHARACTER_ATTRIBUTE_BOLD) != 0) FontWeight.Bold else null,
                    fontStyle = if ((effect and TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0) FontStyle.Italic else null,
                )
                withStyle(span) { append(ch) }
            }
        }
    }
    return sb
}