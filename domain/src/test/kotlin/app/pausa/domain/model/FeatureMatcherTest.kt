package app.pausa.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeatureMatcherTest {

    private val youtube = "com.google.android.youtube"
    private val signatures = FeatureSignatures.all

    private fun detect(pkg: String, vararg nodes: NodeSnapshot) =
        FeatureMatcher.detect(pkg, nodes.asSequence(), signatures)

    private fun node(id: String? = null, selected: Boolean = false, label: String? = null) = NodeSnapshot(id, selected, label)

    @Test
    fun `a view id that belongs to the feature is recognised`() {
        assertEquals(
            FeatureIds.YOUTUBE_SHORTS,
            detect(youtube, node("home_feed"), node("com.google.android.youtube:id/reel_player_page_container")),
        )
    }

    @Test
    fun `the selected tab carrying the feature name is recognised, whatever the case`() {
        assertEquals(FeatureIds.YOUTUBE_SHORTS, detect(youtube, node(selected = true, label = "shorts")))
    }

    @Test
    fun `the same label on an unselected tab is not enough`() {
        assertNull(detect(youtube, node(selected = false, label = "Shorts")))
    }

    @Test
    fun `an ordinary screen shows no feature`() {
        assertNull(detect(youtube, node("watch_player"), node("search_bar"), node(selected = true, label = "Home")))
    }

    @Test
    fun `signatures only apply to their own app`() {
        assertNull(detect("com.example.other", node("reel_player_page_container")))
    }

    @Test
    fun `instagram reels are recognised on the instagram package only`() {
        assertEquals(FeatureIds.INSTAGRAM_REELS, detect("com.instagram.android", node(selected = true, label = "Reels")))
        assertNull(detect(youtube, node(selected = true, label = "Reels")))
    }

    @Test
    fun `reading stops at the first match`() {
        var read = 0
        val nodes = generateSequence(0) { it + 1 }.map {
            read++
            if (it == 3) node("reel_recycler") else node("x$it")
        }
        assertEquals(FeatureIds.YOUTUBE_SHORTS, FeatureMatcher.detect(youtube, nodes, signatures))
        assertEquals(4, read)
    }

    @Test
    fun `catalog knows the supported features`() {
        assertEquals(listOf("Shorts"), FeatureCatalog.forPackage(youtube).map { it.displayName })
        assertEquals("Reels", FeatureCatalog.displayName(FeatureIds.INSTAGRAM_REELS))
        assertEquals("unknown", FeatureCatalog.displayName("unknown"))
    }
}
