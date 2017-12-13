package net.corda.sgx.enclave

/**
 * Public key based on NIST P-256 elliptic curve
 *
 * @param componentX The 256-bit X component of the key.
 * @param componentY The 256-bit Y component of the key.
 */
class ECKey(
        componentX: ByteArray,
        componentY: ByteArray
) {

    /**
     * The bytes constituting the elliptic curve public key.
     */
    val bytes: ByteArray = componentX.plus(componentY)

    companion object {

        /**
         * Create a public key from a byte array.
         *
         * @param bytes The 64 bytes forming the NIST P-256 elliptic curve.
         */
        fun fromBytes(bytes: ByteArray): ECKey {
            if (bytes.size != 64) {
                throw Exception("Expected 64 bytes, but got ${bytes.size}")
            }
            val componentX = bytes.copyOfRange(0, 32)
            val componentY = bytes.copyOfRange(32, 64)
            return ECKey(componentX, componentY)
        }

    }

}

/**
 * Short-hand generator for supplying a constant X or Y component.
 */
fun ecKeyComponent(vararg bytes: Int) = bytes.map { it.toByte() }.toByteArray()
