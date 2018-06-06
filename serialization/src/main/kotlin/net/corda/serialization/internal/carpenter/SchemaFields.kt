/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal.carpenter

import jdk.internal.org.objectweb.asm.Opcodes.*
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Type

abstract class Field(val field: Class<out Any?>) {
    abstract var descriptor: String?

    companion object {
        const val unsetName = "Unset"
    }

    var name: String = unsetName
    abstract val type: String

    abstract fun generateField(cw: ClassWriter)
    abstract fun visitParameter(mv: MethodVisitor, idx: Int)
}

/**
 * Any field that can be a member of an object
 *
 * Known
 *   - [NullableField]
 *   - [NonNullableField]
 */
abstract class ClassField(field: Class<out Any?>) : Field(field) {
    abstract val nullabilityAnnotation: String
    abstract fun nullTest(mv: MethodVisitor, slot: Int)

    override var descriptor: String? = Type.getDescriptor(this.field)
    override val type: String get() = if (this.field.isPrimitive) this.descriptor!! else "Ljava/lang/Object;"

    fun addNullabilityAnnotation(mv: MethodVisitor) {
        mv.visitAnnotation(nullabilityAnnotation, true).visitEnd()
    }

    override fun generateField(cw: ClassWriter) {
        cw.visitField(ACC_PROTECTED + ACC_FINAL, name, descriptor, null, null).visitAnnotation(
                nullabilityAnnotation, true).visitEnd()
    }

    override fun visitParameter(mv: MethodVisitor, idx: Int) {
        with(mv) {
            visitParameter(name, 0)
            if (!field.isPrimitive) {
                visitParameterAnnotation(idx, nullabilityAnnotation, true).visitEnd()
            }
        }
    }
}

/**
 * A member of a constructed class that can be assigned to null, the
 * mandatory type for primitives, but also any member that cannot be
 * null
 *
 * maps to AMQP mandatory = true fields
 */
open class NonNullableField(field: Class<out Any?>) : ClassField(field) {
    override val nullabilityAnnotation = "Ljavax/annotation/Nonnull;"

    constructor(name: String, field: Class<out Any?>) : this(field) {
        this.name = name
    }

    override fun nullTest(mv: MethodVisitor, slot: Int) {
        require(name != unsetName)

        if (!field.isPrimitive) {
            with(mv) {
                visitVarInsn(ALOAD, 0) // load this
                visitVarInsn(ALOAD, slot) // load parameter
                visitLdcInsn("param \"$name\" cannot be null")
                visitMethodInsn(INVOKESTATIC,
                        "java/util/Objects",
                        "requireNonNull",
                        "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false)
                visitInsn(POP)
            }
        }
    }
}

/**
 * A member of a constructed class that can be assigned to null,
 *
 * maps to AMQP mandatory = false fields
 */
class NullableField(field: Class<out Any?>) : ClassField(field) {
    override val nullabilityAnnotation = "Ljavax/annotation/Nullable;"

    constructor(name: String, field: Class<out Any?>) : this(field) {
        this.name = name
    }

    init {
        if (field.isPrimitive) {
            throw NullablePrimitiveException(name, field)
        }
    }

    override fun nullTest(mv: MethodVisitor, slot: Int) {
        require(name != unsetName)
    }
}

/**
 * Represents enum constants within an enum
 */
class EnumField : Field(Enum::class.java) {
    override var descriptor: String? = null

    override val type: String
        get() = "Ljava/lang/Enum;"

    override fun generateField(cw: ClassWriter) {
        cw.visitField(ACC_PUBLIC + ACC_FINAL + ACC_STATIC + ACC_ENUM, name,
                descriptor, null, null).visitEnd()
    }

    override fun visitParameter(mv: MethodVisitor, idx: Int) {
        mv.visitParameter(name, 0)
    }
}

/**
 * Constructs a Field Schema object of the correct type depending weather
 * the AMQP schema indicates it's mandatory (non nullable) or not (nullable)
 */
object FieldFactory {
    fun newInstance(mandatory: Boolean, name: String, field: Class<out Any?>) =
            if (mandatory) NonNullableField(name, field) else NullableField(name, field)
}
