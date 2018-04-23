package net.corda.attestation.host.sgx.enclave

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
        private const val KEY_SIZE = 64

        /**
         * Create a public key from a byte array.
         *
         * @param bytes The 64 bytes forming the NIST P-256 elliptic curve.
         */
        fun fromBytes(bytes: ByteArray): ECKey {
            if (bytes.size != KEY_SIZE) {
                throw Exception("Expected $KEY_SIZE bytes, but got ${bytes.size}")
            }
            val componentX = bytes.copyOfRange(0, KEY_SIZE / 2)
            val componentY = bytes.copyOfRange(KEY_SIZE / 2, KEY_SIZE)
            return ECKey(componentX, componentY)
        }

    }

}

/**
 * Short-hand generator for supplying a constant X or Y component.
 */
fun ecKeyComponent(vararg bytes: Int) = bytes.map { it.toByte() }.toByteArray()
