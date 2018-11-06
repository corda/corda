package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.model.*
import java.io.NotSerializableException

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
    fun interpret(schema: Schema): Map<TypeDescriptor, RemoteTypeInformation> {
        val notationLookup = schema.types.associateBy { it.name.typeIdentifier }
        val byTypeDescriptor = schema.types.associateBy { it.typeDescriptor }

        return byTypeDescriptor.mapValues { (k, v) ->
            cache[k] ?: v.name.typeIdentifier.interpretIdentifier(notationLookup, emptySet())
                    .also { cache.putIfAbsent(k, it) }
        }
    }

    /**
     * Follow a [TypeIdentifier] to the [TypeNotation] associated with it in the lookup, and interpret that notation.
     * If there is no such notation, interpret the [TypeIdentifier] directly into [RemoteTypeInformation].
     *
     * If we have visited this [TypeIdentifier] before while traversing the graph of related [TypeNotation]s, then we
     * know we have hit a cycle and respond accordingly.
     */
    private fun TypeIdentifier.interpretIdentifier(notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): RemoteTypeInformation =
            if (this in seen) RemoteTypeInformation.Cycle(this) { interpretIdentifier(notationLookup, emptySet()) }
            else notationLookup[this]?.interpretNotation(this, notationLookup, seen + this) ?:
                interpretNoNotation(notationLookup, seen + this)

    /**
     * Either fetch from the cache, or interpret, cache, and return, the [RemoteTypeInformation] corresponding to this
     * [TypeNotation].
     */
    private fun TypeNotation.interpretNotation(identifier: TypeIdentifier, notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): RemoteTypeInformation =
            cache[typeDescriptor] ?: when (this) {
                is CompositeType -> interpretComposite(identifier, notationLookup, seen)
                is RestrictedType -> interpretRestricted(identifier, notationLookup, seen)
            }.also { cache.putIfAbsent(typeDescriptor, it) }

    /**
     * Interpret the properties, interfaces and type parameters in this [TypeNotation], and return suitable
     * [RemoteTypeInformation].
     */
    private fun CompositeType.interpretComposite(identifier: TypeIdentifier, notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): RemoteTypeInformation {
        val properties = fields.asSequence().map { it.interpret(notationLookup, seen) }.toMap()
        val typeParameters = identifier.interpretTypeParameters(notationLookup, seen)
        val interfaceIdentifiers = provides.map { name -> name.typeIdentifier }
        val isInterface = identifier in interfaceIdentifiers
        val interfaces = interfaceIdentifiers.mapNotNull { interfaceIdentifier ->
            if (interfaceIdentifier == identifier) null
            else interfaceIdentifier.interpretIdentifier(notationLookup, seen)
        }

        return if (isInterface) RemoteTypeInformation.AnInterface(typeDescriptor, identifier, properties, interfaces, typeParameters)
        else RemoteTypeInformation.Composable(typeDescriptor, identifier, properties, interfaces, typeParameters)
    }

    /**
     * Type parameters are read off from the [TypeIdentifier] we translated the AMQP type name into.
     */
    private fun TypeIdentifier.interpretTypeParameters(notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): List<RemoteTypeInformation> = when (this) {
            is TypeIdentifier.Parameterised -> parameters.map { it.interpretIdentifier(notationLookup, seen) }
            else -> emptyList()
        }

    /**
     * Interpret a [RestrictedType] into suitable [RemoteTypeInformation].
     */
    private fun RestrictedType.interpretRestricted(
            identifier: TypeIdentifier,
            notationLookup: Map<TypeIdentifier, TypeNotation>,
            seen: Set<TypeIdentifier>): RemoteTypeInformation = when (identifier) {
            is TypeIdentifier.Parameterised ->
                RemoteTypeInformation.Parameterised(
                        typeDescriptor,
                        identifier,
                        identifier.interpretTypeParameters(notationLookup, seen))
            is TypeIdentifier.ArrayOf ->
                RemoteTypeInformation.AnArray(
                        typeDescriptor,
                        identifier,
                        identifier.componentType.interpretIdentifier(notationLookup, seen))
            else -> RemoteTypeInformation.AnEnum(typeDescriptor, identifier, choices.map { it.name })
        }

    /**
     * Interpret a [Field] into a name/[RemotePropertyInformation] pair.
     */
    private fun Field.interpret(notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): Pair<String, RemotePropertyInformation> {
        val identifier = type.typeIdentifier

        // A type of "*" is replaced with the value of the "requires" field
        val fieldTypeIdentifier = if (identifier == TypeIdentifier.TopType && !requires.isEmpty()) {
            requires[0].typeIdentifier
        } else identifier

        // We convert Java Object types to Java primitive types if the field is mandatory.
        val fieldType = fieldTypeIdentifier.forcePrimitive(mandatory).interpretIdentifier(notationLookup, seen)

        return name to RemotePropertyInformation(
                fieldType,
                mandatory)
    }

    /**
     * If there is no [TypeNotation] in the [Schema] matching a given [TypeIdentifier], we interpret the [TypeIdentifier]
     * directly.
     */
    private fun TypeIdentifier.interpretNoNotation(notationLookup: Map<TypeIdentifier, TypeNotation>, seen: Set<TypeIdentifier>): RemoteTypeInformation =
            when (this) {
                is TypeIdentifier.TopType -> RemoteTypeInformation.Top
                is TypeIdentifier.UnknownType -> RemoteTypeInformation.Unknown
                is TypeIdentifier.ArrayOf ->
                    RemoteTypeInformation.AnArray(
                            name,
                            this,
                            componentType.interpretIdentifier(notationLookup, seen))
                is TypeIdentifier.Parameterised ->
                    RemoteTypeInformation.Parameterised(
                            name,
                            this,
                            parameters.map { it.interpretIdentifier(notationLookup, seen) })
                else -> RemoteTypeInformation.Unparameterised(name, this)
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

    companion object {
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
    }
}