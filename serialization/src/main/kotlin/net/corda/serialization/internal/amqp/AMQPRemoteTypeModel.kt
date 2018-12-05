package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.NotSerializableDetailedException
import net.corda.serialization.internal.model.*
import java.io.NotSerializableException
import kotlin.collections.LinkedHashMap

/**
 * Interprets AMQP [Schema] information to obtain [RemoteTypeInformation], caching by [TypeDescriptor].
 */
class AMQPRemoteTypeModel {

    private val cache: MutableMap<TypeDescriptor, RemoteTypeInformation> = DefaultCacheProvider.createCache()

    /**
     * Interpret a [Schema] to obtain a [Map] of all of the [RemoteTypeInformation] contained therein, indexed by
     * [TypeDescriptor].
     *
     * A [Schema] contains a set of [TypeNotation]s, which we recursively convert into [RemoteTypeInformation],
     * associating each new piece of [RemoteTypeInformation] with the [TypeDescriptor] attached to it in the schema.
     *
     * We start by building a [Map] of [TypeNotation] by [TypeIdentifier], using [AMQPTypeIdentifierParser] to convert
     * AMQP type names into [TypeIdentifier]s. This is used as a lookup for resolving notations that are referred to by
     * type name from other notations, e.g. the types of properties.
     *
     * We also build a [Map] of [TypeNotation] by [TypeDescriptor], which we then convert into [RemoteTypeInformation]
     * while merging with the cache.
     */
    fun interpret(serializationSchemas: SerializationSchemas): Map<TypeDescriptor, RemoteTypeInformation> {
        val (schema, transforms) = serializationSchemas
        val notationLookup = schema.types.associateBy { it.name.typeIdentifier }
        val byTypeDescriptor = schema.types.associateBy { it.typeDescriptor }
        val enumTransformsLookup = transforms.types.asSequence().map { (name, transformSet) ->
            name.typeIdentifier to transformSet
        }.toMap()

        val interpretationState = InterpretationState(notationLookup, enumTransformsLookup, cache, emptySet())

        val result = byTypeDescriptor.mapValues { (typeDescriptor, typeNotation) ->
            cache.getOrPut(typeDescriptor) { interpretationState.run { typeNotation.name.typeIdentifier.interpretIdentifier() } }
        }
        val typesByIdentifier = result.values.associateBy { it.typeIdentifier }
        result.values.forEach { typeInformation ->
            if (typeInformation is RemoteTypeInformation.Cycle) {
                typeInformation.follow = typesByIdentifier[typeInformation.typeIdentifier] ?:
                        throw NotSerializableException("Cannot resolve cyclic reference to ${typeInformation.typeIdentifier}")
            }
        }
        return result
    }

