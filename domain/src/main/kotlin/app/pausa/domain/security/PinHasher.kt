package app.pausa.domain.security

import java.security.SecureRandom

/** A numeric PIN of four to eight digits, stored through [SecretHasher]. */
object PinHasher {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    fun isValid(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String, random: SecureRandom = SecureRandom()): String {
        require(isValid(pin)) { "PIN must be $MIN_LENGTH to $MAX_LENGTH digits" }
        return SecretHasher.hash(pin, random)
    }

    fun verify(pin: String, stored: String): Boolean = SecretHasher.verify(pin, stored)
}
