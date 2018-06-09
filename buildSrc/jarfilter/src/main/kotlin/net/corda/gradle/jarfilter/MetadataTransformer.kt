package net.corda.gradle.jarfilter

import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.metadata.ProtoBuf
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

/**
 * Base class for removing unwanted elements from [kotlin.Metadata] annotations.
 * This is used by [ClassTransformer] for [JarFilterTask].
 */
internal abstract class MetadataTransformer<out T : MessageLite>(
    private val logger: Logger,
    private val deletedFields: Collection<FieldElement>,
    private val deletedFunctions: Collection<MethodElement>,
    private val deletedConstructors: Collection<MethodElement>,
    private val deletedNestedClasses: Collection<String>,
    private val deletedClasses: Collection<String>,
    private val handleExtraMethod: (MethodElement) -> Unit,
    d1: List<String>,
    d2: List<String>,
    parser: (InputStream, ExtensionRegistryLite) -> T
) {
    private val stringTableTypes: StringTableTypes
    protected val nameResolver: NameResolver
    protected val message: T

    protected abstract val typeTable: TypeTable
    protected open val className: String get() = throw UnsupportedOperationException("No className")
    protected open val nestedClassNames: MutableList<Int> get() = throw UnsupportedOperationException("No nestedClassNames")
    protected open val sealedSubclassNames: MutableList<Int> get() = throw UnsupportedOperationException("No sealedSubclassNames")
    protected abstract val properties: MutableList<ProtoBuf.Property>
    protected abstract val functions: MutableList<ProtoBuf.Function>
    protected open val constructors: MutableList<ProtoBuf.Constructor> get() = throw UnsupportedOperationException("No constructors")
    protected abstract val typeAliases: MutableList<ProtoBuf.TypeAlias>

    init {
        val input = ByteArrayInputStream(BitEncoding.decodeBytes(d1.toTypedArray()))
        stringTableTypes = StringTableTypes.parseDelimitedFrom(input, EXTENSION_REGISTRY)
        nameResolver = JvmNameResolver(stringTableTypes, d2.toTypedArray())
        message = parser(input, EXTENSION_REGISTRY)
    }

    abstract fun rebuild(): T

    fun transform(): List<String> {
        val count = (
            filterProperties()
            + filterFunctions()
            + filterConstructors()
            + filterNestedClasses()
            + filterTypeAliases()
            + filterSealedSubclasses()
        )
        if (count == 0) {
            return emptyList()
        }

        val bytes = ByteArrayOutputStream()
        stringTableTypes.writeDelimitedTo(bytes)
        rebuild().writeTo(bytes)
        return BitEncoding.encodeBytes(bytes.toByteArray()).toList()
    }

    private fun filterNestedClasses(): Int {
        if (deletedNestedClasses.isEmpty()) return 0

        var count = 0
        var idx = 0
        while (idx < nestedClassNames.size) {
            val nestedClassName = nameResolver.getString(nestedClassNames[idx])
            if (deletedNestedClasses.contains(nestedClassName)) {
                logger.info("-- removing nested class: {}", nestedClassName)
                nestedClassNames.removeAt(idx)
                ++count
            } else {
                ++idx
            }
        }
        return count
    }

    private fun filterConstructors(): Int = deletedConstructors.count(::filterConstructor)

    private fun filterConstructor(deleted: MethodElement): Boolean {
        for (idx in 0 until constructors.size) {
            val constructor = constructors[idx]
            val signature = JvmProtoBufUtil.getJvmConstructorSignature(constructor, nameResolver, typeTable)
            if (signature == deleted.name + deleted.descriptor) {
                if (IS_SECONDARY.get(constructor.flags)) {
                    logger.info("-- removing constructor: {}{}", deleted.name, deleted.descriptor)
                } else {
                    logger.warn("Removing primary constructor: {}{}", className, deleted.descriptor)
                }
                constructors.removeAt(idx)
                return true
            }
        }
        return false
    }

    private fun filterFunctions(): Int = deletedFunctions.count(::filterFunction)

    private fun filterFunction(deleted: MethodElement): Boolean {
        for (idx in 0 until functions.size) {
            val function = functions[idx]
            if (nameResolver.getString(function.name) == deleted.name) {
                val signature = JvmProtoBufUtil.getJvmMethodSignature(function, nameResolver, typeTable)
                if (signature == deleted.name + deleted.descriptor) {
                    logger.info("-- removing function: {}{}", deleted.name, deleted.descriptor)
                    functions.removeAt(idx)
                    return true
                }
            }
        }
        return false
    }

    private fun filterProperties(): Int = deletedFields.count(::filterProperty)

    private fun filterProperty(deleted: FieldElement): Boolean {
        for (idx in 0 until properties.size) {
            val property = properties[idx]
            val signature = JvmProtoBufUtil.getJvmFieldSignature(property, nameResolver, typeTable) ?: continue
            if (signature.name.toVisible() == deleted.name) {
                // Check that this property's getter has the correct descriptor.
                // If it doesn't then we have the wrong property here.
                if (property.hasExtension(propertySignature)) {
                    val ext = property.getExtension(propertySignature)
                    val getter = ext.toGetter(nameResolver)
                    if (getter != null) {
                        if (!getter.descriptor.startsWith(deleted.extension)) {
                            continue
                        }
                        deleteExtra(getter)
                    }
                    ext.toSetter(nameResolver)?.apply(::deleteExtra)
                }

                logger.info("-- removing property: {},{}", signature.name, signature.desc)
                properties.removeAt(idx)
                return true
            }
        }
        return false
    }

    private fun deleteExtra(func: MethodElement) {
        if (!deletedFunctions.contains(func)) {
            logger.info("-- identified extra method {}{} for deletion", func.name, func.descriptor)
            handleExtraMethod(func)
            filterFunction(func)
        }
    }

    private fun filterTypeAliases(): Int {
        if (deletedFields.isEmpty()) return 0

        var count = 0
        var idx = 0
        while (idx < typeAliases.size) {
            val aliasName = nameResolver.getString(typeAliases[idx].name)
            if (deletedFields.any { it.name == aliasName && it.extension == "()" }) {
                logger.info("-- removing typealias: {}", aliasName)
                typeAliases.removeAt(idx)
                ++count
            } else {
                ++idx
            }
        }
        return count
    }

    private fun filterSealedSubclasses(): Int {
        if (deletedClasses.isEmpty()) return 0

        var count = 0
        var idx = 0
        while (idx < sealedSubclassNames.size) {
            val subclassName = nameResolver.getString(sealedSubclassNames[idx]).replace('.', '$')
            if (deletedClasses.contains(subclassName)) {
                logger.info("-- removing sealed subclass: {}", subclassName)
                sealedSubclassNames.removeAt(idx)
                ++count
            } else {
                ++idx
            }
        }
        return count
    }

    /**
     * Removes any Kotlin suffix, e.g. "$delegate" or "$annotations".
     */
    private fun String.toVisible(): String {
        val idx = indexOf('$')
        return if (idx == -1) this else substring(0, idx)
    }
}

