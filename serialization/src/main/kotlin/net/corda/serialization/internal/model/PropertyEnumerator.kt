package net.corda.serialization.internal.model

import net.corda.serialization.internal.amqp.LocalSerializerFactory
import java.lang.reflect.Type

data class PropertyEnumerator(private val readers: Map<String, PropertyReader>): (Any) -> Sequence<Pair<String, Any?>> {

    companion object {
        fun forType(typeInformation: LocalTypeInformation) =
                PropertyEnumerator(typeInformation.propertiesOrEmptyMap.mapValues { (_, property) ->
                    PropertyReader.make(property)
                })
    }

    override fun invoke(value: Any): Sequence<Pair<String, Any?>> =
            readers.asSequence().map { (name, reader) -> name to reader.read(value) }
}

class ObjectGraphTraverser(private val typeInformationProvider: (Type) -> LocalTypeInformation) {

    constructor(factory: LocalSerializerFactory) : this(factory::getTypeInformation)

    private val propertyEnumeratorCache = DefaultCacheProvider.createCache<TypeIdentifier, PropertyEnumerator>()
    private val alreadySeen: MutableSet<Any> = mutableSetOf()

    fun traverseGraph(value: Any?): Sequence<Any> {
        if (value == null || value in alreadySeen) return emptySequence()

        alreadySeen += value
        val typeInformation = typeInformationProvider(value::class.java)

        val converter = propertyEnumeratorCache.getOrPut(typeInformation.typeIdentifier) {
            PropertyEnumerator.forType(typeInformation)
        }

        return converter(value).flatMap { (_, propertyValue) -> traverseGraph(propertyValue) } + sequenceOf(value)
    }
}