package app.pausa.domain.security

import java.security.SecureRandom

/**
 * An unlock pattern on a 3x3 grid, as a list of dots numbered 0..8 left to right, top to bottom:
 *
 *     0 1 2
 *     3 4 5
 *     6 7 8
 *
 * It follows the rule of Android's own pattern lock: a line that passes over a dot that was not visited yet takes
 * that dot too, so going from 0 to 2 means 0, 1, 2. The pattern is stored through [SecretHasher] like the PIN.
 */
object Pattern {
    const val MIN_DOTS = 4
    const val MAX_DOTS = 9
    private const val SIZE = 3

    fun isValid(dots: List<Int>): Boolean =
        dots.size in MIN_DOTS..MAX_DOTS && dots.all { it in 0 until SIZE * SIZE } && dots.toSet().size == dots.size

    /** The dot a straight line from [from] to [to] passes over, or null if it passes over none. */
    fun dotBetween(from: Int, to: Int): Int? {
        val rowDelta = to / SIZE - from / SIZE
        val colDelta = to % SIZE - from % SIZE
        if (from == to || rowDelta % 2 != 0 || colDelta % 2 != 0) return null
        return (from / SIZE + rowDelta / 2) * SIZE + (from % SIZE + colDelta / 2)
    }

    /** [current] with [next] added, plus the dot in between if the line skips one that is not taken yet. */
    fun extend(current: List<Int>, next: Int): List<Int> {
        if (next in current) return current
        val last = current.lastOrNull() ?: return listOf(next)
        val between = dotBetween(last, next)
        return if (between != null && between !in current) current + between + next else current + next
    }

    fun encode(dots: List<Int>): String = dots.joinToString("")

    fun hash(dots: List<Int>, random: SecureRandom = SecureRandom()): String {
        require(isValid(dots)) { "A pattern needs $MIN_DOTS to $MAX_DOTS different dots" }
        return SecretHasher.hash(encode(dots), random)
    }

    fun verify(dots: List<Int>, stored: String): Boolean = isValid(dots) && SecretHasher.verify(encode(dots), stored)
}
