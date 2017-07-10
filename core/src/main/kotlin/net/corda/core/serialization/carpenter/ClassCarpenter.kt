package net.corda.core.serialization.carpenter

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type

import java.lang.Character.isJavaIdentifierPart
import java.lang.Character.isJavaIdentifierStart

import java.util.*

/**
 * Any object that implements this interface is expected to expose its own fields via the [get] method, exactly
 * as if `this.class.getMethod("get" + name.capitalize()).invoke(this)` had been called. It is intended as a more
 * convenient alternative to reflection.
 */
interface SimpleFieldAccess {
    operator fun get(name: String): Any?
}


/**
 * A class carpenter generates JVM bytecodes for a class given a schema and then loads it into a sub-classloader.
 * The generated classes have getters, a toString method and implement a simple property access interface. The
 * resulting class can then be accessed via reflection APIs, or cast to one of the requested interfaces.
 *
 * Additional interfaces may be requested if they consist purely of get methods and the schema matches.
 *
 * # Discussion
 *
 * This class may initially appear pointless: why create a class at runtime that simply holds data and which
 * you cannot compile against? The purpose is to enable the synthesis of data classes based on (AMQP) schemas
 * when the app that originally defined them is not available on the classpath. Whilst the getters and setters
 * are not usable directly, many existing reflection based frameworks like JSON/XML processors, Swing property
 * editor sheets, Groovy and so on can work with the JavaBean ("POJO") format. Feeding these objects to such
 * frameworks can often be useful. The generic property access interface is helpful if you want to write code
 * that accesses these schemas but don't want to actually define/depend on the classes themselves.
 *
 * # Usage notes
 *
 * This class is not thread safe.
 *
 * The generated class has private final fields and getters for each field. The constructor has one parameter
 * for each field. In this sense it is like a Kotlin data class.
 *
 * The generated class implements [SimpleFieldAccess]. The get method takes the name of the field, not the name
 * of a getter i.e. use .get("someVar") not .get("getSomeVar") or in Kotlin you can use square brackets syntax.
 *
 * The generated class implements toString() using Google Guava to simplify formatting. Make sure it's on the
 * classpath of the generated classes.
 *
 * Generated classes can refer to each other as long as they're defined in the right order. They can also
 * inherit from each other. When inheritance is used the constructor requires parameters in order of superclasses
 * first, child class last.
 *
 * You cannot create boxed primitive fields with this class: fields are always of primitive type.
 *
 * Nullability information is not emitted.
 *
 * Each [ClassCarpenter] defines its own classloader and thus, its own namespace. If you create multiple
 * carpenters, you can load the same schema with the same name and get two different classes, whose objects
 * will not be interoperable.
 *
 * Equals/hashCode methods are not yet supported.
 */
class ClassCarpenter {
    // TODO: Generics.
    // TODO: Sandbox the generated code when a security manager is in use.
    // TODO: Generate equals/hashCode.
    // TODO: Support annotations.
    // TODO: isFoo getter patterns for booleans (this is what Kotlin generates)

    class DuplicateNameException : RuntimeException("An attempt was made to register two classes with the same name within the same ClassCarpenter namespace.")
    class InterfaceMismatchException(msg: String) : RuntimeException(msg)
    class NullablePrimitiveException(msg: String) : RuntimeException(msg)

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
            println ("generateField $name $nullabilityAnnotation")
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

