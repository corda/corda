/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.utilities

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import java.time.Instant

/**
 * A tree describing the diff between two objects.
 *
 * For example:
 * data class A(val field1: Int, val field2: String, val field3: Unit)
 * fun main(args: Array<String>) {
 *     val someA = A(1, "hello", Unit)
 *     val someOtherA = A(2, "bello", Unit)
 *     println(ObjectDiffer.diff(someA, someOtherA))
 * }
 *
 * Will give back Step(branches=[(field1, Last(a=1, b=2)), (field2, Last(a=hello, b=bello))])
 */
sealed class DiffTree {
    /**
     * Describes a "step" from the object root. It contains a list of field-subtree pairs.
     */
    data class Step(val branches: List<Pair<String, DiffTree>>) : DiffTree()

    /**
     * Describes the leaf of the diff. This is either where the diffing was cutoff (e.g. primitives) or where it failed.
     */
    data class Last(val a: Any?, val b: Any?) : DiffTree()

    /**
     * Flattens the [DiffTree] into a list of [DiffPath]s
     */
    fun toPaths(): List<DiffPath> {
        return when (this) {
            is Step -> branches.flatMap { (step, tree) -> tree.toPaths().map { it.copy(path = listOf(step) + it.path) } }
            is Last -> listOf(DiffPath(emptyList(), a, b))
        }
    }
}

/**
 * A diff focused on a single [DiffTree.Last] diff, including the path leading there.
 */
data class DiffPath(
        val path: List<String>,
        val a: Any?,
        val b: Any?
) {
    override fun toString(): String {
        return "${path.joinToString(".")}: \n    $a\n    $b\n"
    }
}

/**
 * This is a very simple differ used to diff objects of any kind, to be used for diagnostic.
 */
object ObjectDiffer {
    fun diff(a: Any?, b: Any?): DiffTree? {
        if (a == null || b == null) {
            if (a == b) {
                return null
            } else {
                return DiffTree.Last(a, b)
            }
        }
        if (a != b) {
            if (a.javaClass.isPrimitive || a.javaClass in diffCutoffClasses) {
                return DiffTree.Last(a, b)
            }
            // TODO deduplicate this code
            if (a is Map<*, *> && b is Map<*, *>) {
                val allKeys = a.keys + b.keys
                val branches = allKeys.mapNotNull { key -> diff(a.get(key), b.get(key))?.let { key.toString() to it } }
                if (branches.isEmpty()) {
                    return null
                } else {
                    return DiffTree.Step(branches)
                }
            }
            if (a is java.util.Map<*, *> && b is java.util.Map<*, *>) {
                val allKeys = a.keySet() + b.keySet()
                val branches = allKeys.mapNotNull { key -> diff(a.get(key), b.get(key))?.let { key.toString() to it } }
                if (branches.isEmpty()) {
                    return null
                } else {
                    return DiffTree.Step(branches)
                }
            }
            val aFields = getFieldFoci(a)
            val bFields = getFieldFoci(b)
            try {
                if (aFields != bFields) {
                    return DiffTree.Last(a, b)
                } else {
                    // TODO need to account for cases where the fields don't match up (different subclasses)
                    val branches = aFields.map { field -> diff(field.get(a), field.get(b))?.let { field.name to it } }.filterNotNull()
                    if (branches.isEmpty()) {
                        return DiffTree.Last(a, b)
                    } else {
                        return DiffTree.Step(branches)
                    }
                }
            } catch (throwable: Exception) {
                Exception("Error while diffing $a with $b", throwable).printStackTrace(System.out)
                return DiffTree.Last(a, b)
            }
        } else {
            return null
        }
    }

    // List of types to cutoff the diffing at.
    private val diffCutoffClasses: Set<Class<*>> = setOf(
            String::class.java,
            Class::class.java,
            Instant::class.java
    )

    // A type capturing the accessor to a field. This is a separate abstraction to simple reflection as we identify
    // getX() and isX() calls as fields as well.
    private data class FieldFocus(val name: String, val type: Type, val getter: Method) {
        fun get(obj: Any): Any? {
            return getter.invoke(obj)
        }
    }

    private fun getFieldFoci(obj: Any) : List<FieldFocus> {
        val foci = ArrayList<FieldFocus>()
        for (method in obj.javaClass.declaredMethods) {
            if (Modifier.isStatic(method.modifiers)) {
                continue
            }
            if (method.name.startsWith("get") && method.name.length > 3 && method.parameterCount == 0) {
                val fieldName = method.name[3].toLowerCase() + method.name.substring(4)
                foci.add(FieldFocus(fieldName, method.returnType, method))
            } else if (method.name.startsWith("is") && method.parameterCount == 0) {
                foci.add(FieldFocus(method.name, method.returnType, method))
            }
        }
        return foci
    }
}
