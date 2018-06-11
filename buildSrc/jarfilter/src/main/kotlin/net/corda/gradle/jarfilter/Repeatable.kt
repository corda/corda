package net.corda.gradle.jarfilter

import org.objectweb.asm.ClassVisitor

interface Repeatable<T : ClassVisitor> {
    fun recreate(visitor: ClassVisitor): T
    val hasUnwantedElements: Boolean
}
