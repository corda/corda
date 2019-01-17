package net.corda.core.internal.reflection

import net.corda.core.KeepForDJVM
import java.util.*

@KeepForDJVM
data class PropertyEnumerator(private val readers: Map<String, PropertyReader>): (Any) -> Sequence<Pair<String, Any?>> {

    companion object {
        fun forType(typeInformation: LocalTypeInformation) =
                PropertyEnumerator(typeInformation.propertiesOrEmptyMap.asSequence().mapNotNull { (name, property) ->
                    if (property.isCalculated) null else name to PropertyReader.make(property)
                }.toMap())
    }

    override fun invoke(value: Any): Sequence<Pair<String, Any?>> =
            readers.asSequence().map { (name, reader) -> name to reader.read(value) }
}

@KeepForDJVM
class ObjectGraphTraverser private constructor(private val localTypeModel: LocalTypeModel) {

    companion object {
        fun traverse(value: Any?,
                     localTypeModel: LocalTypeModel = LocalTypeModel.unconstrained,
                     filter: (Any) -> Boolean = { true }): Sequence<Any> =
            ObjectGraphTraverser(localTypeModel).traverse(value, filter)
    }

    private val propertyEnumeratorCache = DefaultCacheProvider.createCache<TypeIdentifier, PropertyEnumerator>()
    private val alreadySeen: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())

    private fun traverse(value: Any?, filter: (Any) -> Boolean = { true }): Sequence<Any> {
        if (value == null || value in alreadySeen) return emptySequence()

        alreadySeen += value
        val typeInformation = localTypeModel.inspect(value::class.java)

        val converter = propertyEnumeratorCache.getOrPut(typeInformation.typeIdentifier) {
            PropertyEnumerator.forType(typeInformation)
        }

        return converter(value).flatMap { (_, propertyValue) -> traverse(propertyValue, filter) } +
                if (filter(value)) sequenceOf(value) else emptySequence()
    }
}