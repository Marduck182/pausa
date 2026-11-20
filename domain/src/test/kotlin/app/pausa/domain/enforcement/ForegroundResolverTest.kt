package app.pausa.domain.enforcement

import app.pausa.domain.model.Restriction
import app.pausa.domain.model.Rule
import app.pausa.domain.model.Target
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundResolverTest {

    private val transient = setOf("com.samsung.android.game.gametools", "com.android.systemui")
    private val game = "com.supercell.clashroyale"
    private val launcher = "com.sec.android.app.launcher"

    @Test
    fun `the top-most window wins`() {
        val windows = listOf(AppWindow(1, launcher), AppWindow(5, game))
        assertEquals(game, ForegroundResolver.topApp(windows, transient))
    }

    @Test
    fun `a floating window on top of the game does not hide the game`() {
        // The case that slipped through: Game Tools is drawn over Clash Royale and was the only event sent.
        val windows = listOf(AppWindow(9, "com.samsung.android.game.gametools"), AppWindow(5, game), AppWindow(1, launcher))
        assertEquals(game, ForegroundResolver.topApp(windows, transient))
    }

    @Test
    fun `the order of the list does not matter`() {
        val windows = listOf(AppWindow(1, launcher), AppWindow(9, "com.android.systemui"), AppWindow(5, game))
        assertEquals(game, ForegroundResolver.topApp(windows, transient))
    }

    @Test
    fun `windows with no package are skipped`() {
        assertEquals(game, ForegroundResolver.topApp(listOf(AppWindow(9, null), AppWindow(5, game)), transient))
    }

    @Test
    fun `nothing but floating windows means no app`() {
        assertNull(ForegroundResolver.topApp(listOf(AppWindow(9, "com.android.systemui")), transient))
        assertNull(ForegroundResolver.topApp(emptyList(), transient))
    }

    @Test
    fun `the launcher on top means the user is on the home screen`() {
        val windows = listOf(AppWindow(7, launcher), AppWindow(3, game))
        assertEquals(launcher, ForegroundResolver.topApp(windows, transient))
    }

    @Test
    fun `the active window wins over a stale one that is still listed on top`() {
        // Right after going home the game is still drawn on top while it animates away, but the launcher is active.
        val windows = listOf(AppWindow(9, game, isActive = false), AppWindow(4, launcher, isActive = true))
        assertEquals(launcher, ForegroundResolver.topApp(windows, transient))
    }

    @Test
    fun `an app that is opening is the active one and is found`() {
        val windows = listOf(AppWindow(5, game, isActive = true), AppWindow(4, launcher, isActive = false))
        assertEquals(game, ForegroundResolver.topApp(windows, transient))
    }

    @Test
    fun `when the active window is only a floating one the top app window is used`() {
        val windows = listOf(
            AppWindow(9, "com.samsung.android.game.gametools", isActive = true),
            AppWindow(5, game, isActive = false),
        )
        assertEquals(game, ForegroundResolver.topApp(windows, transient))
    }

    @Test
    fun `with no active window the top-most one is used`() {
        assertEquals(game, ForegroundResolver.topApp(listOf(AppWindow(5, game), AppWindow(1, launcher)), transient))
    }

    @Test
    fun `packages are only asked for as far down as needed`() {
        val asked = mutableListOf<Int>()
        val top = ForegroundResolver.topApp(
            windows = listOf(1, 5, 9),
            layerOf = { it },
            packageOf = { layer -> asked += layer; if (layer == 5) game else null },
            isActiveOf = { false },
            transient = transient,
        )
        assertEquals(game, top)
        assertEquals(listOf(9, 5), asked) // the busiest app at the bottom was never bothered
    }

    // The behaviour this fixes, end to end through the core: the game comes back with no event of its own.

    private val zone = ZoneId.of("America/Mexico_City")
    private fun at(minute: Int, second: Int = 0) = ZonedDateTime.of(2026, 11, 19, 17, minute, second, 0, zone)

    @Test
    fun `an app that came back with no event of its own is blocked once the screen is read`() {
        val core = EnforcementCore()
        core.systemPackages = SystemPackageSets(home = setOf(launcher), transient = transient)
        core.rules = listOf(Rule(1, Target.App(game), listOf(Restriction.AlwaysBlock)))

        // Open, blocked, home. Then back from recent apps: only the Game Tools event arrives, which is ignored.
        assertTrue(core.onWindowChanged(game, at(30, 23)) is EnforcementAction.ShowBlock)
        core.onWindowChanged(launcher, at(30, 25))
        assertEquals(EnforcementAction.None, core.onWindowChanged("com.samsung.android.game.gametools", at(30, 35)))

        // Reading the windows finds the game on top, and reporting it blocks it.
        val top = ForegroundResolver.topApp(listOf(AppWindow(9, "com.samsung.android.game.gametools"), AppWindow(5, game)), transient)!!
        assertTrue(core.onWindowChanged(top, at(30, 35)) is EnforcementAction.ShowBlock)
    }

    @Test
    fun `reporting the same app twice does not block twice`() {
        val core = EnforcementCore()
        core.systemPackages = SystemPackageSets(home = setOf(launcher), transient = transient)
        core.rules = listOf(Rule(1, Target.App(game), listOf(Restriction.Intervention())))
        assertTrue(core.onWindowChanged(game, at(30)) is EnforcementAction.ShowIntervention)
        assertEquals(EnforcementAction.None, core.onWindowChanged(game, at(30, 1)))
    }
}
