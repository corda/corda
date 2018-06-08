package net.corda.gradle.jarfilter

import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.ProtoBuf.Class.Kind.*
import org.jetbrains.kotlin.metadata.deserialization.Flags.*
import org.jetbrains.kotlin.metadata.deserialization.NameResolver
import org.jetbrains.kotlin.metadata.deserialization.TypeTable
import org.jetbrains.kotlin.metadata.jvm.JvmProtoBuf.*
import org.jetbrains.kotlin.metadata.jvm.deserialization.BitEncoding
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmNameResolver
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil.EXTENSION_REGISTRY
import org.jetbrains.kotlin.protobuf.ExtensionRegistryLite
import org.jetbrains.kotlin.protobuf.MessageLite
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal abstract class MetaFixTransformer<out T : MessageLite>(
    private val logger: Logger,
    private val actualFields: Collection<FieldElement>,
    private val actualMethods: Collection<String>,
    private val actualNestedClasses: Collection<String>,
    private val actualClasses: Collection<String>,
    d1: List<String>,
    d2: List<String>,
    parser: (InputStream, ExtensionRegistryLite) -> T
) {
    private val stringTableTypes: StringTableTypes
    private val nameResolver: NameResolver
    protected val message: T

    protected abstract val typeTable: TypeTable
    protected open val classKind: ProtoBuf.Class.Kind? = null
    protected abstract val properties: MutableList<ProtoBuf.Property>
    protected abstract val functions: MutableList<ProtoBuf.Function>
    protected abstract val constructors: MutableList<ProtoBuf.Constructor>
    protected open val nestedClassNames: MutableList<Int> get() = throw UnsupportedOperationException("No nestedClassNames")
    protected open val sealedSubclassNames: MutableList<Int> get() = throw UnsupportedOperationException("No sealedSubclassNames")

    init {
        val input = ByteArrayInputStream(BitEncoding.decodeBytes(d1.toTypedArray()))
        stringTableTypes = StringTableTypes.parseDelimitedFrom(input, EXTENSION_REGISTRY)
        nameResolver = JvmNameResolver(stringTableTypes, d2.toTypedArray())
        message = parser(input, EXTENSION_REGISTRY)
    }

    abstract fun rebuild(): T

    private fun filterNestedClasses(): Int {
        if (classKind == null) return 0

        var count = 0
        var idx = 0
        while (idx < nestedClassNames.size) {
            val nestedClassName = nameResolver.getString(nestedClassNames[idx])
            if (actualNestedClasses.contains(nestedClassName)) {
                ++idx
            } else {
                logger.info("-- removing nested class: {}", nestedClassName)
                nestedClassNames.removeAt(idx)
                ++count
            }
        }
        return count
    }

    private fun filterSealedSubclassNames(): Int {
        if (classKind == null) return 0

        var count = 0
        var idx = 0
        while (idx < sealedSubclassNames.size) {
            val sealedSubclassName = nameResolver.getString(sealedSubclassNames[idx]).replace('.', '$')
            if (actualClasses.contains(sealedSubclassName)) {
                ++idx
            } else {
                logger.info("-- removing sealed subclass: {}", sealedSubclassName)
                sealedSubclassNames.removeAt(idx)
                ++count
            }
        }
        return count
    }

    private fun filterFunctions(): Int {
        var count = 0
        var idx = 0
        while (idx < functions.size) {
            val signature = JvmProtoBufUtil.getJvmMethodSignature(functions[idx], nameResolver, typeTable)
            if ((signature == null) || actualMethods.contains(signature)) {
                ++idx
            } else {
                logger.info("-- removing method: {}", signature)
                functions.removeAt(idx)
                ++count
            }
        }
        return count
    }

    private fun filterConstructors(): Int {
        var count = 0
        var idx = 0
        while (idx < constructors.size) {
            val signature = JvmProtoBufUtil.getJvmConstructorSignature(constructors[idx], nameResolver, typeTable)
            if ((signature == null) || actualMethods.contains(signature)) {
                ++idx
            } else {
                logger.info("-- removing constructor: {}", signature)
                constructors.removeAt(idx)
                ++count
            }
        }
        return count
    }

    private fun filterProperties(): Int {
        var count = 0
        var idx = 0
        removed@ while (idx < properties.size) {
            val property = properties[idx]
            val signature = JvmProtoBufUtil.getJvmFieldSignature(property, nameResolver, typeTable)
            if (signature != null) {
                if (property.hasExtension(propertySignature)) {
                    val getterMethod = property.getExtension(propertySignature).toGetter(nameResolver)
                    val field = FieldElement(signature.name, signature.desc)

                    /**
                     * A property annotated with [JvmField] will use a field instead of a getter method.
                     * But properties without [JvmField] will also usually have a backing field. So we only
                     * remove a property that has either lost its getter method, or never had a getter method
                     * and has lost its field.
                     *
                     * Having said that, we cannot remove [JvmField] properties from a companion object class
                     * because these properties are implemented as static fields on the companion's host class.
                     */
                    val isValidProperty = if (getterMethod == null) {
                        actualFields.contains(field) || classKind == COMPANION_OBJECT
                    } else {
                        actualMethods.contains(getterMethod.name + getterMethod.descriptor)
                    }

                    if (!isValidProperty) {
                        logger.info("-- removing property: {},{}", field.name, field.descriptor)
                        properties.removeAt(idx)
                        ++count
                        continue@removed
                    }
                }
            }
            ++idx
        }
        return count
    }

    fun transform(): List<String> {
        var count = filterProperties() + filterFunctions() + filterNestedClasses() + filterSealedSubclassNames()
        if (classKind != ANNOTATION_CLASS) {
            count += filterConstructors()
        }
        if (count == 0) {
            return emptyList()
        }

        val bytes = ByteArrayOutputStream()
        stringTableTypes.writeDelimitedTo(bytes)
        rebuild().writeTo(bytes)
        return BitEncoding.encodeBytes(bytes.toByteArray()).toList()
    }
}

