@file:JvmName("Assertions")
package net.corda.serialization.djvm

import net.corda.core.serialization.CordaSerializable
import org.junit.jupiter.api.Assertions.assertNull

inline fun <reified T> assertNotCordaSerializable() {
    assertNotCordaSerializable(T::class.java)
}

fun assertNotCordaSerializable(clazz: Class<*>) {
    assertNull(clazz.getAnnotation(CordaSerializable::class.java),
        "$clazz must NOT be annotated as @CordaSerializable!")
}
