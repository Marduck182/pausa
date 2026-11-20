package app.pausa.domain.model

/**
 * What a rule is about. Today an app or one feature inside an app; websites or categories can be added later
 * as new subtypes without touching the existing ones.
 */
sealed interface Target {
    val packageName: String

    data class App(override val packageName: String) : Target

    data class Feature(override val packageName: String, val featureId: String) : Target
}

/** Feature ids Pausa knows how to detect. They are stable keys, never shown to the user as-is. */
object FeatureIds {
    const val YOUTUBE_SHORTS = "youtube_shorts"
    const val INSTAGRAM_REELS = "instagram_reels"
}

/**
 * A rule on [this] applies to [request] when the rule covers it: an app rule covers the whole app, including
 * any of its features; a feature rule only covers that exact feature.
 */
fun Target.appliesTo(request: Target): Boolean = when (this) {
    is Target.App -> packageName == request.packageName
    is Target.Feature -> request is Target.Feature &&
        packageName == request.packageName &&
        featureId == request.featureId
}
