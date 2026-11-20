package app.pausa.enforcement

import android.util.Log
import app.pausa.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Debug-only, in-memory log for the technical lab. It is never written to disk and disappears when the process
 * ends, so nothing observed during a test can outlive it.
 */
object LabLog {
    data class Entry(val timeMillis: Long, val text: String)

    private const val MAX_ENTRIES = 600

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    fun add(text: String) = addBlock(listOf(text))

    /** Adds [lines] keeping their order, as one block on top of the older ones. */
    fun addBlock(lines: List<String>) {
        // The lab sees which apps are opened, so it exists in debug builds only: release builds neither log nor
        // keep anything in memory.
        if (!BuildConfig.DEBUG) return
        lines.forEach { Log.d("PausaLab", it) }
        val now = System.currentTimeMillis()
        _entries.update { old -> (lines.map { Entry(now, it) } + old).take(MAX_ENTRIES) }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}

/** Packages the lab is asked to block, to test the real block flow end to end. */
object LabConfig {
    val blockedPackages = MutableStateFlow<Set<String>>(emptySet())

    const val YOUTUBE = "com.google.android.youtube"
    const val INSTAGRAM = "com.instagram.android"

    fun toggle(packageName: String) {
        blockedPackages.update { if (packageName in it) it - packageName else it + packageName }
    }
}
