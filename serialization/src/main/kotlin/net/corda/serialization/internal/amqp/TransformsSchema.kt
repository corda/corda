package net.corda.serialization.internal.amqp

import net.corda.core.Deterministic
import net.corda.core.serialization.CordaSerializationTransformEnumDefault
import net.corda.core.serialization.CordaSerializationTransformRename
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.codec.DescribedTypeConstructor
import java.io.NotSerializableException
import java.util.*

// NOTE: We are effectively going to replicate the annotations, we need to do this because
// we can't instantiate instances of those annotation classes and this code needs to
// work at the de-serialising end
/**
 * Base class for representations of specific types of transforms as applied to a type within the
 * Corda serialisation framework
 */
abstract class Transform : DescribedType {
    companion object : DescribedTypeConstructor<Transform> {
        val DESCRIPTOR = AMQPDescriptorRegistry.TRANSFORM_ELEMENT.amqpDescriptor

        /**
         * @param obj: a serialized instance of a described type, should be one of the
         * descendants of this class
         */
        private fun checkDescribed(obj: Any?): Any? {
            val describedType = obj as DescribedType

            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }

            return describedType.described
        }

        /**
         * From an encoded descendant return an instance of the specific type. Transforms are encoded into
         * the schema as a list of class name and parameters.Using the class name (list element 0)
         * create the appropriate class instance
         *
         * For future proofing any unknown transform types are not treated as errors, rather we
         * simply create a placeholder object so we can ignore it
         *
         * @param obj: a serialized instance of a described type, should be one of the
         * descendants of this class
         */
        override fun newInstance(obj: Any?): Transform {
            val described = Transform.checkDescribed(obj) as List<*>
            return when (described[0]) {
                EnumDefaultSchemaTransform.typeName -> EnumDefaultSchemaTransform.newInstance(described)
                RenameSchemaTransform.typeName -> RenameSchemaTransform.newInstance(described)
                else -> UnknownTransform()
            }
        }

        override fun getTypeClass(): Class<*> = Transform::class.java
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    /**
     * Return a string representation of a transform in terms of key / value pairs, used
     * by the serializer to encode arbitrary transforms
     */
    abstract fun params(): String

    abstract val name: String
}

/**
 * Transform type placeholder that allows for backward compatibility. Should a noce recieve
 * a transform type it doesn't recognise, we can will use this as a placeholder
 */
class UnknownTransform : Transform() {
    companion object : DescribedTypeConstructor<UnknownTransform> {
        const val typeName = "UnknownTransform"

        override fun newInstance(obj: Any?) = UnknownTransform()

        override fun getTypeClass(): Class<*> = UnknownTransform::class.java
    }

    override fun getDescribed(): Any = emptyList<Any>()
    override fun params() = ""

    override val name: String get() = typeName
}

/**
 * Used by the unit testing framework
 */
class UnknownTestTransform(val a: Int, val b: Int, val c: Int) : Transform() {
    companion object : DescribedTypeConstructor<UnknownTestTransform> {
        const val typeName = "UnknownTest"

        override fun newInstance(obj: Any?): UnknownTestTransform {
            val described = obj as List<*>
            return UnknownTestTransform(described[1] as Int, described[2] as Int, described[3] as Int)
        }

        override fun getTypeClass(): Class<*> = UnknownTransform::class.java
    }

    override fun getDescribed(): Any = listOf(name, a, b, c)
    override fun params() = ""

    override val name: String get() = typeName
}

/**
 * Transform to be used on an Enumerated Type whenever a new element is added
 *
 * @property old The value the [new] instance should default to when not available
 * @property new the value (as a String) that has been added
 */
