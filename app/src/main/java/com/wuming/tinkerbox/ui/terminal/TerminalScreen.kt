package com.wuming.tinkerbox.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuming.tinkerbox.core.terminal.EmulatorSession

/**
 * 内置终端（接入 termux terminal-emulator 仿真层）：模块 tab 的主界面。
 *
 * 顶部会话条（新建/切换/关闭）；中部为 emulator 渲染的彩色终端屏（VT100 解析/光标/颜色）；
 * 底部一行简洁的 Termux 风格提示符（`❯` + 等宽输入），回车即发送。
 *
 * 权限模型对齐 Termux：默认普通 shell；后续在此输入 `su` 走手动提权。
 */
@Composable
fun TerminalScreen() {
    val sessions = remember { mutableStateListOf<EmulatorSession>() }
    var activeIndex by remember { mutableStateOf(0) }
    var command by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (sessions.isEmpty()) {
            val s = EmulatorSession()
            s.spawn()
            sessions.add(s)
        }
    }

    fun newSession() {
        val s = EmulatorSession()
        s.spawn()
        sessions.add(s)
        activeIndex = sessions.lastIndex
    }

    fun closeSession(index: Int) {
        sessions[index].close()
        sessions.removeAt(index)
        if (sessions.isEmpty()) newSession() else if (activeIndex >= sessions.size) {
            activeIndex = sessions.lastIndex
        }
    }

    fun submit() {
        val active = sessions.getOrNull(activeIndex) ?: return
        if (command.isNotBlank()) {
            active.submit(command.trimEnd())
            command = ""
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .statusBarsPadding(),
    ) {
        // —— 顶部会话条 ——
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sessions.forEachIndexed { index, s ->
                TextButton(
                    onClick = { activeIndex = index },
                    enabled = index != activeIndex,
                ) {
                    Text(
                        text = "${s.session.sessionName} #$index",
                        color = if (index == activeIndex) Color(0xFF58A6FF) else Color(0xFF8B949E),
                    )
                }
                IconButton(onClick = { closeSession(index) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭会话 $index",
                        tint = Color(0xFF8B949E),
                    )
                }
            }
            IconButton(onClick = { newSession() }) {
                Icon(Icons.Filled.Add, contentDescription = "新建会话", tint = Color(0xFF58A6FF))
            }
        }

        // —— 当前会话终端屏：按实际可用尺寸换算虚拟列/行并渲染 ——
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val density = LocalDensity.current
            val cellW = with(density) { 8.dp.roundToPx() }
            val cellH = with(density) { 18.dp.roundToPx() }
            val pxW = with(density) { maxWidth.roundToPx() }
            val pxH = with(density) { maxHeight.roundToPx() }
            val cols = (pxW / cellW).coerceAtLeast(10)
            val rows = (pxH / cellH).coerceAtLeast(5)

            val active = sessions.getOrNull(activeIndex)
            LaunchedEffect(active, cols, rows, cellW, cellH) {
                active?.ensureEmulator(cols, rows, cellW, cellH)
            }
            if (active != null) {
                EmulatorView(active, Modifier.fillMaxSize())
            }
        }

        // —— 底部提示符输入行 ——
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D1117))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "❯",
                color = Color(0xFF58A6FF),
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    color = Color(0xFFE6EDF3),
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(Color(0xFF58A6FF)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
            )
        }
    }
}