/**
 * Removes elements from a [kotlin.Metadata] annotation that contains
 * a [ProtoBuf.Class] object in its [d1][kotlin.Metadata.d1] field.
 */
internal class ClassMetadataTransformer(
    logger: Logger,
    deletedFields: Collection<FieldElement>,
    deletedFunctions: Collection<MethodElement>,
    deletedConstructors: Collection<MethodElement>,
    deletedNestedClasses: Collection<String>,
    deletedClasses: Collection<String>,
    handleExtraMethod: (MethodElement) -> Unit,
    d1: List<String>,
    d2: List<String>
) : MetadataTransformer<ProtoBuf.Class>(
    logger,
    deletedFields,
    deletedFunctions,
    deletedConstructors,
    deletedNestedClasses,
    deletedClasses,
    handleExtraMethod,
    d1,
    d2,
    ProtoBuf.Class::parseFrom
) {
    override val typeTable = TypeTable(message.typeTable)
    override val className = nameResolver.getString(message.fqName)
    override val nestedClassNames = mutableList(message.nestedClassNameList)
    override val sealedSubclassNames = mutableList(message.sealedSubclassFqNameList)
    override val properties = mutableList(message.propertyList)
    override val functions = mutableList(message.functionList)
    override val constructors = mutableList(message.constructorList)
    override val typeAliases = mutableList(message.typeAliasList)

    override fun rebuild(): ProtoBuf.Class = message.toBuilder().apply {
        if (nestedClassNames.size != nestedClassNameCount) {
            clearNestedClassName().addAllNestedClassName(nestedClassNames)
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
        if (typeAliases.size != typeAliasCount) {
            clearTypeAlias().addAllTypeAlias(typeAliases)
        }
        if (sealedSubclassNames.size != sealedSubclassFqNameCount) {
            clearSealedSubclassFqName().addAllSealedSubclassFqName(sealedSubclassNames)
        }
    }.build()
}

/**
 * Removes elements from a [kotlin.Metadata] annotation that contains
 * a [ProtoBuf.Package] object in its [d1][kotlin.Metadata.d1] field.
 */
internal class PackageMetadataTransformer(
    logger: Logger,
    deletedFields: Collection<FieldElement>,
    deletedFunctions: Collection<MethodElement>,
    handleExtraMethod: (MethodElement) -> Unit,
    d1: List<String>,
    d2: List<String>
) : MetadataTransformer<ProtoBuf.Package>(
    logger,
    deletedFields,
    deletedFunctions,
    emptyList(),
    emptyList(),
    emptyList(),
    handleExtraMethod,
    d1,
    d2,
    ProtoBuf.Package::parseFrom
) {
    override val typeTable = TypeTable(message.typeTable)
    override val properties = mutableList(message.propertyList)
    override val functions = mutableList(message.functionList)
    override val typeAliases = mutableList(message.typeAliasList)

    override fun rebuild(): ProtoBuf.Package = message.toBuilder().apply {
        if (functions.size != functionCount) {
            clearFunction().addAllFunction(functions)
        }
        if (properties.size != propertyCount) {
            clearProperty().addAllProperty(properties)
        }
        if (typeAliases.size != typeAliasCount) {
            clearTypeAlias().addAllTypeAlias(typeAliases)
        }
    }.build()
}