class EnumDefaultSchemaTransform(val old: String, val new: String) : Transform() {
    companion object : DescribedTypeConstructor<EnumDefaultSchemaTransform> {
        /**
         * Value encoded into the schema that identifies a transform as this type
         */
        const val typeName = "EnumDefault"

        override fun newInstance(obj: Any?): EnumDefaultSchemaTransform {
            val described = obj as List<*>
            val old = described[1] as? String ?: throw IllegalStateException("Was expecting \"old\" as a String")
            val new = described[2] as? String ?: throw IllegalStateException("Was expecting \"new\" as a String")
            return EnumDefaultSchemaTransform(old, new)
        }

        override fun getTypeClass(): Class<*> = EnumDefaultSchemaTransform::class.java
    }

    @Suppress("UNUSED")
    constructor (annotation: CordaSerializationTransformEnumDefault) : this(annotation.old, annotation.new)

    override fun getDescribed(): Any = listOf(name, old, new)
    override fun params() = "old=${old.esc()} new=${new.esc()}"

    override fun equals(other: Any?) = (
            (other is EnumDefaultSchemaTransform && other.new == new && other.old == old) || super.equals(other))

    override fun hashCode() = (17 * new.hashCode()) + old.hashCode()

    override val name: String get() = typeName
}

/**
 * Transform applied to either a class or enum where a property is renamed
 *
 * @property from the name of the property or constant prior to being changed, i.e. what it was
 * @property to the new name of the property or constant after the change has been made, i.e. what it is now
 */
class RenameSchemaTransform(val from: String, val to: String) : Transform() {
    companion object : DescribedTypeConstructor<RenameSchemaTransform> {
        /**
         * Value encoded into the schema that identifies a transform as this type
         */
        const val typeName = "Rename"

        override fun newInstance(obj: Any?): RenameSchemaTransform {
            val described = obj as List<*>
            val from = described[1] as? String ?: throw IllegalStateException("Was expecting \"from\" as a String")
            val to = described[2] as? String ?: throw IllegalStateException("Was expecting \"to\" as a String")
            return RenameSchemaTransform(from, to)
        }

        override fun getTypeClass(): Class<*> = RenameSchemaTransform::class.java
    }

    @Suppress("UNUSED")
    constructor (annotation: CordaSerializationTransformRename) : this(annotation.from, annotation.to)

    override fun getDescribed(): Any = listOf(name, from, to)

    override fun params() = "from=${from.esc()} to=${to.esc()}"

    override fun equals(other: Any?) = (
            (other is RenameSchemaTransform && other.from == from && other.to == to) || super.equals(other))

    override fun hashCode() = (11 * from.hashCode()) + to.hashCode()

    override val name: String get() = typeName
}

/**
 * Represents the set of all transforms that can be a applied to all classes represented as part of
 * an AMQP schema. It forms a part of the AMQP envelope alongside the [Schema] and the serialized bytes
 *
 * @property types maps class names to a map of transformation types. In turn those transformation types
 * are each a list of instances o that transform.
 */
