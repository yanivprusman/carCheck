package com.automatelinux.carCheck.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.automatelinux.carCheck.data.CarPhoto
import com.automatelinux.carCheck.data.CarPhotos
import com.automatelinux.carCheck.data.CarPhotosResult
import com.automatelinux.carCheck.data.VehicleReport
import com.automatelinux.carCheck.data.httpGetBytes
import com.automatelinux.carCheck.ui.theme.LocalPalette
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private val TILE_W = 220.dp
private val TILE_H = 148.dp

/** A thumbnail either arrived as a bitmap or failed, and the tile says which. */
private sealed class Thumb {
    data class Ready(val image: ImageBitmap) : Thumb()
    data object Failed : Thumb()
}

/**
 * A strip of photos of the model under the headline. They are labelled as the model's, from
 * Wikipedia — the registry has no photo of the car itself, and a strip that let the reader think
 * otherwise would be lying. No matching article ⇒ no strip at all.
 */
@Composable
fun CarPhotoStrip(report: VehicleReport, host: HostActions) {
    val p = LocalPalette.current
    val result by produceState<CarPhotosResult?>(null, report.plate) {
        value = CarPhotos.find(report.make, report.commercialName, report.year)
    }
    when (val r = result) {
        null -> PlaceholderRow()
        CarPhotosResult.None -> Unit
        is CarPhotosResult.Failed -> Text(
            "לא ניתן לטעון תמונות של הדגם (${r.detail})",
            style = MaterialTheme.typography.bodySmall,
            color = p.inkDim,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp),
        )
        is CarPhotosResult.Found -> {
            val thumbs = remember(r) { mutableStateMapOf<String, Thumb>() }
            LaunchedEffect(r) {
                coroutineScope {
                    r.set.photos.map { photo ->
                        async {
                            val got = httpGetBytes(photo.thumbUrl)
                            val image = if (got.code in 200..299) decodeImage(got.bytes) else null
                            thumbs[photo.thumbUrl] = image?.let { Thumb.Ready(it) } ?: Thumb.Failed
                        }
                    }.awaitAll()
                }
            }
            Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    itemsIndexed(r.set.photos, key = { _, ph -> ph.thumbUrl }) { i, photo ->
                        PhotoTile(photo, thumbs[photo.thumbUrl], i) { host.openUrl(photo.pageUrl) }
                    }
                }
                Row(
                    Modifier
                        .padding(top = 8.dp, start = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { host.openUrl(r.set.articleUrl) }
                        .testTag("car-photos-source")
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "תמונות של הדגם, לא של הרכב הזה · ויקיפדיה",
                        style = MaterialTheme.typography.bodySmall,
                        color = p.inkDim,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.OpenInNew, contentDescription = "פתיחת הערך בוויקיפדיה", tint = p.inkDim, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

@Composable
private fun PhotoTile(photo: CarPhoto, thumb: Thumb?, index: Int, onOpen: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(TILE_W, TILE_H)
            .clip(RoundedCornerShape(14.dp))
            .background(p.panelAlt)
            .clickable(onClick = onOpen)
            .testTag("car-photo-$index"),
        contentAlignment = Alignment.Center,
    ) {
        when (thumb) {
            null -> Unit
            is Thumb.Ready -> Image(
                bitmap = thumb.image,
                contentDescription = photo.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Thumb.Failed -> Icon(Icons.Filled.BrokenImage, contentDescription = "התמונה לא נטענה", tint = p.inkDim)
        }
    }
}

@Composable
private fun PlaceholderRow() {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(2) {
            Box(Modifier.size(TILE_W, TILE_H).clip(RoundedCornerShape(14.dp)).background(p.panelAlt))
        }
    }
}
