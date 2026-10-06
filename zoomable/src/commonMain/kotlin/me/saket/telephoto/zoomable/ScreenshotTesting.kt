package me.saket.telephoto.zoomable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/** Whether telephoto is running inside a screenshot test or an IDE preview. */
@Composable
@ReadOnlyComposable
internal expect fun isInScreenshotTest(): Boolean
