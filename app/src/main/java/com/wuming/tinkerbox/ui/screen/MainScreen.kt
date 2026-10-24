package com.wuming.tinkerbox.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wuming.tinkerbox.ui.home.HomeScreen

/**
 * 入口总控：
 *  1. loading 中 -> 进度圈
 *  2. 未选择模式（首次 或 上次权限已失效回退）-> 模式引导页
 *  3. 已选择     -> 主界面（当前为模式状态骨架）
 */
@Composable
fun MainScreen(
    viewModel: ModeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.mode == null -> {
            ModeSelectionScreen(
                capabilities = state.capabilities,
                onSelect = viewModel::selectMode,
            )
        }
        else -> {
            HomeScreen(
                mode = state.mode!!,
                modeViewModel = viewModel,
            )
        }
    }
}