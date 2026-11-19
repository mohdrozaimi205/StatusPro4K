package com.mohdrozaimi.statuspro4k.data

import android.content.Context
import java.io.File

data class RecentItem(val file: File, val sourceName: String, val time: Long)

/** Remembers the latest enhanced videos (files live in the app's private exports folder). */
object RecentStore {
    private const val PREF = "recent"
    private const val KEY = "items"
    private const val MAX = 20

    fun add(ctx: Context, file: File, sourceName: String) {
        val list = load(ctx).filter { it.file != file }.toMutableList()
        list.add(0, RecentItem(file, sourceName, System.currentTimeMillis()))
        val kept = list.take(MAX)
        // delete files that fall off the list to save storage
        list.drop(MAX).forEach { it.file.delete() }
        val raw = kept.joinToString("\n") { "${it.time}\t${it.sourceName.replace('\t', ' ').replace('\n', ' ')}\t${it.file.absolutePath}" }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
    }

    fun load(ctx: Context): List<RecentItem> {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return raw.lines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 3) return@mapNotNull null
            val f = File(p[2])
            if (!f.exists()) null else RecentItem(f, p[1], p[0].toLongOrNull() ?: 0L)
        }
    }

    fun remove(ctx: Context, item: RecentItem) {
        item.file.delete()
        val kept = load(ctx).filter { it.file != item.file }
        val raw = kept.joinToString("\n") { "${it.time}\t${it.sourceName}\t${it.file.absolutePath}" }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
    }
}
