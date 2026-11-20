package app.pausa.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `the right pin verifies and a wrong one does not`() {
        val stored = PinHasher.hash("4821")
        assertTrue(PinHasher.verify("4821", stored))
        assertFalse(PinHasher.verify("4822", stored))
        assertFalse(PinHasher.verify("", stored))
    }

    @Test
    fun `the stored text never contains the pin`() {
        assertFalse(PinHasher.hash("4821").contains("4821"))
    }

    @Test
    fun `the same pin gives a different stored text each time, because of the salt`() {
        assertNotEquals(PinHasher.hash("4821"), PinHasher.hash("4821"))
    }

    @Test
    fun `damaged or foreign stored text is rejected instead of crashing`() {
        assertFalse(PinHasher.verify("4821", ""))
        assertFalse(PinHasher.verify("4821", "plaintext"))
        assertFalse(PinHasher.verify("4821", "v1\$notanumber\$AAAA\$AAAA"))
        assertFalse(PinHasher.verify("4821", "v1\$1000\$!!!\$!!!"))
        assertFalse(PinHasher.verify("4821", "v9\$120000\$AAAA\$AAAA"))
    }

    @Test
    fun `only four to eight digits are a valid pin`() {
        assertTrue(PinHasher.isValid("1234"))
        assertTrue(PinHasher.isValid("12345678"))
        assertFalse(PinHasher.isValid("123"))
        assertFalse(PinHasher.isValid("123456789"))
        assertFalse(PinHasher.isValid("12a4"))
        assertFalse(PinHasher.isValid(""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an invalid pin cannot be hashed`() {
        PinHasher.hash("12")
    }

    @Test
    fun `format carries its version`() {
        assertEquals("v1", PinHasher.hash("1234").substringBefore('$'))
    }
}