    /**
     * A Schema represents a desired class.
     */
    abstract class Schema(
            val name: String,
            fields: Map<String, Field>,
            val superclass: Schema? = null,
            val interfaces: List<Class<*>> = emptyList())
    {
        private fun Map<String, ClassCarpenter.Field>.descriptors() =
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

    private val String.jvm: String get() = replace(".", "/")

    class ClassSchema(
            name: String,
            fields: Map<String, Field>,
            superclass: Schema? = null,
            interfaces: List<Class<*>> = emptyList()
    ) : Schema(name, fields, superclass, interfaces)

    class InterfaceSchema(
            name: String,
            fields: Map<String, Field>,
            superclass: Schema? = null,
            interfaces: List<Class<*>> = emptyList()
    ) : Schema(name, fields, superclass, interfaces)

    private class CarpenterClassLoader : ClassLoader(Thread.currentThread().contextClassLoader) {
        fun load(name: String, bytes: ByteArray) = defineClass(name, bytes, 0, bytes.size)
    }

    private val classloader = CarpenterClassLoader()

    private val _loaded = HashMap<String, Class<*>>()

    /** Returns a snapshot of the currently loaded classes as a map of full class name (package names+dots) -> class object */
    val loaded: Map<String, Class<*>> = HashMap(_loaded)

    /**
     * Generate bytecode for the given schema and load into the JVM. The returned class object can be used to
     * construct instances of the generated class.
     *
     * @throws DuplicateName if the schema's name is already taken in this namespace (you can create a new ClassCarpenter if you're OK with ambiguous names)
     */
    fun build(schema: Schema): Class<*> {
        validateSchema(schema)
        // Walk up the inheritance hierarchy and then start walking back down once we either hit the top, or
        // find a class we haven't generated yet.
        val hierarchy = ArrayList<Schema>()
        hierarchy += schema
        var cursor = schema.superclass
        while (cursor != null && cursor.name !in _loaded) {
            hierarchy += cursor
            cursor = cursor.superclass
        }

        hierarchy.reversed().forEach {
            when (it) {
                is InterfaceSchema -> generateInterface(it)
                is ClassSchema -> generateClass(it)
            }
        }

        return _loaded[schema.name]!!
    }

    private fun generateInterface(interfaceSchema: Schema): Class<*> {
        return generate(interfaceSchema) { cw, schema ->
            val interfaces = schema.interfaces.map { it.name.jvm }.toTypedArray()

            with(cw) {
                visit(V1_8, ACC_PUBLIC + ACC_ABSTRACT + ACC_INTERFACE, schema.jvmName, null, "java/lang/Object", interfaces)

                generateAbstractGetters(schema)

                visitEnd()
            }
        }
    }

    private fun generateClass(classSchema: Schema): Class<*> {
        return generate(classSchema) { cw, schema ->
            val superName = schema.superclass?.jvmName ?: "java/lang/Object"
            val interfaces = arrayOf(SimpleFieldAccess::class.java.name.jvm) + schema.interfaces.map { it.name.jvm }

            with(cw) {
                visit(V1_8, ACC_PUBLIC + ACC_SUPER, schema.jvmName, null, superName, interfaces)

                generateFields(schema)
                generateConstructor(schema)
                generateGetters(schema)
                if (schema.superclass == null)
                    generateGetMethod()   // From SimplePropertyAccess
                generateToString(schema)

                visitEnd()
            }
        }
    }

    private fun generate(schema: Schema, generator: (ClassWriter, Schema) -> Unit): Class<*> {
        // Lazy: we could compute max locals/max stack ourselves, it'd be faster.
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)

        generator(cw, schema)

        val clazz = classloader.load(schema.name, cw.toByteArray())
        _loaded[schema.name] = clazz
        return clazz
    }

    private fun ClassWriter.generateFields(schema: Schema) {
        schema.fields.forEach { it.value.generateField(this) }
    }

