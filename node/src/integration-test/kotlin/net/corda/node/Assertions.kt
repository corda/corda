@file:JvmName("Assertions")
package net.corda.node

import net.corda.core.serialization.CordaSerializable
import org.junit.Assert.assertNull

inline fun <reified T> assertNotCordaSerializable() {
    assertNotCordaSerializable(T::class.java)
}

fun assertNotCordaSerializable(clazz: Class<*>) {
    assertNull("$clazz must NOT be annotated as @CordaSerializable!",
        clazz.getAnnotation(CordaSerializable::class.java))
}
