package com.automatelinux.carCheck.ui

import com.automatelinux.carCheck.data.RegistrationStatus
import com.automatelinux.carCheck.data.VehicleReport
import com.automatelinux.carCheck.data.formatDate
import com.automatelinux.carCheck.data.formatInt

/** The report as a WhatsApp-able paragraph: the headline facts, nothing that needs a table. */
fun VehicleReport.shareText(): String {
    val sb = StringBuilder()
    sb.append("רכב ").append(plate.display).append('\n')
    sb.append(title)
    if (subtitle.isNotBlank()) sb.append(" · ").append(subtitle)
    sb.append('\n')
    when (val s = status) {
        is RegistrationStatus.Active -> formatDate(s.validUntil)?.let { sb.append("טסט בתוקף עד ").append(it).append('\n') }
        is RegistrationStatus.OffRoad -> sb.append("ירד מהכביש").append(formatDate(s.date)?.let { " ב-$it" } ?: "").append('\n')
        RegistrationStatus.Inactive -> sb.append("לא פעיל — הרישיון לא חודש\n")
    }
    ownership?.let { sb.append("בעלות: ").append(it).append('\n') }
    if (ownershipHistory.isNotEmpty()) sb.append("ידיים: ").append(ownershipHistory.size).append('\n')
    kmAtLastTest?.let { sb.append("ק״מ בטסט האחרון: ").append(formatInt(it)).append('\n') }
    horsepower?.let { sb.append("כוח סוס: ").append(it).append('\n') }
    if (recalls.isNotEmpty()) sb.append("⚠️ ריקול פתוח: ").append(recalls.size).append('\n')
    if (structuralChange == true) sb.append("⚠️ שינוי מבנה רשום\n")
    listPriceNis?.let { sb.append("מחיר מחירון בחדש: ₪").append(formatInt(it)).append('\n') }
    sb.append("\nמקור: משרד התחבורה, data.gov.il")
    return sb.toString()
}
