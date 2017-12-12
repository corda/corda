package net.corda.attestation

import java.security.SecureRandom
import java.util.*

class DummyRandom : SecureRandom(byteArrayOf()) {
    override fun nextBoolean() = false
    override fun nextInt() = 9
    override fun nextInt(bound: Int) = 0
    override fun nextBytes(bytes: ByteArray) = Arrays.fill(bytes, 9)
    override fun nextDouble() = 9.0
    override fun nextFloat() = 9.0f
    override fun nextLong() = 9L
    override fun nextGaussian() = 0.0
}
