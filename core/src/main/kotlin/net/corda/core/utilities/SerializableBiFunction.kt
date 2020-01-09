package net.corda.core.utilities

import java.io.Serializable

/**
 * Serializable Kotlin function (basically [kotlin.jvm.functions.Function2]).
 *
 * This is needed to allow Kryo and Quasar to serialize functions written in Java.
 * Kotlin functions will work correctly without this, but public API functions that
 * need to be serialized should use this functional interface instead.
 */
interface SerializableBiFunction<in P1, in P2, out R> : (P1, P2) -> R, Serializable
