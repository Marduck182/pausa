package app.pausa.domain.model

/** Which look the app uses: follow the phone, or always light, or always dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** What happens to a blocked app: send the person to the home screen, or only one step back. */
enum class BlockExit { HOME, BACK }

/** Everything the user can set that is not a rule. Stored only on the phone. */
data class AppSettings(
    /** How long a deliberate action (turning a rule off, asking for a temporary pass) makes the user wait. */
    val cooldownSeconds: Int = DEFAULT_COOLDOWN_SECONDS,
    val pinEnabled: Boolean = false,
    /** A security pattern is set, so apps with a pattern rule ask for it. */
    val patternEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
    /** Apps that pass every block (messages, maps, mail...). */
    val exceptions: Set<String> = emptySet(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val blockExit: BlockExit = BlockExit.HOME,
) {
    companion object {
        const val DEFAULT_COOLDOWN_SECONDS = 30
        const val MAX_COOLDOWN_SECONDS = 120
    }
}
