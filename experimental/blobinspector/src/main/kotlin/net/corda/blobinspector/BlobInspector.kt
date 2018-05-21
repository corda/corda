package net.corda.blobinspector

import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.EncodingWhitelist
import net.corda.core.serialization.SerializationEncoding
import net.corda.core.utilities.ByteSequence
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.*
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.amqp.Symbol

/**
 * Print a string to the console only if the verbose config option is set.
 */
fun String.debug(config: Config) {
    if (config.verbose) {
        println(this)
    }
}

/**
 *
 */
interface Stringify {
    fun stringify(sb: IndentingStringBuilder)
}

/**
 * Makes classnames easier to read by stripping off the package names from the class and separating nested
 * classes
 *
 * For example:
 *
 * net.corda.blobinspector.Class1<net.corda.blobinspector.Class2>
 * Class1 <Class2>
 *
 * net.corda.blobinspector.Class1<net.corda.blobinspector.Class2, net.corda.blobinspector.Class3>
 * Class1 <Class2, Class3>
 *
 * net.corda.blobinspector.Class1<net.corda.blobinspector.Class2<net.corda.blobinspector.Class3>>
 * Class1 <Class2 <Class3>>
 *
 * net.corda.blobinspector.Class1<net.corda.blobinspector.Class2<net.corda.blobinspector.Class3>>
 * Class1 :: C <Class2 <Class3>>
 */
fun String.simplifyClass(): String {

    return if (this.endsWith('>')) {
        val templateStart = this.indexOf('<')
        val clazz = (this.substring(0, templateStart))
        val params = this.substring(templateStart+1, this.length-1).split(',').joinToString { it.simplifyClass() }

        "${clazz.simplifyClass()} <$params>"
    }
    else {
        substring(this.lastIndexOf('.') + 1).replace("$", " :: ")
    }
}

/**
 * Represents the deserialized form of the property of an Object
 *
 * @param name
 * @param type
 */
abstract class Property(
        val name: String,
        val type: String) : Stringify

/**
 * Derived class of [Property], represents properties of an object that are non compelex, such
 * as any POD type or String
 */
class PrimProperty(
        name: String,
        type: String,
        private val value: String) : Property(name, type) {
    override fun toString(): String = "$name : $type : $value"

    override fun stringify(sb: IndentingStringBuilder) {
        sb.appendln("$name : $type : $value")
    }
}

/**
 * Derived class of [Property] that represents a binary blob. Specifically useful because printing
 * a stream of bytes onto the screen isn't very use friendly
 */
class BinaryProperty(
        name: String,
        type: String,
        val value: ByteArray) : Property(name, type) {
    override fun toString(): String = "$name : $type : <<<BINARY BLOB>>>"

    override fun stringify(sb: IndentingStringBuilder) {
        sb.appendln("$name : $type : <<<BINARY BLOB>>>")
    }
}

/**
 * Derived class of [Property] that represent a list property. List could be either PoD types or
 * composite types.
 */
class ListProperty(
        name: String,
        type: String,
        private val values: MutableList<Any> = mutableListOf()) : Property(name, type) {
    override fun stringify(sb: IndentingStringBuilder) {
        sb.apply {
            when {
                values.isEmpty() -> appendln("$name : $type : [ << EMPTY LIST >> ]")
                values.first() is Stringify -> {
                    appendln("$name : $type : [")
                    values.forEach {
                        (it as Stringify).stringify(this)
                    }
                    appendln("]")
                }
                else -> {
                    appendln("$name : $type : [")
                    values.forEach {
                        appendln(it.toString())
                    }
                    appendln("]")
                }
            }
        }
    }
}

