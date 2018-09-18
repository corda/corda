package net.corda.node.internal

import java.util.*

fun Exception.errorCode(): String {
    val hash = staticLocationBasedHash()
    return hash.toBase(36)
}

private fun Throwable.staticLocationBasedHash(visited: Set<Throwable> = setOf(this)): Int {
    val cause = this.cause
    return when {
        cause != null && !visited.contains(cause) -> Objects.hash(this::class.java.name, stackTrace.customHashCode(), cause.staticLocationBasedHash(visited +  cause))
        else -> Objects.hash(this::class.java.name, stackTrace.customHashCode())
    }
}

private fun Int.toBase(base: Int): String = Integer.toUnsignedString(this, base)

private fun Array<StackTraceElement?>?.customHashCode(): Int {

    if (this == null) {
        return 0
    }
    return Arrays.hashCode(map { it?.customHashCode() ?: 0 }.toIntArray())
}

private fun StackTraceElement.customHashCode(): Int {

    return Objects.hash(StackTraceElement::class.java.name, methodName, lineNumber)
}