    private fun ClassWriter.generateToString(schema: Schema) {
        val toStringHelper = "com/google/common/base/MoreObjects\$ToStringHelper"
        with(visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", "", null)) {
            visitCode()
            // com.google.common.base.MoreObjects.toStringHelper("TypeName")
            visitLdcInsn(schema.name.split('.').last())
            visitMethodInsn(INVOKESTATIC, "com/google/common/base/MoreObjects", "toStringHelper", "(Ljava/lang/String;)L$toStringHelper;", false)
            // Call the add() methods.
            for ((name, field) in schema.fieldsIncludingSuperclasses().entries) {
                visitLdcInsn(name)
                visitVarInsn(ALOAD, 0)  // this
                visitFieldInsn(GETFIELD, schema.jvmName, name, schema.descriptorsIncludingSuperclasses()[name])
                visitMethodInsn(INVOKEVIRTUAL, toStringHelper, "add", "(Ljava/lang/String;${field.type})L$toStringHelper;", false)
            }
            // call toString() on the builder and return.
            visitMethodInsn(INVOKEVIRTUAL, toStringHelper, "toString", "()Ljava/lang/String;", false)
            visitInsn(ARETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
    }

    private fun ClassWriter.generateGetMethod() {
        val ourJvmName = ClassCarpenter::class.java.name.jvm
        with(visitMethod(ACC_PUBLIC, "get", "(Ljava/lang/String;)Ljava/lang/Object;", null, null)) {
            visitCode()
            visitVarInsn(ALOAD, 0)  // Load 'this'
            visitVarInsn(ALOAD, 1)  // Load the name argument
            // Using this generic helper method is slow, as it relies on reflection. A faster way would be
            // to use a tableswitch opcode, or just push back on the user and ask them to use actual reflection
            // or MethodHandles (super fast reflection) to access the object instead.
            visitMethodInsn(INVOKESTATIC, ourJvmName, "getField", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", false)
            visitInsn(ARETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
    }

    private fun ClassWriter.generateGetters(schema: Schema) {
        for ((name, type) in schema.fields) {
            with(visitMethod(ACC_PUBLIC, "get" + name.capitalize(), "()" + type.descriptor, null, null)) {
                type.addNullabilityAnnotation(this)
                visitCode()
                visitVarInsn(ALOAD, 0)  // Load 'this'
                visitFieldInsn(GETFIELD, schema.jvmName, name, type.descriptor)
                when (type.field) {
                    java.lang.Boolean.TYPE, Integer.TYPE, java.lang.Short.TYPE, java.lang.Byte.TYPE,
                            java.lang.Character.TYPE -> visitInsn(IRETURN)
                    java.lang.Long.TYPE -> visitInsn(LRETURN)
                    java.lang.Double.TYPE -> visitInsn(DRETURN)
                    java.lang.Float.TYPE -> visitInsn(FRETURN)
                    else -> visitInsn(ARETURN)
                }
                visitMaxs(0, 0)
                visitEnd()
            }
        }
    }

    private fun ClassWriter.generateAbstractGetters(schema: Schema) {
        for ((name, field) in schema.fields) {
            val opcodes = ACC_ABSTRACT + ACC_PUBLIC
            with(visitMethod(opcodes, "get" + name.capitalize(), "()${field.descriptor}", null, null)) {
                // abstract method doesn't have any implementation so just end
                visitEnd()
            }
        }
    }

    private fun ClassWriter.generateConstructor(schema: Schema) {
        with(visitMethod(
                ACC_PUBLIC,
                "<init>",
                "(" + schema.descriptorsIncludingSuperclasses().values.joinToString("") + ")V",
                null,
                null))
        {
            var idx = 0
            schema.fields.values.forEach { it.visitParameter(this, idx++) }

            visitCode()

            // Calculate the super call.
            val superclassFields = schema.superclass?.fieldsIncludingSuperclasses() ?: emptyMap()
            visitVarInsn(ALOAD, 0)
            if (schema.superclass == null) {
                visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            } else {
                var slot = 1
                for (fieldType in superclassFields.values)
                    slot += load(slot, fieldType)
                val superDesc = schema.superclass.descriptorsIncludingSuperclasses().values.joinToString("")
                visitMethodInsn(INVOKESPECIAL, schema.superclass.name.jvm, "<init>", "($superDesc)V", false)
            }

            // Assign the fields from parameters.
            var slot = 1 + superclassFields.size
            for ((name, field) in schema.fields.entries) {
                field.nullTest(this, slot)

                visitVarInsn(ALOAD, 0)  // Load 'this' onto the stack
                slot += load(slot, field)  // Load the contents of the parameter onto the stack.
                visitFieldInsn(PUTFIELD, schema.jvmName, name, field.descriptor)
            }
            visitInsn(RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
    }

    private fun MethodVisitor.load(slot: Int, type: Field): Int {
        when (type.field) {
            java.lang.Boolean.TYPE, Integer.TYPE, java.lang.Short.TYPE, java.lang.Byte.TYPE,
                    java.lang.Character.TYPE -> visitVarInsn(ILOAD, slot)
            java.lang.Long.TYPE -> visitVarInsn(LLOAD, slot)
            java.lang.Double.TYPE -> visitVarInsn(DLOAD, slot)
            java.lang.Float.TYPE -> visitVarInsn(FLOAD, slot)
            else -> visitVarInsn(ALOAD, slot)
        }
        return when (type.field) {
            java.lang.Long.TYPE, java.lang.Double.TYPE -> 2
            else -> 1
        }
    }

    private fun validateSchema(schema: Schema) {
        if (schema.name in _loaded) throw DuplicateNameException()
        fun isJavaName(n: String) = n.isNotBlank() && isJavaIdentifierStart(n.first()) && n.all(::isJavaIdentifierPart)
        require(isJavaName(schema.name.split(".").last())) { "Not a valid Java name: ${schema.name}" }
        schema.fields.keys.forEach { require(isJavaName(it)) { "Not a valid Java name: $it" } }
        // Now check each interface we've been asked to implement, as the JVM will unfortunately only catch the
        // fact that we didn't implement the interface we said we would at the moment the missing method is
        // actually called, which is a bit too dynamic for my tastes.
        val allFields = schema.fieldsIncludingSuperclasses()
        for (itf in schema.interfaces) {
            itf.methods.forEach {
                val fieldNameFromItf = when {
                    it.name.startsWith("get") -> it.name.substring(3).decapitalize()
                    else -> throw InterfaceMismatchException(
                            "Requested interfaces must consist only of methods that start "
                            + "with 'get': ${itf.name}.${it.name}")
                }

                if ((schema is ClassSchema) and (fieldNameFromItf !in allFields))
                    throw InterfaceMismatchException(
                            "Interface ${itf.name} requires a field named $fieldNameFromItf but that "
                            + "isn't found in the schema or any superclass schemas")
            }
        }
    }

    companion object {
        @JvmStatic @Suppress("UNUSED")
        fun getField(obj: Any, name: String): Any? = obj.javaClass.getMethod("get" + name.capitalize()).invoke(obj)
    }
}
