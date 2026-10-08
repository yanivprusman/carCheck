package com.automatelinux.carCheck.ui

import androidx.compose.ui.graphics.ImageBitmap

/** Encoded image bytes (JPEG/PNG/WebP) to a bitmap; null when they do not decode. */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?