internal class ClassMetaFixTransformer(
    logger: Logger,
    actualFields: Collection<FieldElement>,
    actualMethods: Collection<String>,
    actualNestedClasses: Collection<String>,
    actualClasses: Collection<String>,
    d1: List<String>,
    d2: List<String>
) : MetaFixTransformer<ProtoBuf.Class>(
    logger,
    actualFields,
    actualMethods,
    actualNestedClasses,
    actualClasses,
    d1,
    d2,
    ProtoBuf.Class::parseFrom
) {
    override val typeTable = TypeTable(message.typeTable)
    override val classKind: ProtoBuf.Class.Kind = CLASS_KIND.get(message.flags)
    override val properties = mutableList(message.propertyList)
    override val functions = mutableList(message.functionList)
    override val constructors = mutableList(message.constructorList)
    override val nestedClassNames = mutableList(message.nestedClassNameList)
    override val sealedSubclassNames= mutableList(message.sealedSubclassFqNameList)

    override fun rebuild(): ProtoBuf.Class = message.toBuilder().apply {
        if (nestedClassNames.size != nestedClassNameCount) {
            clearNestedClassName().addAllNestedClassName(nestedClassNames)
        }
        if (sealedSubclassNames.size != sealedSubclassFqNameCount) {
            clearSealedSubclassFqName().addAllSealedSubclassFqName(sealedSubclassNames)
        }
        if (constructors.size != constructorCount) {
            clearConstructor().addAllConstructor(constructors)
        }
        if (functions.size != functionCount) {
            clearFunction().addAllFunction(functions)
        }
        if (properties.size != propertyCount) {
            clearProperty().addAllProperty(properties)
        }
    }.build()
}

internal class PackageMetaFixTransformer(
    logger: Logger,
    actualFields: Collection<FieldElement>,
    actualMethods: Collection<String>,
    d1: List<String>,
    d2: List<String>
) : MetaFixTransformer<ProtoBuf.Package>(
    logger,
    actualFields,
    actualMethods,
    emptyList(),
    emptyList(),
    d1,
    d2,
    ProtoBuf.Package::parseFrom
) {
    override val typeTable = TypeTable(message.typeTable)
    override val properties = mutableList(message.propertyList)
    override val functions = mutableList(message.functionList)
    override val constructors = mutableListOf<ProtoBuf.Constructor>()

    override fun rebuild(): ProtoBuf.Package = message.toBuilder().apply {
        if (functions.size != functionCount) {
            clearFunction().addAllFunction(functions)
        }
        if (properties.size != propertyCount) {
            clearProperty().addAllProperty(properties)
        }
    }.build()
}
