@file:JvmName("Assertions")
package net.corda.node

import net.corda.core.serialization.CordaSerializable
import org.junit.Assert.assertNull
import kotlin.reflect.KClass

fun assertNotCordaSerializable(klass: KClass<out Any>) {
    assertNotCordaSerializable(klass.java)
}

fun assertNotCordaSerializable(clazz: Class<*>) {
    assertNull("$clazz must NOT be annotated as @CordaSerializable!",
        clazz.getAnnotation(CordaSerializable::class.java))
}
