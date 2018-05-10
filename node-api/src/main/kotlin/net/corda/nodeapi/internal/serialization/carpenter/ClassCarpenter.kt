package net.corda.nodeapi.internal.serialization.carpenter

import com.google.common.base.MoreObjects
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.CordaSerializable
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import java.lang.Character.isJavaIdentifierPart
import java.lang.Character.isJavaIdentifierStart
import java.lang.reflect.Method
import java.util.*

/**
 * Any object that implements this interface is expected to expose its own fields via the [get] method, exactly
 * as if `this.class.getMethod("get" + name.capitalize()).invoke(this)` had been called. It is intended as a more
 * convenient alternative to reflection.
 */
@CordaSerializable
interface SimpleFieldAccess {
    operator fun get(name: String): Any?
}

class CarpenterClassLoader(parentClassLoader: ClassLoader = Thread.currentThread().contextClassLoader) :
        ClassLoader(parentClassLoader) {
    fun load(name: String, bytes: ByteArray): Class<*> = defineClass(name, bytes, 0, bytes.size)
}

class InterfaceMismatchNonGetterException(val clazz: Class<*>, val method: Method) : InterfaceMismatchException(
        "Requested interfaces must consist only of methods that start with 'get': ${clazz.name}.${method.name}")

class InterfaceMismatchMissingAMQPFieldException(val clazz: Class<*>, val field: String) : InterfaceMismatchException(
        "Interface ${clazz.name} requires a field named $field but that isn't found in the schema or any superclass schemas")

/**
 * Which version of the java runtime are we constructing objects against
 */
private const val TARGET_VERSION = V1_8

private val jlEnum: String = Type.getInternalName(Enum::class.java)
private val jlString: String = Type.getInternalName(String::class.java)
private val jlObject: String = Type.getInternalName(Object::class.java)
private val jlClass: String = Type.getInternalName(Class::class.java)
private val moreObjects: String = Type.getInternalName(MoreObjects::class.java)
private val toStringHelper: String = Type.getInternalName(MoreObjects.ToStringHelper::class.java)

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
class ClassCarpenter(cl: ClassLoader, val whitelist: ClassWhitelist) {
    constructor(whitelist: ClassWhitelist) : this(Thread.currentThread().contextClassLoader, whitelist)

    // TODO: Generics.
    // TODO: Sandbox the generated code when a security manager is in use.
    // TODO: Generate equals/hashCode.
    // TODO: Support annotations.
    // TODO: isFoo getter patterns for booleans (this is what Kotlin generates)

    val classloader = CarpenterClassLoader(cl)

    private val _loaded = HashMap<String, Class<*>>()

    /** Returns a snapshot of the currently loaded classes as a map of full class name (package names+dots) -> class object */
    val loaded: Map<String, Class<*>> = HashMap(_loaded)

