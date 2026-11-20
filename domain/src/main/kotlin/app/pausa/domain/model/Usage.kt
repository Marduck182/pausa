package app.pausa.domain.model

import java.time.Instant

/**
 * One finished stretch of using a controlled app. It never spans two calendar days (it is split at midnight)
 * and holds only a package name and two instants: no screen content, nothing about what was done in the app.
 */
data class CompletedSession(
    val packageName: String,
    val start: Instant,
    val end: Instant,
)

/** What one app used on one day, as stored: completed sessions only. */
data class PackageUsage(
    val usedMillis: Long,
    val sessions: Int,
)
