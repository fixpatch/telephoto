package me.saket.telephoto.zoomable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalInspectionMode

@Composable
@ReadOnlyComposable
internal actual fun isInScreenshotTest(): Boolean = LocalInspectionMode.current
