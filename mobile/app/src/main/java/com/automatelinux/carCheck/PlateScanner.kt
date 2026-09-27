package com.automatelinux.carCheck

import android.content.Context
import android.net.Uri
import com.automatelinux.carCheck.data.OcrLine
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads the text in a picture on the phone itself, with ML Kit's bundled Latin
 * recogniser — a plate is Latin digits, and nothing leaves the device. Which of the
 * lines is the plate is decided in the shared [com.automatelinux.carCheck.data.PlateOcr].
 */
object PlateScanner {
    // One recogniser for the process: the first call warms the model up, later ones are instant.
    private val recognizer: TextRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(context: Context, uri: Uri): List<OcrLine> {
        // fromFilePath decodes off the content resolver and applies the EXIF rotation, so a
        // photo taken in portrait is read upright.
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        val text = recognizer.process(image).await()
        return text.textBlocks.flatMap { block ->
            block.lines.map { line -> OcrLine(line.text, line.boundingBox?.height()?.toFloat() ?: 0f) }
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