    data class InterpretationState(val notationLookup: Map<TypeIdentifier, TypeNotation>,
                                   val enumTransformsLookup: Map<TypeIdentifier, TransformsMap>,
                                   val cache: MutableMap<TypeDescriptor, RemoteTypeInformation>,
                                   val seen: Set<TypeIdentifier>) {

        private inline fun <T> withSeen(typeIdentifier: TypeIdentifier, block: InterpretationState.() -> T): T =
                withSeen(seen + typeIdentifier, block)

        private inline fun <T> withSeen(seen: Set<TypeIdentifier>, block: InterpretationState.() -> T): T =
                copy(seen = seen).run(block)

        /**
         * Follow a [TypeIdentifier] to the [TypeNotation] associated with it in the lookup, and interpret that notation.
         * If there is no such notation, interpret the [TypeIdentifier] directly into [RemoteTypeInformation].
         *
         * If we have visited this [TypeIdentifier] before while traversing the graph of related [TypeNotation]s, then we
         * know we have hit a cycle and respond accordingly.
         */
        fun TypeIdentifier.interpretIdentifier(): RemoteTypeInformation =
            if (this in seen) RemoteTypeInformation.Cycle(this)
            else withSeen(this) {
                val identifier = this@interpretIdentifier
                notationLookup[identifier]?.interpretNotation(identifier) ?: interpretNoNotation()
            }

        /**
         * Either fetch from the cache, or interpret, cache, and return, the [RemoteTypeInformation] corresponding to this
         * [TypeNotation].
         */
        private fun TypeNotation.interpretNotation(identifier: TypeIdentifier): RemoteTypeInformation =
                cache.getOrPut(typeDescriptor) {
                    when (this) {
                        is CompositeType -> interpretComposite(identifier)
                        is RestrictedType -> interpretRestricted(identifier)
                    }
                }

        /**
         * Interpret the properties, interfaces and type parameters in this [TypeNotation], and return suitable
         * [RemoteTypeInformation].
         */
        private fun CompositeType.interpretComposite(identifier: TypeIdentifier): RemoteTypeInformation {
            val properties = fields.asSequence().sortedBy { it.name }.map { it.interpret() }.toMap(LinkedHashMap())
            val typeParameters = identifier.interpretTypeParameters()
            val interfaceIdentifiers = provides.map { name -> name.typeIdentifier }
            val isInterface = identifier in interfaceIdentifiers
            val interfaces = interfaceIdentifiers.mapNotNull { interfaceIdentifier ->
                if (interfaceIdentifier == identifier) null
                else interfaceIdentifier.interpretIdentifier()
            }

            return if (isInterface) RemoteTypeInformation.AnInterface(typeDescriptor, identifier, properties, interfaces, typeParameters)
            else RemoteTypeInformation.Composable(typeDescriptor, identifier, properties, interfaces, typeParameters)
        }

        /**
         * Type parameters are read off from the [TypeIdentifier] we translated the AMQP type name into.
         */
        private fun TypeIdentifier.interpretTypeParameters(): List<RemoteTypeInformation> = when (this) {
            is TypeIdentifier.Parameterised -> parameters.map { it.interpretIdentifier() }
            else -> emptyList()
        }

        /**
         * Interpret a [RestrictedType] into suitable [RemoteTypeInformation].
         */
        private fun RestrictedType.interpretRestricted(identifier: TypeIdentifier): RemoteTypeInformation = when (identifier) {
            is TypeIdentifier.Parameterised ->
                RemoteTypeInformation.Parameterised(
                        typeDescriptor,
                        identifier,
                        identifier.interpretTypeParameters())
            is TypeIdentifier.ArrayOf ->
                RemoteTypeInformation.AnArray(
                        typeDescriptor,
                        identifier,
                        identifier.componentType.interpretIdentifier())
            is TypeIdentifier.Unparameterised ->
                if (choices.isEmpty()) {
                    RemoteTypeInformation.Unparameterised(
                            typeDescriptor,
                            identifier)
                } else interpretEnum(identifier)

            else -> throw NotSerializableException("Cannot interpret restricted type $this")
        }

        private fun RestrictedType.interpretEnum(identifier: TypeIdentifier): RemoteTypeInformation.AnEnum {
            val constants = choices.asSequence().mapIndexed { index, choice -> choice.name to index }.toMap(LinkedHashMap())
            val transforms = try {
                enumTransformsLookup[identifier]?.let { EnumTransforms.build(it, constants) } ?: EnumTransforms.empty
            } catch (e: InvalidEnumTransformsException) {
                throw NotSerializableDetailedException(name, e.message!!)
            }
            return RemoteTypeInformation.AnEnum(
                    typeDescriptor,
                    identifier,
                    constants.keys.toList(),
                    transforms)
        }

        /**
         * Interpret a [Field] into a name/[RemotePropertyInformation] pair.
         */
        private fun Field.interpret(): Pair<String, RemotePropertyInformation> {
            val identifier = type.typeIdentifier

            // A type of "*" is replaced with the value of the "requires" field
            val fieldTypeIdentifier = if (identifier == TypeIdentifier.TopType && !requires.isEmpty()) {
                requires[0].typeIdentifier
            } else identifier

            // We convert Java Object types to Java primitive types if the field is mandatory.
            val fieldType = fieldTypeIdentifier.forcePrimitive(mandatory).interpretIdentifier()

            return name to RemotePropertyInformation(
                    fieldType,
                    mandatory)
        }

        /**
         * If there is no [TypeNotation] in the [Schema] matching a given [TypeIdentifier], we interpret the [TypeIdentifier]
         * directly.
         */
        private fun TypeIdentifier.interpretNoNotation(): RemoteTypeInformation =
                when (this) {
                    is TypeIdentifier.TopType -> RemoteTypeInformation.Top
                    is TypeIdentifier.UnknownType -> RemoteTypeInformation.Unknown
                    is TypeIdentifier.ArrayOf ->
                        RemoteTypeInformation.AnArray(
                                name,
                                this,
                                componentType.interpretIdentifier())
                    is TypeIdentifier.Parameterised ->
                        RemoteTypeInformation.Parameterised(
                                name,
                                this,
                                parameters.map { it.interpretIdentifier() })
                    else -> RemoteTypeInformation.Unparameterised(name, this)
                }
    }
}

private val TypeNotation.typeDescriptor: String get() = descriptor.name?.toString() ?:
throw NotSerializableException("Type notation has no type descriptor: $this")

private val String.typeIdentifier get(): TypeIdentifier = AMQPTypeIdentifierParser.parse(this)

/**
 * Force e.g. [java.lang.Integer] to `int`, if it is the type of a mandatory field.
 */
private fun TypeIdentifier.forcePrimitive(mandatory: Boolean) =
        if (mandatory) primitives[this] ?: this
        else this

private val primitives = sequenceOf(
        Boolean::class,
        Byte::class,
        Char::class,
        Int::class,
        Short::class,
        Long::class,
        Float::class,
        Double::class).associate {
    TypeIdentifier.forClass(it.javaObjectType) to TypeIdentifier.forClass(it.javaPrimitiveType!!)
}