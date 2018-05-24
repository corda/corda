package net.corda.sandbox.rewiring

/**
 * The byte code representation of a class.
 *
 * @property bytes The raw bytes of the class.
 * @property isModified Indication of whether the class has been modified as part of loading.
 */
class ByteCode(
        val bytes: ByteArray,
        val isModified: Boolean
)
