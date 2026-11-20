package app.pausa.domain.model

/** An installed app the user can choose to control. Only its package and visible name are ever read. */
data class InstalledApp(
    val packageName: String,
    val label: String,
)
