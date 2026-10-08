package com.automatelinux.carCheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.carCheck.data.Recall
import com.automatelinux.carCheck.data.RegistrationStatus
import com.automatelinux.carCheck.data.VehicleKind
import com.automatelinux.carCheck.data.VehicleReport
import com.automatelinux.carCheck.data.colorSwatch
import com.automatelinux.carCheck.data.formatDate
import com.automatelinux.carCheck.data.formatDouble
import com.automatelinux.carCheck.data.formatInt
import com.automatelinux.carCheck.data.formatUtcDateTime
import com.automatelinux.carCheck.data.isoDateOrNull
import com.automatelinux.carCheck.data.today
import com.automatelinux.carCheck.ui.components.PlateView
import com.automatelinux.carCheck.ui.theme.LocalPalette
import com.automatelinux.carCheck.ui.theme.Palette
import kotlinx.datetime.DatePeriod
import kotlinx.coroutines.launch
import kotlinx.datetime.plus

private enum class Tone { Good, Warn, Bad, Neutral }

@Composable
fun ReportScreen(report: VehicleReport, host: HostActions, onBack: () -> Unit) {
    val p = LocalPalette.current
    // The shared picture is the plate, the top of the report down to its history, and the source line —
    // the facts a buyer asks about, short enough that a chat app does not shrink it past reading.
    val plateLayer = rememberGraphicsLayer()
    val summaryLayer = rememberGraphicsLayer()
    val footerLayer = rememberGraphicsLayer()
    val pictureLayer = rememberGraphicsLayer()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val share: () -> Unit = {
        scope.launch {
            val image = stackShareImage(
                into = pictureLayer,
                plate = plateLayer,
                parts = listOf(summaryLayer, footerLayer),
                background = p.page,
                density = density,
                paddingPx = with(density) { 16.dp.toPx() },
            )
            host.shareImage(image, report.plate.digits, report.shareText())
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopBar(report, plateLayer, onBack = onBack, onShare = share)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Column(Modifier.fillMaxWidth().recordInto(summaryLayer)) {
                Headline(report)
                if (report.mainRegistryRefreshing) RefreshingBanner(report)
                QuickStats(report)
                for (r in report.recalls) RecallCard(r, host)
                Licensing(report, host)
                History(report)
            }
            Engine(report)
            Body(report)
            Safety(report)
            Emissions(report)
            Money(report)
            Box(Modifier.fillMaxWidth().recordInto(footerLayer)) { Footer(report) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TopBar(report: VehicleReport, plateLayer: GraphicsLayer, onBack: () -> Unit, onShare: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה", tint = p.ink)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(Modifier.recordInto(plateLayer)) { PlateView(report.plate.display, height = 40.dp) }
        }
        IconButton(onClick = onShare, modifier = Modifier.testTag("share-report")) {
            Icon(Icons.Filled.Share, contentDescription = "שיתוף", tint = p.ink)
        }
    }
}

// ---------------------------------------------------------------- headline

@Composable
private fun Headline(report: VehicleReport) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp)) {
        Text(report.title, style = MaterialTheme.typography.headlineMedium, color = p.ink)
        val sub = buildList {
            report.year?.let { add(it.toString()) }
            report.trim?.let { add(it) }
            report.bodyType?.let { add(it) }
            report.fuel?.let { add(it) }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            report.color?.let { c ->
                colorSwatch(c)?.let { argb ->
                    Box(
                        Modifier.size(14.dp).background(Color(argb), CircleShape)
                            .then(if (p.isNight) Modifier else Modifier.border(1.dp, p.hairline, CircleShape)),
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(c, style = MaterialTheme.typography.bodyLarge, color = p.inkDim)
                if (sub.isNotEmpty()) Text("  ·  ", style = MaterialTheme.typography.bodyLarge, color = p.inkDim)
            }
            Text(sub.joinToString("  ·  "), style = MaterialTheme.typography.bodyLarge, color = p.inkDim)
        }
        StatusRow(report)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusRow(report: VehicleReport) {
    val p = LocalPalette.current
    val (statusText, tone) = statusOf(report)
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill(statusText, tone, strong = true)
        report.ownership?.let { Pill("בעלות $it", Tone.Neutral) }
        when (report.kind) {
            VehicleKind.Motorcycle -> Pill("דו-גלגלי", Tone.Neutral)
            VehicleKind.Heavy -> Pill("רכב כבד", Tone.Neutral)
            VehicleKind.Public -> Pill("רכב ציבורי", Tone.Neutral)
            VehicleKind.PersonalImport -> Pill(report.importType ?: "יבוא אישי", Tone.Warn)
            VehicleKind.Car -> Unit
        }
        if (report.disabledTagSince != null) Pill("תג נכה", Tone.Neutral)
        if (report.recalls.isNotEmpty()) Pill("ריקול פתוח", Tone.Bad, strong = true)
        if (report.structuralChange == true) Pill("שינוי מבנה", Tone.Warn)
        if (report.gasConversion == true) Pill("הסבה לגז", Tone.Warn)
    }
}

private fun statusOf(report: VehicleReport): Pair<String, Tone> = when (val s = report.status) {
    is RegistrationStatus.Active -> {
        val until = isoDateOrNull(s.validUntil)
        val shown = formatDate(s.validUntil)
        when {
            until == null -> "רשום ופעיל" to Tone.Good
            until < today() -> "הטסט פג ב-$shown" to Tone.Bad
            until < today().plus(DatePeriod(days = 30)) -> "הטסט פג בקרוב · $shown" to Tone.Warn
            else -> "טסט בתוקף עד $shown" to Tone.Good
        }
    }
    is RegistrationStatus.OffRoad -> ("ירד מהכביש" + (formatDate(s.date)?.let { " · $it" } ?: "")) to Tone.Bad
    RegistrationStatus.Inactive -> "לא פעיל · הרישיון לא חודש" to Tone.Warn
    RegistrationStatus.Unknown -> "מצב הרישוי לא ידוע כרגע" to Tone.Warn
}

/** The main file is mid-reload: say what this report is built from and what it cannot say yet. */
@Composable
private fun RefreshingBanner(report: VehicleReport) {
    val p = LocalPalette.current
    val since = formatUtcDateTime(report.dataAsOf)?.let { " (מאז $it)" } ?: ""
    val fromCopy = report.mirrorAsOf != null
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(p.warnBg)
            .border(1.dp, p.warn.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(
            if (fromCopy) "המאגר הראשי מתעדכן — הדוח מהעותק השמור" else "דוח חלקי — המאגר הראשי מתעדכן",
            style = MaterialTheme.typography.titleMedium,
            color = p.warn,
        )
        val body = if (fromCopy) {
            "משרד התחבורה מחליף עכשיו את קובץ הרישוי הראשי ב-data.gov.il$since, וזה לוקח כמה שעות. " +
                "הדוח מבוסס על העותק ששמרנו מהקובץ הקודם, שהועלה ב-${formatUtcDateTime(report.mirrorAsOf) ?: "?"}. " +
                "טסט או בעלות שהשתנו מאז יופיעו כשההחלפה תסתיים."
        } else {
            "משרד התחבורה מחליף עכשיו את קובץ הרישוי הראשי ב-data.gov.il$since, וזה לוקח כמה שעות. " +
                "מה שכאן מגיע מהקבצים הנלווים: הדגם והמפרט שלו, ק״מ בטסט האחרון, גרירה וצמיגים, ריקולים ותג נכה. " +
                "עד שההחלפה תסתיים חסרים תוקף הטסט, שנת הייצור, הצבע, הבעלות ומספר השלדה." +
                (report.mirrorNote?.let { " העותק השמור לא עזר הפעם: $it." } ?: "")
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = p.ink, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Pill(text: String, tone: Tone, strong: Boolean = false) {
    val p = LocalPalette.current
    val (bg, fg) = when (tone) {
        Tone.Good -> p.goodBg to p.good
        Tone.Warn -> p.warnBg to p.warn
        Tone.Bad -> p.badBg to p.bad
        Tone.Neutral -> p.panelAlt to p.ink
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .then(if (strong) Modifier.border(1.dp, fg.copy(alpha = 0.35f), RoundedCornerShape(999.dp)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

// ---------------------------------------------------------------- quick stats

@Composable
private fun QuickStats(report: VehicleReport) {
    val tiles = buildList {
        report.kmAtLastTest?.let { add("ק״מ בטסט האחרון" to formatInt(it)) }
        if (report.ownershipHistory.size > 1) add("ידיים" to report.ownershipHistory.size.toString())
        report.horsepower?.let { add("כוח סוס" to it.toString()) }
        if (size < 3) report.listPriceNis?.let { add("מחירון בחדש, ₪" to formatInt(it)) }
        if (size < 3) report.displacementCc?.let { add("נפח מנוע, סמ״ק" to formatInt(it)) }
        if (size < 3) report.towBrakedKg?.let { add("גרירה, ק״ג" to formatInt(it)) }
        if (size < 3) report.grossWeightKg?.let { add("משקל כולל, ק״ג" to formatInt(it)) }
    }.take(3)
    // A lone tile stretched across the screen reads as a mistake; two or three read as a dashboard.
    if (tiles.size < 2) return
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for ((label, value) in tiles) StatTile(label, value)
    }
}

@Composable
private fun RowScope.StatTile(label: String, value: String) {
    val p = LocalPalette.current
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(p.panel)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, fontFeatureSettings = "tnum"),
            color = p.ink,
            maxLines = 1,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = p.inkDim, modifier = Modifier.padding(top = 4.dp))
    }
}

// ---------------------------------------------------------------- recall

@Composable
private fun RecallCard(r: Recall, host: HostActions) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(p.badBg)
            .border(1.dp, p.bad.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text("ריקול שלא בוצע", style = MaterialTheme.typography.titleMedium, color = p.bad)
        r.type?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = p.bad, modifier = Modifier.padding(top = 2.dp)) }
        r.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = p.ink, modifier = Modifier.padding(top = 8.dp)) }
        val meta = listOfNotNull(
            r.category?.let { "תחום: $it" },
            r.fix?.let { "תיקון: $it" },
            formatDate(r.opened)?.let { "נפתח: $it" },
        )
        if (meta.isNotEmpty()) {
            Text(meta.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = p.inkDim, modifier = Modifier.padding(top = 8.dp))
        }
        r.importer?.let {
            Text("היבואן: $it", style = MaterialTheme.typography.bodyMedium, color = p.ink, modifier = Modifier.padding(top = 8.dp))
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            r.phone?.let { ActionChip("התקשר $it", Icons.Filled.Call) { host.dial(it) } }
            r.website?.let { ActionChip("אתר היבואן", Icons.Filled.OpenInNew) { host.openUrl(it) } }
        }
    }
}

