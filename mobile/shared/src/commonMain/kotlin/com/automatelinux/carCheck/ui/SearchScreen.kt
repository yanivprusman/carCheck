package com.automatelinux.carCheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.automatelinux.carCheck.data.Plate
import com.automatelinux.carCheck.data.PlateCandidate
import com.automatelinux.carCheck.data.RecentEntry
import com.automatelinux.carCheck.data.colorSwatch
import com.automatelinux.carCheck.ui.components.PlateInput
import com.automatelinux.carCheck.ui.components.PlateView
import com.automatelinux.carCheck.ui.theme.LocalPalette

@Composable
fun SearchScreen(
    state: CarCheckModel.State,
    onInput: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onScan: (ImageSource) -> Unit,
    onPickPlate: (String) -> Unit,
) {
    val p = LocalPalette.current
    val working = state.busy || state.reading
    val canSearch = state.input.length >= Plate.MIN_DIGITS && !working
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp)) {
                Text("בדיקת רכב", style = MaterialTheme.typography.headlineMedium, color = p.ink)
                Text(
                    "כל מה שמשרד התחבורה יודע על רכב, לפי מספר הרישוי",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.inkDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PlateInput(
                        digits = state.input,
                        onDigitsChange = onInput,
                        onSearch = { if (canSearch) onSearch() },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth(),
                        height = 84.dp,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onSearch,
                    enabled = canSearch,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = p.accent,
                        contentColor = p.onAccent,
                        disabledContainerColor = p.accent.copy(alpha = 0.35f),
                        disabledContentColor = p.onAccent.copy(alpha = 0.8f),
                    ),
                ) {
                    if (working) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = p.onAccent, strokeWidth = 2.5.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (state.reading) "קורא את המספר מהתמונה…" else "שואל את משרד התחבורה…",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = null, Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("בדוק רכב", style = MaterialTheme.typography.titleMedium)
                    }
                }
                ScanRow(enabled = !working, onScan = onScan, modifier = Modifier.padding(top = 10.dp))
                if (state.imagePlates.isNotEmpty()) {
                    ImagePlatesCard(state.imagePlates, chosen = state.input, onPick = onPickPlate, modifier = Modifier.padding(top = 14.dp))
                }
                state.notice?.let { NoticeCard(it, Modifier.padding(top = 14.dp)) }
            }
        }
        if (state.recents.isEmpty()) {
            item { EmptyHint(Modifier.padding(top = 36.dp)) }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("בדיקות אחרונות", style = MaterialTheme.typography.titleMedium, color = p.ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearRecents) { Text("נקה", color = p.inkDim) }
                }
            }
            items(state.recents, key = { it.digits }) { entry ->
                RecentRow(entry, onOpen = { onOpenRecent(entry.digits) }, onRemove = { onRemoveRecent(entry.digits) })
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun NoticeCard(notice: CarCheckModel.Notice, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val (title, body, bg, fg) = when (notice) {
        CarCheckModel.Notice.TooShort -> Quad("מספר קצר מדי", "מספר רישוי הוא בדרך כלל 7 או 8 ספרות. הקלד לפחות שתיים.", p.warnBg, p.warn)
        CarCheckModel.Notice.NotFound -> Quad(
            "לא נמצא רכב עם המספר הזה",
            "בדוק שהמספר מלא ונכון. רכבים שאינם במאגר: לפני 1996, צבאיים, דיפלומטיים, ורכבים שהוסרו לפני 2000.",
            p.panelAlt, p.ink,
        )
        CarCheckModel.Notice.Refreshing -> Quad(
            "המאגר מתעדכן כרגע",
            "משרד התחבורה מחליף עכשיו את קובץ הרישוי הראשי ב-data.gov.il, ובזמן הזה הוא ריק. זה לוקח כמה שעות. נסה שוב מאוחר יותר.",
            p.warnBg, p.warn,
        )
        CarCheckModel.Notice.Offline -> Quad("אין חיבור לאינטרנט", "הבדיקה שואלת את data.gov.il ישירות, וצריך רשת בשביל זה.", p.badBg, p.bad)
        is CarCheckModel.Notice.Failed -> Quad("data.gov.il לא ענה", "השרת של הממשלה החזיר שגיאה (${notice.detail}). נסה שוב בעוד רגע.", p.badBg, p.bad)
        CarCheckModel.Notice.NoPlateInImage -> Quad(
            "לא זוהה מספר רישוי בתמונה",
            "צריך שהלוחית תהיה בתוך התמונה, ישרה וקריאה. אפשר לנסות תמונה אחרת, או פשוט להקליד את המספר.",
            p.warnBg, p.warn,
        )
        is CarCheckModel.Notice.ImageFailed -> Quad("לא הצלחתי לפתוח את התמונה", "הטלפון החזיר שגיאה (${notice.detail}).", p.badBg, p.bad)
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = fg)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = p.ink, modifier = Modifier.padding(top = 4.dp))
    }
}

