@file:JvmName("Utils")

package net.corda.node.internal.classloading

inline fun <reified A : Annotation> Class<*>.requireAnnotation(): A {
    return requireNotNull(getDeclaredAnnotation(A::class.java)) { "$name needs to be annotated with ${A::class.java.name}" }
}