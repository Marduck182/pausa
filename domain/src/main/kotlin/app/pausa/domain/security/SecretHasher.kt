package app.pausa.domain.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Turns a short secret (a PIN, a pattern) into something that can be stored: a random salt plus a slow hash
 * (PBKDF2-HMAC-SHA256), so the secret itself is never kept. The format carries its own version and iteration count
 * so it can be strengthened later without invalidating secrets already set.
 *
 * A short secret has few possibilities, so this is friction against a casual attempt on an unlocked phone, not
 * protection against someone who has the stored value and time. That is the aim: stopping an impulse or a curious
 * hand, not an attacker.
 */
object SecretHasher {
    private const val VERSION = "v1"
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun hash(secret: String, random: SecureRandom = SecureRandom()): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = derive(secret, salt, ITERATIONS)
        val encoder = Base64.getEncoder()
        return listOf(VERSION, ITERATIONS, encoder.encodeToString(salt), encoder.encodeToString(hash)).joinToString("$")
    }

    fun verify(secret: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != VERSION) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        return try {
            val decoder = Base64.getDecoder()
            val expected = decoder.decode(parts[3])
            val actual = derive(secret, decoder.decode(parts[2]), iterations)
            // Constant-time comparison, so the time taken does not reveal how much of it matched.
            MessageDigest.isEqual(expected, actual)
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun derive(secret: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance(ALGORITHM)
            .generateSecret(PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_BITS))
            .encoded
}
