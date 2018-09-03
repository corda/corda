package net.corda.node.internal.security

import java.util.*

class Password(valueRaw: CharArray) : AutoCloseable {

    constructor(value: String) : this(value.toCharArray())

    private val internalValue = valueRaw.copyOf()

    val value: CharArray
        get() = internalValue.copyOf()

    val valueAsString: String
        get() = internalValue.joinToString("")

    override fun close() {
        internalValue.indices.forEach { index ->
            internalValue[index] = MASK
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Password

        if (!Arrays.equals(internalValue, other.internalValue)) return false

        return true
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(internalValue)
    }

    override fun toString(): String = (0..5).map { MASK }.joinToString("")

    private companion object {
        private const val MASK = '*'
    }
}