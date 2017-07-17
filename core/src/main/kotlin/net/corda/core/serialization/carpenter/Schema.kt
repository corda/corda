package net.corda.core.serialization.carpenter

import jdk.internal.org.objectweb.asm.Opcodes.*

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor

import org.objectweb.asm.Type
import java.util.LinkedHashMap

/**
 * A Schema represents a desired class.
 */
abstract class Schema(
        val name: String,
        fields: Map<String, Field>,
        val superclass: Schema? = null,
        val interfaces: List<Class<*>> = emptyList())
{
    private fun Map<String, Field>.descriptors() =
            LinkedHashMap(this.mapValues { it.value.descriptor })

    /* Fix the order up front if the user didn't, inject the name into the field as it's
       neater when iterating */
    val fields = LinkedHashMap(fields.mapValues { it.value.copy(it.key, it.value.field) })

    fun fieldsIncludingSuperclasses(): Map<String, Field> =
            (superclass?.fieldsIncludingSuperclasses() ?: emptyMap()) + LinkedHashMap(fields)

    fun descriptorsIncludingSuperclasses(): Map<String, String> =
            (superclass?.descriptorsIncludingSuperclasses() ?: emptyMap()) + fields.descriptors()

    val jvmName: String
        get() = name.replace(".", "/")
}

class ClassSchema(
    name: String,
    fields: Map<String, Field>,
    superclass: Schema? = null,
    interfaces: List<Class<*>> = emptyList()
) : Schema (name, fields, superclass, interfaces)

class InterfaceSchema(
    name: String,
    fields: Map<String, Field>,
    superclass: Schema? = null,
    interfaces: List<Class<*>> = emptyList()
) : Schema (name, fields, superclass, interfaces)

object CarpenterSchemaFactory {
    fun newInstance (
            name: String,
            fields: Map<String, Field>,
            superclass: Schema? = null,
            interfaces: List<Class<*>> = emptyList(),
            isInterface: Boolean = false
    ) : Schema =
            if (isInterface) InterfaceSchema (name, fields, superclass, interfaces)
            else ClassSchema (name, fields, superclass, interfaces)
}

abstract class Field(val field: Class<out Any?>) {
    companion object {
        const val unsetName = "Unset"
    }

    var name: String = unsetName
    abstract val nullabilityAnnotation: String

    val descriptor: String
        get() = Type.getDescriptor(this.field)

    val type: String
        get() = if (this.field.isPrimitive) this.descriptor else "Ljava/lang/Object;"

    fun generateField(cw: ClassWriter) {
        val fieldVisitor = cw.visitField(ACC_PROTECTED + ACC_FINAL, name, descriptor, null, null)
        fieldVisitor.visitAnnotation(nullabilityAnnotation, true).visitEnd()
        fieldVisitor.visitEnd()
    }

    fun addNullabilityAnnotation(mv: MethodVisitor) {
        mv.visitAnnotation(nullabilityAnnotation, true).visitEnd()
    }

    fun visitParameter(mv: MethodVisitor, idx: Int) {
        with(mv) {
            visitParameter(name, 0)
            if (!field.isPrimitive) {
                visitParameterAnnotation(idx, nullabilityAnnotation, true).visitEnd()
            }
        }
    }

    abstract fun copy(name: String, field: Class<out Any?>): Field
    abstract fun nullTest(mv: MethodVisitor, slot: Int)
}

class NonNullableField(field: Class<out Any?>) : Field(field) {
    override val nullabilityAnnotation = "Ljavax/annotation/Nonnull;"

    constructor(name: String, field: Class<out Any?>) : this(field) {
        this.name = name
    }

    override fun copy(name: String, field: Class<out Any?>) = NonNullableField(name, field)

    override fun nullTest(mv: MethodVisitor, slot: Int) {
        assert(name != unsetName)

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

class NullableField(field: Class<out Any?>) : Field(field) {
    override val nullabilityAnnotation = "Ljavax/annotation/Nullable;"

    constructor(name: String, field: Class<out Any?>) : this(field) {
        if (field.isPrimitive) {
            throw NullablePrimitiveException (
                    "Field $name is primitive type ${Type.getDescriptor(field)} and thus cannot be nullable")
        }

        this.name = name
    }

    override fun copy(name: String, field: Class<out Any?>) = NullableField(name, field)

    override fun nullTest(mv: MethodVisitor, slot: Int) {
        assert(name != unsetName)
    }
}

object FieldFactory {
    fun newInstance (mandatory: Boolean, name: String, field: Class<out Any?>) =
            if (mandatory) NonNullableField (name, field) else NullableField (name, field)

}