@Composable
private fun ActionChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(p.panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = p.link, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = p.link)
    }
}

// ---------------------------------------------------------------- sections

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = p.ink, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.panel).padding(horizontal = 16.dp, vertical = 4.dp),
        ) { content() }
    }
}

/** One label→value line. `mono` for identifiers you would copy off the screen. */
@Composable
private fun Fact(label: String, value: String?, mono: Boolean = false, onCopy: (() -> Unit)? = null) {
    if (value.isNullOrBlank()) return
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = p.inkDim, modifier = Modifier.width(126.dp))
        Text(
            value,
            style = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            else MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = p.ink,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
        )
        if (onCopy != null) {
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "העתק", tint = p.inkDim, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalPalette.current.hairline))
}

private fun yesNo(b: Boolean?): String? = when (b) { true -> "כן"; false -> "לא"; null -> null }

@Composable
private fun Licensing(report: VehicleReport, host: HostActions) {
    Section("רישוי") {
        Fact("טסט אחרון", formatDate(report.lastTest))
        Fact("תוקף הרישיון", formatDate((report.status as? RegistrationStatus.Active)?.validUntil))
        Fact("עלייה לכביש", formatDate(report.onRoad))
        Fact("רישום ראשון", formatDate(report.firstRegistration))
        Fact("סוג רכב", report.vehicleType)
        Fact("קטגוריה", report.euCategory)
        Fact("תג נכה", formatDate(report.disabledTagSince)?.let { "מאז $it" })
        Fact("מספר שלדה", report.chassis, mono = true, onCopy = report.chassis?.let { { host.copy("מספר שלדה", it) } })
        Fact("מספר מנוע", report.engineNumber, mono = true, onCopy = report.engineNumber?.let { { host.copy("מספר מנוע", it) } })
        Fact("מקוריות", report.originality)
    }
}

