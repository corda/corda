package net.corda.gradle.jarfilter.asm

import net.corda.gradle.jarfilter.MetadataTransformer
import net.corda.gradle.jarfilter.toPackageFormat
import net.corda.gradle.jarfilter.mutableList
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.deserialization.TypeTable

internal class ClassMetadata(
    logger: Logger,
    d1: List<String>,
    d2: List<String>
) : MetadataTransformer<ProtoBuf.Class>(
    logger,
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList(),
    {},
    d1,
    d2,
    ProtoBuf.Class::parseFrom
) {
    override val typeTable = TypeTable(message.typeTable)
    override val className = nameResolver.getString(message.fqName)
    override val nestedClassNames = mutableList(message.nestedClassNameList)
    override val properties = mutableList(message.propertyList)
    override val functions = mutableList(message.functionList)
    override val constructors = mutableList(message.constructorList)
    override val typeAliases = mutableList(message.typeAliasList)
    override val sealedSubclassNames = mutableList(message.sealedSubclassFqNameList)

    override fun rebuild(): ProtoBuf.Class = message

    val sealedSubclasses: List<String> = sealedSubclassNames.map {
        // Transform "a/b/c/BaseName.SubclassName" -> "a.b.c.BaseName$SubclassName"
        nameResolver.getString(it).replace('.', '$').toPackageFormat }.toList()

    val nestedClasses: List<String>

    init {
        val internalClassName = className.toPackageFormat
        nestedClasses = nestedClassNames.map { "$internalClassName\$${nameResolver.getString(it)}" }.toList()
    }
}