data class TransformsSchema(val types: Map<String, EnumMap<TransformTypes, MutableList<Transform>>>) : DescribedType {
    companion object : DescribedTypeConstructor<TransformsSchema> {
        val DESCRIPTOR = AMQPDescriptorRegistry.TRANSFORM_SCHEMA.amqpDescriptor

        /**
         * Takes a class name and either returns a cached instance of the TransformSet for it or, on a cache miss,
         * instantiates the transform set before inserting into the cache and returning it.
         *
         * @param name fully qualified class name to lookup transforms for
         * @param sf the [SerializerFactory] building this transform set. Needed as each can define it's own
         * class loader and this dictates which classes we can and cannot see
         */
        fun get(name: String, sf: SerializerFactory) = sf.transformsCache.computeIfAbsent(name) {
            val transforms = EnumMap<TransformTypes, MutableList<Transform>>(TransformTypes::class.java)
            try {
                val clazz = sf.classloader.loadClass(name)

                supportedTransforms.forEach { transform ->
                    clazz.getAnnotation(transform.type)?.let { list ->
                        transform.getAnnotations(list).forEach { annotation ->
                            val t = transform.enum.build(annotation)

                            // we're explicitly rejecting repeated annotations, whilst it's fine and we'd just
                            // ignore them it feels like a good thing to alert the user to since this is
                            // more than likely a typo in their code so best make it an actual error
                            if (transforms.computeIfAbsent(transform.enum) { mutableListOf() }.any { t == it }) {
                                throw NotSerializableException(
                                        "Repeated unique transformation annotation of type ${t.name}")
                            }

                            transforms[transform.enum]!!.add(t)
                        }

                        transform.enum.validate(
                                transforms[transform.enum] ?: emptyList(),
                                clazz.enumConstants.mapIndexed { i, s -> Pair(s.toString(), i) }.toMap())
                    }
                }
            } catch (_: ClassNotFoundException) {
                // if we can't load the class we'll end up caching an empty list which is fine as that
                // list, on lookup, won't be included in the schema because it's empty
            }

            transforms
        }

        private fun getAndAdd(
                type: String,
                sf: SerializerFactory,
                map: MutableMap<String, EnumMap<TransformTypes, MutableList<Transform>>>) {
            get(type, sf).apply {
                if (isNotEmpty()) {
                    map[type] = this
                }
            }
        }

        /**
         * Prepare a schema for encoding, takes all of the types being transmitted and inspects each
         * one for any transform annotations. If there are any build up a set that can be
         * encoded into the AMQP [Envelope]
         *
         * @param schema should be a [Schema] generated for a serialised data structure
         * @param sf should be provided by the same serialization context that generated the schema
         */
        fun build(schema: Schema, sf: SerializerFactory) = TransformsSchema(
                mutableMapOf<String, EnumMap<TransformTypes, MutableList<Transform>>>().apply {
                    schema.types.forEach { type -> getAndAdd(type.name, sf, this) }
                })

        override fun getTypeClass(): Class<*> = TransformsSchema::class.java

        /**
         * Constructs an instance of the object from the serialised form of an instance
         * of this object
         */
        override fun newInstance(described: Any?): TransformsSchema {
            val rtn = mutableMapOf<String, EnumMap<TransformTypes, MutableList<Transform>>>()
            val describedType = described as? DescribedType ?: return TransformsSchema(rtn)

            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }

            val map = describedType.described as? Map<*, *>
                    ?: throw NotSerializableException("Transform schema must be encoded as a map")

            map.forEach { type ->
                val fingerprint = type.key as? String
                        ?: throw NotSerializableException("Fingerprint must be encoded as a string")

                rtn[fingerprint] = EnumMap<TransformTypes, MutableList<Transform>>(TransformTypes::class.java)

                (type.value as Map<*, *>).forEach { transformType, transforms ->
                    val transform = TransformTypes.newInstance(transformType)

                    rtn[fingerprint]!![transform] = mutableListOf()
                    (transforms as List<*>).forEach {
                        rtn[fingerprint]!![TransformTypes.newInstance(transformType)]?.add(Transform.newInstance(it))
                                ?: throw NotSerializableException("De-serialization error with transform for class "
                                        + "${type.key} ${transform.name}")
                    }
                }
            }

            return TransformsSchema(rtn)
        }
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = types

    @Suppress("NAME_SHADOWING")
    override fun toString(): String {
        @Deterministic
        data class Indent(val indent: String) {
            @Suppress("UNUSED") constructor(i: Indent) : this("  ${i.indent}")

            override fun toString() = indent
        }

        val sb = StringBuilder("")
        val indent = Indent("")

        sb.appendln("$indent<type-transforms>")
        types.forEach { type ->
            val indent = Indent(indent)
            sb.appendln("$indent<type name=${type.key.esc()}>")
            type.value.forEach { transform ->
                val indent = Indent(indent)
                sb.appendln("$indent<transforms type=${transform.key.name.esc()}>")
                transform.value.forEach {
                    val indent = Indent(indent)
                    sb.appendln("$indent<transform ${it.params()} />")
                }
                sb.appendln("$indent</transforms>")
            }
            sb.appendln("$indent</type>")
        }
        sb.appendln("$indent</type-transforms>")

        return sb.toString()
    }
}

private fun String.esc() = "\"$this\""
