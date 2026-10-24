package com.wuming.tinkerbox.core.apps

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** 图标目标尺寸（像素）。 */
private const val ICON_SIZE = 44

/**
 * 把 Android [android.graphics.drawable.Drawable] 绘制为固定尺寸的 [ImageBitmap]，
 * 供监控台进程列表展示应用图标。绘制在共享缓冲上，避免超大位图内存抖动。
 */
object ProcessIcon {
    fun toImageBitmap(drawable: android.graphics.drawable.Drawable): ImageBitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, ICON_SIZE, ICON_SIZE)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}