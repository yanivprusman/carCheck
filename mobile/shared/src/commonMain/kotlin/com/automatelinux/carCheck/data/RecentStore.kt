package com.automatelinux.carCheck.data

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A plate looked up before, with enough of its answer to show in a list without asking again. */
@Serializable
data class RecentEntry(
    val digits: String,
    val title: String,
    val subtitle: String,
    val color: String? = null,
    val at: Long,
)

/** Recent lookups, newest first, in the platform's key-value store. */
class RecentStore(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<RecentEntry> {
        val raw = settings.getStringOrNull(KEY) ?: return emptyList()
        return try { json.decodeFromString(ListSerializer(RecentEntry.serializer()), raw) } catch (_: Exception) { emptyList() }
    }

    fun remember(entry: RecentEntry): List<RecentEntry> {
        val next = (listOf(entry) + load().filter { it.digits != entry.digits }).take(MAX)
        save(next)
        return next
    }

    fun remove(digits: String): List<RecentEntry> {
        val next = load().filter { it.digits != digits }
        save(next)
        return next
    }

    fun clear(): List<RecentEntry> {
        settings.remove(KEY)
        return emptyList()
    }

    private fun save(list: List<RecentEntry>) {
        settings.putString(KEY, json.encodeToString(ListSerializer(RecentEntry.serializer()), list))
    }

    private companion object {
        const val KEY = "recent_plates_v1"
        const val MAX = 30
    }
}
