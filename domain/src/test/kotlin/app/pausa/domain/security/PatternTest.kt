package app.pausa.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternTest {

    @Test
    fun `a line over an untaken dot takes that dot too`() {
        assertEquals(1, Pattern.dotBetween(0, 2))
        assertEquals(3, Pattern.dotBetween(0, 6))
        assertEquals(4, Pattern.dotBetween(0, 8))
        assertEquals(4, Pattern.dotBetween(2, 6))
        assertEquals(5, Pattern.dotBetween(2, 8))
        assertEquals(4, Pattern.dotBetween(1, 7))
    }

    @Test
    fun `neighbours and knight moves pass over nothing`() {
        assertNull(Pattern.dotBetween(0, 1))
        assertNull(Pattern.dotBetween(0, 4))
        assertNull(Pattern.dotBetween(0, 5)) // knight move
        assertNull(Pattern.dotBetween(0, 7))
        assertNull(Pattern.dotBetween(4, 4))
    }

    @Test
    fun `extending adds the skipped dot once`() {
        assertEquals(listOf(0, 1, 2), Pattern.extend(listOf(0), 2))
        // The dot in between was already taken, so it is not added again.
        assertEquals(listOf(1, 0, 2), Pattern.extend(listOf(1, 0), 2))
        assertEquals(listOf(4), Pattern.extend(emptyList(), 4))
    }

    @Test
    fun `a dot already in the pattern is ignored`() {
        assertEquals(listOf(0, 1), Pattern.extend(listOf(0, 1), 0))
    }

    @Test
    fun `a pattern needs four to nine different dots`() {
        assertTrue(Pattern.isValid(listOf(0, 1, 2, 5)))
        assertTrue(Pattern.isValid((0..8).toList()))
        assertFalse(Pattern.isValid(listOf(0, 1, 2)))
        assertFalse(Pattern.isValid(listOf(0, 1, 1, 2)))
        assertFalse(Pattern.isValid(listOf(0, 1, 2, 9)))
        assertFalse(Pattern.isValid(emptyList()))
    }

    @Test
    fun `the right pattern verifies and a different order does not`() {
        val stored = Pattern.hash(listOf(0, 3, 6, 7))
        assertTrue(Pattern.verify(listOf(0, 3, 6, 7), stored))
        assertFalse(Pattern.verify(listOf(7, 6, 3, 0), stored))
        assertFalse(Pattern.verify(listOf(0, 3, 6), stored))
    }

    @Test
    fun `the stored text does not contain the pattern`() {
        assertFalse(Pattern.hash(listOf(0, 3, 6, 7)).contains("0367"))
    }

    @Test
    fun `a pin and a pattern with the same digits are stored in the same safe way`() {
        // They share the hasher, so what protects the PIN protects the pattern.
        assertTrue(SecretHasher.verify("0367", Pattern.hash(listOf(0, 3, 6, 7))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a pattern that is too short cannot be hashed`() {
        Pattern.hash(listOf(0, 1))
    }
}