class MapProperty(
        name: String,
        type: String,
        private val map: MutableMap<*, *>
) : Property(name, type) {
    override fun stringify(sb: IndentingStringBuilder) {
        if (map.isEmpty()) {
            sb.appendln("$name : $type : { << EMPTY MAP >> }")
            return
        }

        // TODO this will not produce pretty output
        sb.apply {
            appendln("$name : $type : {")
            map.forEach {
                try {
                    (it.key as Stringify).stringify(this)
                } catch (e: ClassCastException) {
                    append (it.key.toString() + " : ")
                }
                try {
                    (it.value as Stringify).stringify(this)
                } catch (e: ClassCastException) {
                    appendln("\"${it.value.toString()}\"")
                }
            }
            appendln("}")
        }
    }
}

/**
 * Derived class of [Property] that represents class properties that are themselves instances of
 * some complex type.
 */
class InstanceProperty(
        name: String,
        type: String,
        val value: Instance) : Property(name, type) {
    override fun stringify(sb: IndentingStringBuilder) {
        sb.append("$name : ")
        value.stringify(sb)
    }
}

/**
 * Represents an instance of a composite type.
 */
class Instance(
        val name: String,
        val type: String,
        val fields: MutableList<Property> = mutableListOf()) : Stringify {
    override fun stringify(sb: IndentingStringBuilder) {
        sb.apply {
            appendln("${name.simplifyClass()} : {")
            fields.forEach {
                it.stringify(this)
            }
            appendln("}")
        }
    }
}

/**
 *
 */
fun inspectComposite(
        config: Config,
        typeMap: Map<Symbol?, TypeNotation>,
        obj: DescribedType): Instance {
    if (obj.described !is List<*>) throw MalformedBlob("")

    val name = (typeMap[obj.descriptor] as CompositeType).name
    "composite: $name".debug(config)

    val inst = Instance(
            typeMap[obj.descriptor]?.name ?: "",
            typeMap[obj.descriptor]?.label ?: "")

    (typeMap[obj.descriptor] as CompositeType).fields.zip(obj.described as List<*>).forEach {
        "  field: ${it.first.name}".debug(config)
        inst.fields.add(
                if (it.second is DescribedType) {
                    "    - is described".debug(config)
                    val d = inspectDescribed(config, typeMap, it.second as DescribedType)

                    when (d) {
                        is Instance ->
                            InstanceProperty(
                                    it.first.name,
                                    it.first.type,
                                    d)
                        is List<*> -> {
                            "      - List".debug(config)
                            ListProperty(
                                    it.first.name,
                                    it.first.type,
                                    d as MutableList<Any>)
                        }
                        is Map<*, *> -> {
                            MapProperty(
                                    it.first.name,
                                    it.first.type,
                                    d as MutableMap<*, *>)
                        }
                        else -> {
                            "    skip it".debug(config)
                            return@forEach
                        }
                    }

                } else {
                    "    - is prim".debug(config)
                    when (it.first.type) {
                        // Note, as in the case of SHA256 we can treat particular binary types
                        // as different properties with a little coercion
                        "binary" -> {
                            if (name == "net.corda.core.crypto.SecureHash\$SHA256") {
                                PrimProperty(
                                        it.first.name,
                                        it.first.type,
                                        SecureHash.SHA256((it.second as Binary).array).toString())
                            } else {
                                BinaryProperty(it.first.name, it.first.type, (it.second as Binary).array)
                            }
                        }
                        else -> PrimProperty(it.first.name, it.first.type, it.second.toString())
                    }
                })
    }

    return inst
}

fun inspectRestricted(
        config: Config,
        typeMap: Map<Symbol?, TypeNotation>,
        obj: DescribedType): Any {
    return when ((typeMap[obj.descriptor] as RestrictedType).source) {
        "list" -> inspectRestrictedList(config, typeMap, obj)
        "map" -> inspectRestrictedMap(config, typeMap, obj)
        else -> throw NotImplementedError()
    }
}


