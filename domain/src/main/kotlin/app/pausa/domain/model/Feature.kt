package app.pausa.domain.model

/** A part of an app Pausa knows how to recognise and can therefore restrict on its own. */
data class SupportedFeature(
    val packageName: String,
    val featureId: String,
    val displayName: String,
)

object FeatureCatalog {
    val all: List<SupportedFeature> = listOf(
        SupportedFeature("com.google.android.youtube", FeatureIds.YOUTUBE_SHORTS, "Shorts"),
        SupportedFeature("com.instagram.android", FeatureIds.INSTAGRAM_REELS, "Reels"),
    )

    fun forPackage(packageName: String): List<SupportedFeature> = all.filter { it.packageName == packageName }

    fun displayName(featureId: String): String = all.firstOrNull { it.featureId == featureId }?.displayName ?: featureId
}

/**
 * One thing seen in the window, reduced to what recognising a feature needs: the view id, whether it is selected,
 * and a short label. The label is only offered for selected elements and is never the text of content.
 */
data class NodeSnapshot(
    val viewId: String?,
    val selected: Boolean,
    val label: String?,
)

/**
 * How to tell that the user is inside [featureId] of [packageName]. There is no official API for this, so it is
 * a best-effort reading of the screen's structure: a view id that belongs to the feature, or the selected tab
 * carrying the feature's name. Apps change these without notice, which is why the rules live in one place and the
 * technical lab can capture the real ones from the phone.
 */
data class FeatureSignature(
    val packageName: String,
    val featureId: String,
    val viewIdFragments: List<String>,
    val selectedLabels: List<String>,
)

object FeatureMatcher {

    /** The feature the screen shows, or null. [nodes] is read lazily and reading stops at the first match. */
    fun detect(packageName: String, nodes: Sequence<NodeSnapshot>, signatures: List<FeatureSignature>): String? {
        val candidates = signatures.filter { it.packageName == packageName }
        if (candidates.isEmpty()) return null
        for (node in nodes) {
            for (signature in candidates) {
                if (matches(signature, node)) return signature.featureId
            }
        }
        return null
    }

    private fun matches(signature: FeatureSignature, node: NodeSnapshot): Boolean {
        val id = node.viewId
        if (id != null && signature.viewIdFragments.any { id.contains(it) }) return true
        val label = node.label
        return node.selected && label != null && signature.selectedLabels.any { it.equals(label, ignoreCase = true) }
    }
}

/** What is known today. Unverified against current app versions: see the notes on [FeatureSignature]. */
object FeatureSignatures {
    val all: List<FeatureSignature> = listOf(
        FeatureSignature(
            packageName = "com.google.android.youtube",
            featureId = FeatureIds.YOUTUBE_SHORTS,
            viewIdFragments = listOf("reel_player_page_container", "reel_recycler", "reel_watch_fragment_root"),
            selectedLabels = listOf("Shorts"),
        ),
        FeatureSignature(
            packageName = "com.instagram.android",
            featureId = FeatureIds.INSTAGRAM_REELS,
            viewIdFragments = listOf("clips_viewer_view_pager", "clips_viewer_video_layout"),
            selectedLabels = listOf("Reels"),
        ),
    )
}