    /**
     * Generate bytecode for the given schema and load into the JVM. The returned class object can be used to
     * construct instances of the generated class.
     *
     * @throws DuplicateNameException if the schema's name is already taken in this namespace (you can create a
     * new ClassCarpenter if you're OK with ambiguous names)
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
                is EnumSchema -> generateEnum(it)
            }
        }

        assert(schema.name in _loaded)

        return _loaded[schema.name]!!
    }

    private fun generateEnum(enumSchema: Schema): Class<*> {
        return generate(enumSchema) { cw, schema ->
            cw.apply {
                visit(TARGET_VERSION, ACC_PUBLIC + ACC_FINAL + ACC_SUPER + ACC_ENUM, schema.jvmName,
                        "L$jlEnum<L${schema.jvmName};>;", jlEnum, null)

                if (schema.flags.cordaSerializable()) {
                    visitAnnotation(Type.getDescriptor(CordaSerializable::class.java), true).visitEnd()
                }

                generateFields(schema)
                generateStaticEnumConstructor(schema)
                generateEnumConstructor()
                generateEnumValues(schema)
                generateEnumValueOf(schema)
            }.visitEnd()
        }
    }

    private fun generateInterface(interfaceSchema: Schema): Class<*> {
        return generate(interfaceSchema) { cw, schema ->
            val interfaces = schema.interfaces.map { Type.getInternalName(it) }.toTypedArray()

            cw.apply {
                visit(TARGET_VERSION, ACC_PUBLIC + ACC_ABSTRACT + ACC_INTERFACE, schema.jvmName, null,
                        jlObject, interfaces)

                if (schema.flags.cordaSerializable()) {
                    visitAnnotation(Type.getDescriptor(CordaSerializable::class.java), true).visitEnd()
                }
                generateAbstractGetters(schema)
            }.visitEnd()
        }
    }

    private fun generateClass(classSchema: Schema): Class<*> {
        return generate(classSchema) { cw, schema ->
            val superName = schema.superclass?.jvmName ?: jlObject
            val interfaces = schema.interfaces.map { Type.getInternalName(it) }.toMutableList()

            if (SimpleFieldAccess::class.java !in schema.interfaces
                    && schema.flags.cordaSerializable()
                    && schema.flags.simpleFieldAccess()) {
                interfaces.add(Type.getInternalName(SimpleFieldAccess::class.java))
            }

            cw.apply {
                visit(TARGET_VERSION, ACC_PUBLIC + ACC_SUPER, schema.jvmName, null, superName,
                        interfaces.toTypedArray())

                if (schema.flags.cordaSerializable()) {
                    visitAnnotation(Type.getDescriptor(CordaSerializable::class.java), true).visitEnd()
                }
                generateFields(schema)
                generateClassConstructor(schema)
                generateGetters(schema)
                if (schema.superclass == null) {
                    generateGetMethod()   // From SimplePropertyAccess
                }
                generateToString(schema)
            }.visitEnd()
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
        schema.generateFields(this)
    }

    private fun ClassWriter.generateToString(schema: Schema) {
        with(visitMethod(ACC_PUBLIC, "toString", "()L$jlString;", null, null)) {
            visitCode()
            // com.google.common.base.MoreObjects.toStringHelper("TypeName")
            visitLdcInsn(schema.name.split('.').last())
            visitMethodInsn(INVOKESTATIC, moreObjects, "toStringHelper",
                    "(L$jlString;)L$toStringHelper;", false)
            // Call the add() methods.
            for ((name, field) in schema.fieldsIncludingSuperclasses().entries) {
                visitLdcInsn(name)
                visitVarInsn(ALOAD, 0)  // this
                visitFieldInsn(GETFIELD, schema.jvmName, name, schema.descriptorsIncludingSuperclasses()[name])
                visitMethodInsn(INVOKEVIRTUAL, toStringHelper, "add", "(L$jlString;${field.type})L$toStringHelper;", false)
            }
            // call toString() on the builder and return.
            visitMethodInsn(INVOKEVIRTUAL, toStringHelper, "toString", "()L$jlString;", false)
            visitInsn(ARETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
    }

    private fun ClassWriter.generateGetMethod() {
        val ourJvmName = Type.getInternalName(ClassCarpenter::class.java)
        with(visitMethod(ACC_PUBLIC, "get", "(L$jlString;)L$jlObject;", null, null)) {
            visitCode()
            visitVarInsn(ALOAD, 0)  // Load 'this'
            visitVarInsn(ALOAD, 1)  // Load the name argument
            // Using this generic helper method is slow, as it relies on reflection. A faster way would be
            // to use a tableswitch opcode, or just push back on the user and ask them to use actual reflection
            // or MethodHandles (super fast reflection) to access the object instead.
            visitMethodInsn(INVOKESTATIC, ourJvmName, "getField", "(L$jlObject;L$jlString;)L$jlObject;", false)
            visitInsn(ARETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
    }

    private fun ClassWriter.generateGetters(schema: Schema) {
        for ((name, type) in schema.fields) {
            visitMethod(ACC_PUBLIC, "get" + name.capitalize(), "()" + type.descriptor, null, null).apply {
                (type as ClassField).addNullabilityAnnotation(this)
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
            }.visitEnd()
        }
    }

    private fun ClassWriter.generateAbstractGetters(schema: Schema) {
        for ((name, field) in schema.fields) {
            val opcodes = ACC_ABSTRACT + ACC_PUBLIC
            // abstract method doesn't have any implementation so just end
            visitMethod(opcodes, "get" + name.capitalize(), "()${field.descriptor}", null, null).visitEnd()
        }
    }

    private fun ClassWriter.generateStaticEnumConstructor(schema: Schema) {
        visitMethod(ACC_STATIC, "<clinit>", "()V", null, null).apply {
            visitCode()
            visitIntInsn(BIPUSH, schema.fields.size)
            visitTypeInsn(ANEWARRAY, schema.jvmName)
            visitInsn(DUP)

            var idx = 0
            schema.fields.forEach {
                visitInsn(DUP)
                visitIntInsn(BIPUSH, idx)
                visitTypeInsn(NEW, schema.jvmName)
                visitInsn(DUP)
                visitLdcInsn(it.key)
                visitIntInsn(BIPUSH, idx++)
                visitMethodInsn(INVOKESPECIAL, schema.jvmName, "<init>", "(L$jlString;I)V", false)
                visitInsn(DUP)
                visitFieldInsn(PUTSTATIC, schema.jvmName, it.key, "L${schema.jvmName};")
                visitInsn(AASTORE)
            }

            visitFieldInsn(PUTSTATIC, schema.jvmName, "\$VALUES", schema.asArray)
            visitInsn(RETURN)

            visitMaxs(0, 0)
        }.visitEnd()
    }

    private fun ClassWriter.generateEnumValues(schema: Schema) {
        visitMethod(ACC_PUBLIC + ACC_STATIC, "values", "()${schema.asArray}", null, null).apply {
            visitCode()
            visitFieldInsn(GETSTATIC, schema.jvmName, "\$VALUES", schema.asArray)
            visitMethodInsn(INVOKEVIRTUAL, schema.asArray, "clone", "()L$jlObject;", false)
            visitTypeInsn(CHECKCAST, schema.asArray)
            visitInsn(ARETURN)
            visitMaxs(0, 0)
        }.visitEnd()
    }

    private fun ClassWriter.generateEnumValueOf(schema: Schema) {
        visitMethod(ACC_PUBLIC + ACC_STATIC, "valueOf", "(L$jlString;)L${schema.jvmName};", null, null).apply {
            visitCode()
            visitLdcInsn(Type.getType("L${schema.jvmName};"))
            visitVarInsn(ALOAD, 0)
            visitMethodInsn(INVOKESTATIC, jlEnum, "valueOf", "(L$jlClass;L$jlString;)L$jlEnum;", true)
            visitTypeInsn(CHECKCAST, schema.jvmName)
            visitInsn(ARETURN)
            visitMaxs(0, 0)
        }.visitEnd()

    }

    private fun ClassWriter.generateEnumConstructor() {
        visitMethod(ACC_PROTECTED, "<init>", "(L$jlString;I)V", "()V", null).apply {
            visitParameter("\$enum\$name", ACC_SYNTHETIC)
            visitParameter("\$enum\$ordinal", ACC_SYNTHETIC)

            visitCode()

            visitVarInsn(ALOAD, 0) // this
            visitVarInsn(ALOAD, 1)
            visitVarInsn(ILOAD, 2)
            visitMethodInsn(INVOKESPECIAL, jlEnum, "<init>", "(L$jlString;I)V", false)
            visitInsn(RETURN)

            visitMaxs(0, 0)
        }.visitEnd()
    }

    private fun ClassWriter.generateClassConstructor(schema: Schema) {
        visitMethod(
                ACC_PUBLIC,
                "<init>",
                "(" + schema.descriptorsIncludingSuperclasses().values.joinToString("") + ")V",
                null,
                null).apply {
            var idx = 0

            schema.fields.values.forEach { it.visitParameter(this, idx++) }

            visitCode()

            // Calculate the super call.
            val superclassFields = schema.superclass?.fieldsIncludingSuperclasses() ?: emptyMap()
            visitVarInsn(ALOAD, 0)
            val sc = schema.superclass
            if (sc == null) {
                visitMethodInsn(INVOKESPECIAL, jlObject, "<init>", "()V", false)
            } else {
                var slot = 1
                superclassFields.values.forEach { slot += load(slot, it) }
                val superDesc = sc.descriptorsIncludingSuperclasses().values.joinToString("")
                visitMethodInsn(INVOKESPECIAL, sc.jvmName, "<init>", "($superDesc)V", false)
            }

            // Assign the fields from parameters.
            var slot = 1 + superclassFields.size
            for ((name, field) in schema.fields) {
                (field as ClassField).nullTest(this, slot)

                visitVarInsn(ALOAD, 0)  // Load 'this' onto the stack
                slot += load(slot, field)  // Load the contents of the parameter onto the stack.
                visitFieldInsn(PUTFIELD, schema.jvmName, name, field.descriptor)
            }
            visitInsn(RETURN)
            visitMaxs(0, 0)
        }.visitEnd()
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

    /**
     * If a sub element isn't whitelist we will not build a class containing that type as a member. Since, by
     * default, classes created by the [ClassCarpenter] are annotated as [CordaSerializable] we will always
     * be able to carpent classes generated from our AMQP library as, at a base level, we will either be able to
     * create the lowest level in the meta hierarchy because either all members are jvm primitives or
     * whitelisted classes
     */
    private fun validateSchema(schema: Schema) {
        if (schema.name in _loaded) throw DuplicateNameException(schema.name)
        fun isJavaName(n: String) = n.isNotBlank() && isJavaIdentifierStart(n.first()) && n.all(::isJavaIdentifierPart)
        require(isJavaName(schema.name.split(".").last())) { "Not a valid Java name: ${schema.name}" }
        schema.fields.forEach {
            require(isJavaName(it.key)) { "Not a valid Java name: $it" }
        }

        // Now check each interface we've been asked to implement, as the JVM will unfortunately only catch the
        // fact that we didn't implement the interface we said we would at the moment the missing method is
        // actually called, which is a bit too dynamic for my tastes.
        val allFields = schema.fieldsIncludingSuperclasses()
        for (itf in schema.interfaces) {
            itf.methods.forEach {
                val fieldNameFromItf = when {
                    it.name.startsWith("get") -> it.name.substring(3).decapitalize()
                    else -> throw InterfaceMismatchNonGetterException(itf, it)
                }

                // If we're trying to carpent a class that prior to serialisation / deserialization
                // was made by a carpenter then we can ignore this (it will implement a plain get
                // method from SimpleFieldAccess).
                if (fieldNameFromItf.isEmpty() && SimpleFieldAccess::class.java in schema.interfaces) return@forEach

                if ((schema is ClassSchema) and (fieldNameFromItf !in allFields)) {
                    throw InterfaceMismatchMissingAMQPFieldException(itf, fieldNameFromItf)
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @Suppress("UNUSED")
        fun getField(obj: Any, name: String): Any? = obj.javaClass.getMethod("get" + name.capitalize()).invoke(obj)
    }
}