fun inspectRestrictedList(
        config: Config,
        typeMap: Map<Symbol?, TypeNotation>,
        obj: DescribedType
) : List<Any> {
    if (obj.described !is List<*>) throw MalformedBlob("")

    return mutableListOf<Any>().apply {
        (obj.described as List<*>).forEach {
            when (it) {
                is DescribedType -> add(inspectDescribed(config, typeMap, it))
                is RestrictedType -> add(inspectRestricted(config, typeMap, it))
                else -> add (it.toString())
            }
        }
    }
}

fun inspectRestrictedMap(
        config: Config,
        typeMap: Map<Symbol?, TypeNotation>,
        obj: DescribedType
) : Map<Any, Any> {
    if (obj.described !is Map<*,*>) throw MalformedBlob("")

    return mutableMapOf<Any, Any>().apply {
        (obj.described as Map<*, *>).forEach {
            val key = when (it.key) {
                is DescribedType -> inspectDescribed(config, typeMap, it.key as DescribedType)
                is RestrictedType -> inspectRestricted(config, typeMap, it.key as RestrictedType)
                else -> it.key.toString()
            }

            val value = when (it.value) {
                is DescribedType -> inspectDescribed(config, typeMap, it.value as DescribedType)
                is RestrictedType -> inspectRestricted(config, typeMap, it.value as RestrictedType)
                else -> it.value.toString()
            }

            this[key] = value
        }
    }
}


/**
 * Every element of the blob stream will be a ProtonJ [DescribedType]. When inspecting the blob stream
 * the two custom Corda types we're interested in are [CompositeType]'s, representing the instance of
 * some object (class), and [RestrictedType]'s, representing containers and enumerations.
 *
 * @param config The configuration object that controls the behaviour of the BlobInspector
 * @param typeMap
 * @param obj
 */
fun inspectDescribed(
        config: Config,
        typeMap: Map<Symbol?, TypeNotation>,
        obj: DescribedType): Any {
    "${obj.descriptor} in typeMap? = ${obj.descriptor in typeMap}".debug(config)

    return when (typeMap[obj.descriptor]) {
        is CompositeType -> {
            "* It's composite".debug(config)
            inspectComposite(config, typeMap, obj)
        }
        is RestrictedType -> {
            "* It's restricted".debug(config)
            inspectRestricted(config, typeMap, obj)
        }
        else -> {
            "${typeMap[obj.descriptor]?.name} is neither Composite or Restricted".debug(config)
        }
    }

}

internal object NullEncodingWhitelist : EncodingWhitelist {
    override fun acceptEncoding(encoding: SerializationEncoding) = false
}

// TODO : Refactor to generically poerate on arbitrary blobs, not a single workflow
fun inspectBlob(config: Config, blob: ByteArray) {
    val bytes = ByteSequence.of(blob)

    val headerSize = SerializationFactoryImpl.magicSize

    // TODO written to only understand one version, when we support multiple this will need to change
    val headers = listOf(ByteSequence.of(amqpMagic.bytes))

    val blobHeader = bytes.take(headerSize)

    if (blobHeader !in headers) {
        throw MalformedBlob("Blob is not a Corda AMQP serialised object graph")
    }


    val e = DeserializationInput.getEnvelope(bytes, NullEncodingWhitelist)

    if (config.schema) {
        println(e.schema)
    }

    if (config.transforms) {
        println(e.transformsSchema)
    }

    val typeMap = e.schema.types.associateBy({ it.descriptor.name }, { it })

    if (config.data) {
        val inspected = inspectDescribed(config, typeMap, e.obj as DescribedType)

        println("\n${IndentingStringBuilder().apply { (inspected as Instance).stringify(this) }}")

        (inspected as Instance).fields.find {
            it.type.startsWith("net.corda.core.serialization.SerializedBytes<")
        }?.let {
            "Found field of SerializedBytes".debug(config)
            (it as InstanceProperty).value.fields.find { it.name == "bytes" }?.let { raw ->
                inspectBlob(config, (raw as BinaryProperty).value)
            }
        }
    }
}