@Composable
private fun History(report: VehicleReport) {
    val p = LocalPalette.current
    val hasFlags = listOf(report.structuralChange, report.gasConversion, report.colorChange, report.tyreChange).any { it != null }
    if (report.ownershipHistory.isEmpty() && !hasFlags && report.kmAtLastTest == null) return
    Section("היסטוריה") {
        Fact("ק״מ בטסט האחרון", report.kmAtLastTest?.let { formatInt(it) })
        if (hasFlags) {
            val changes = listOfNotNull(
                "שינוי מבנה".takeIf { report.structuralChange == true },
                "הסבה לגז".takeIf { report.gasConversion == true },
                "שינוי צבע".takeIf { report.colorChange == true },
                "שינוי צמיגים".takeIf { report.tyreChange == true },
            )
            Fact("שינויים רשומים", if (changes.isEmpty()) "אין" else changes.joinToString(", "))
        }
        if (report.ownershipHistory.isNotEmpty()) {
            Hairline()
            Column(Modifier.padding(vertical = 10.dp)) {
                Text(
                    "${report.ownershipHistory.size} בעלויות רשומות",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.inkDim,
                )
                for ((i, o) in report.ownershipHistory.withIndex()) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(if (i == report.ownershipHistory.lastIndex) p.accent else p.hairline, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            formatDate(o.yearMonth) ?: o.yearMonth,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            color = p.inkDim,
                            modifier = Modifier.width(64.dp),
                        )
                        Text(o.ownership, style = MaterialTheme.typography.bodyLarge, color = p.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun Engine(report: VehicleReport) {
    val any = listOf(report.fuel, report.engineModel, report.displacementCc, report.horsepower, report.drive, report.automatic,
        report.driveTechnology, report.grossWeightKg, report.towBrakedKg, report.towHitch).any { it != null }
    if (!any) return
    Section("מנוע והנעה") {
        Fact("דלק", report.fuel)
        Fact("נפח מנוע", report.displacementCc?.let { "${formatInt(it)} סמ״ק" })
        Fact("כוח סוס", report.horsepower?.toString())
        Fact("דגם מנוע", report.engineModel, mono = true)
        Fact("הנעה", report.drive)
        Fact("תיבת הילוכים", report.automatic?.let { if (it) "אוטומטית" else "ידנית" })
        Fact("טכנולוגיה", report.driveTechnology?.takeUnless { it == "הנעה רגילה" })
        Fact("משקל כולל", report.grossWeightKg?.let { "${formatInt(it)} ק״ג" })
        Fact("משקל עצמי", report.curbWeightKg?.let { "${formatInt(it)} ק״ג" })
        Fact("וו גרירה", yesNo(report.towHitch))
        Fact("כושר גרירה", listOfNotNull(
            report.towBrakedKg?.let { "${formatInt(it)} ק״ג עם בלמים" },
            report.towUnbrakedKg?.let { "${formatInt(it)} בלי" },
        ).joinToString(" · ").ifEmpty { null })
    }
}

@Composable
private fun Body(report: VehicleReport) {
    val any = listOf(report.doors, report.seats, report.tyreFront, report.model).any { it != null }
    if (!any) return
    Section("מרכב") {
        Fact("דגם", report.model, mono = true)
        Fact("כינוי מסחרי", report.commercialName)
        Fact("רמת גימור", report.trim)
        Fact("מרכב", report.bodyType)
        Fact("דלתות", report.doors?.toString())
        Fact("מושבים", report.seats?.toString())
        Fact("צמיגים קדמיים", report.tyreFront)
        Fact("צמיגים אחוריים", report.tyreRear?.takeUnless { it == report.tyreFront })
        Fact("קוד עומס/מהירות", listOfNotNull(report.tyreLoadCode, report.tyreSpeedCode).joinToString(" ").ifEmpty { null })
        Fact("ארץ ייצור", report.makeCountry)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Safety(report: VehicleReport) {
    val p = LocalPalette.current
    if (report.safetyLevel == null && report.safetyScore == null && report.airbags == null && report.safetyFeatures.isEmpty()) return
    Section("בטיחות") {
        Fact("רמת אבזור בטיחותי", report.safetyLevel?.let { "$it מתוך 8" })
        Fact("ניקוד בטיחות", report.safetyScore?.let { formatDouble(it) })
        Fact("כריות אוויר", report.airbags?.toString())
        if (report.safetyFeatures.isNotEmpty()) {
            Hairline()
            FlowRow(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (f in report.safetyFeatures) {
                    Text(
                        f.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = p.ink,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(p.panelAlt).padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Emissions(report: VehicleReport) {
    val any = listOf(report.pollutionGroup, report.greenIndex, report.co2Wltp, report.particleFilterFitted).any { it != null }
    if (!any) return
    Section("זיהום") {
        Fact("קבוצת זיהום", report.pollutionGroup?.let { "$it מתוך 15" })
        Fact("מדד ירוק", report.greenIndex?.let { formatDouble(it) })
        Fact("CO₂ (WLTP)", report.co2Wltp?.let { "${formatDouble(it)} גר׳/ק״מ" })
        Fact("NOx", report.noxWltp?.let { "${formatDouble(it)} מ״ג/ק״מ" })
        Fact("חלקיקים", report.pmWltp?.let { "${formatDouble(it)} מ״ג/ק״מ" })
        Fact("מסנן חלקיקים", formatDate(report.particleFilterFitted)?.let { "הותקן $it" })
    }
}

@Composable
private fun Money(report: VehicleReport) {
    if (report.listPriceNis == null) return
    Section("מחיר") {
        Fact("מחירון בחדש", "₪${formatInt(report.listPriceNis)}")
        Fact("יבואן", report.importer)
        Fact("שנת המחירון", report.year?.toString())
    }
}

@Composable
private fun Footer(report: VehicleReport) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 22.dp, start = 4.dp, end = 4.dp)) {
        if (report.unavailable.isNotEmpty()) {
            Text(
                "לא התקבלה תשובה הפעם עבור: ${report.unavailable.joinToString(", ")}. נסה שוב מאוחר יותר.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.warn,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Text(
            "מקור: משרד התחבורה, data.gov.il" + (formatDate(report.mirrorAsOf ?: report.dataAsOf)?.let { " · נכון ל-$it" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = p.inkDim,
        )
    }
}