private data class Quad(val a: String, val b: String, val c: Color, val d: Color)

/** Camera and gallery: the plate is read off the picture, so a listing's photo is enough. */
@Composable
private fun ScanRow(enabled: Boolean, onScan: (ImageSource) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScanButton("צלם לוחית", Icons.Filled.PhotoCamera, enabled, Modifier.weight(1f).testTag("scan-camera")) { onScan(ImageSource.Camera) }
            ScanButton("מתמונה", Icons.Filled.PhotoLibrary, enabled, Modifier.weight(1f).testTag("scan-gallery")) { onScan(ImageSource.Gallery) }
        }
        Text(
            "אפשר גם לשתף תמונה של רכב מכל אפליקציה אל בדיקת רכב",
            style = MaterialTheme.typography.bodySmall,
            color = p.inkDim,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        )
    }
}

@Composable
private fun ScanButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (enabled) p.hairline else p.hairline.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = p.panel,
            contentColor = p.ink,
            disabledContainerColor = p.panel.copy(alpha = 0.6f),
            disabledContentColor = p.inkDim.copy(alpha = 0.6f),
        ),
    ) {
        Icon(icon, contentDescription = null, Modifier.size(20.dp), tint = if (enabled) p.accent else p.inkDim.copy(alpha = 0.6f))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Every plate the picture held, nearest first; the one being looked up is outlined. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImagePlatesCard(plates: List<PlateCandidate>, chosen: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.panel)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("בתמונה יש כמה מספרים", style = MaterialTheme.typography.titleMedium, color = p.ink)
        Text(
            "הגדול ביותר הוא בדרך כלל הרכב שצולם. הקש על מספר כדי לבדוק אותו.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.inkDim,
            modifier = Modifier.padding(top = 2.dp),
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (c in plates) {
                val picked = c.digits == chosen
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (picked) Modifier.border(2.dp, p.accent, RoundedCornerShape(8.dp)) else Modifier)
                        .clickable { onPick(c.digits) }
                        .padding(3.dp)
                        .testTag("image-plate-${c.digits}"),
                ) {
                    PlateView(Plate.format(c.digits), height = 34.dp)
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth()) {
        Text("מה תקבל", style = MaterialTheme.typography.titleMedium, color = p.ink)
        val lines = listOf(
            "יצרן, דגם, שנה, צבע ורמת גימור",
            "תוקף הטסט, בעלות, וכמה ידיים עברו",
            "ק״מ בטסט האחרון ושינויי מבנה או צבע",
            "ריקולים פתוחים שלא בוצעו",
            "מנוע, כוח סוס, כושר גרירה, וו גרירה",
            "מערכות בטיחות, כריות אוויר, קבוצת זיהום",
            "מחיר המחירון כשהיה חדש",
        )
        for (l in lines) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(p.accent, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(l, style = MaterialTheme.typography.bodyLarge, color = p.inkDim)
            }
        }
    }
}

@Composable
private fun RecentRow(entry: RecentEntry, onOpen: () -> Unit, onRemove: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.panel)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlateView(Plate.format(entry.digits), height = 30.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, color = p.ink, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                colorSwatch(entry.color)?.let { argb ->
                    Box(
                        Modifier.size(10.dp).background(Color(argb), CircleShape)
                            .then(if (p.isNight) Modifier else Modifier.border(1.dp, p.hairline, CircleShape)),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(entry.subtitle, style = MaterialTheme.typography.bodyMedium, color = p.inkDim, maxLines = 1)
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "הסר", tint = p.inkDim, modifier = Modifier.size(18.dp))
        }
    }
}
