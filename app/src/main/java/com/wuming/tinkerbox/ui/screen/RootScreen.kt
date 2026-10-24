package com.wuming.tinkerbox.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wuming.tinkerbox.core.mode.CapabilityState
import com.wuming.tinkerbox.core.mode.PermissionMode
import com.wuming.tinkerbox.core.mode.ShizukuManager
import com.wuming.tinkerbox.core.root.RootBackend

/**
 * 主界面骨架：只展示「当前模式」对应的权限状态，不做无关展示。
 *  - 普通模式：提示无需权限
 *  - Shizuku：  展示 Shizuku 连接状态
 *  - Root：     展示 Root 后端与版本，可刷新
 */
@Composable
fun MainHomeScreen(
    mode: PermissionMode,
    backend: RootBackend,
    capability: CapabilityState?,
    onRefreshRoot: () -> Unit,
    onShizukuGrantReceived: () -> Unit = {},
) {
    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = "TinkerBox",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = "玩机杂货铺",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ModeCard(mode = mode)

                when (mode) {
                    PermissionMode.NORMAL -> NormalStatusCard(capability)
                    PermissionMode.SHIZUKU -> ShizukuStatusCard(capability, onShizukuGrantReceived)
                    PermissionMode.ROOT -> RootBackendCard(backend, onRefreshRoot)
                }
            }
        }
    }
}

@Composable
private fun ModeCard(mode: PermissionMode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(PaddingValues(16.dp))) {
            Text(
                text = "当前模式",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = mode.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun NormalStatusCard(capability: CapabilityState?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(20.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = capability?.detail ?: "可正常使用",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "普通模式无需任何权限，可浏览只读监控与轻量调校功能。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ShizukuStatusCard(
    capability: CapabilityState?,
    onGrantReceived: () -> Unit,
) {
    var canGrant by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        canGrant = ShizukuManager.isAvailable() && !ShizukuManager.checkPermission()
    }
    DisposableEffect(Unit) {
        val listener = object : rikka.shizuku.Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode == ShizukuManager.REQUEST_CODE) {
                    onGrantReceived()
                }
            }
        }
        ShizukuManager.addRequestPermissionListener(listener)
        onDispose { ShizukuManager.removeRequestPermissionListener(listener) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(20.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Shizuku 状态",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = capability?.detail ?: "检测中",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            if (capability?.available == false) {
                Text(
                    text = capability.detail.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            if (canGrant) {
                Button(
                    onClick = { ShizukuManager.requestPermission() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("前往授权")
                }
            } else if (!ShizukuManager.isAvailable()) {
                Text(
                    text = "请先在 Shizuku App 中启动服务",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RootBackendCard(backend: RootBackend, onRefreshRoot: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(20.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Root 后端",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = backend.displayName,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            backend.version?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = onRefreshRoot,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text("重新检测 Root")
            }
        }
    }
}