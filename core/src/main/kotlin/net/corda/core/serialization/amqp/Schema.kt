package net.corda.core.serialization.amqp

import net.corda.core.serialization.OpaqueBytes
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.amqp.UnsignedLong
import org.apache.qpid.proton.codec.Data
import org.apache.qpid.proton.codec.DescribedTypeConstructor
import java.io.NotSerializableException

val DESCRIPTOR_MSW: Long = 0xc0da0000

// "corda" + majorVersionByte + minorVersionMSB + minorVersionLSB
val AmqpHeaderV1_0: OpaqueBytes = OpaqueBytes("corda\u0001\u0000\u0000".toByteArray())

// TODO: make the schema parsing lazy since mostly schemas will have been seen before and we only need it if we don't recognise a type descriptor.
data class Envelope(val obj: Any?, val schema: Schema) : DescribedType {
    companion object {
        val DESCRIPTOR = UnsignedLong(0x0001L or DESCRIPTOR_MSW)

        fun get(data: Data): Envelope {
            val describedType = data.`object` as DescribedType
            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
            val list = describedType.described as List<*>
            return Constructor.newInstance(listOf(list[0], Schema.get(list[1]!!)))
        }
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(obj, schema)

    object Constructor : DescribedTypeConstructor<Envelope> {
        override fun getTypeClass(): Class<*> = Envelope::class.java

        override fun newInstance(described: Any?): Envelope {
            val list = described as? List<Any> ?: throw IllegalStateException("Was expecting a list")
            return Envelope(list[0], list[1] as Schema)
        }
    }
}

data class Schema(val types: List<TypeNotation>) : DescribedType {
    companion object {
        val DESCRIPTOR = UnsignedLong(0x0002L or DESCRIPTOR_MSW)

        fun get(obj: Any): Schema {
            val describedType = obj as DescribedType
            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
            val list = describedType.described as List<*>
            return Constructor.newInstance(listOf((list[0] as List<*>).map { TypeNotation.get(it!!) }))
        }
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(types)

    object Constructor : DescribedTypeConstructor<Schema> {
        override fun getTypeClass(): Class<*> = Schema::class.java

        override fun newInstance(described: Any?): Schema {
            val list = described as? List<Any> ?: throw IllegalStateException("Was expecting a list")
            return Schema(list[0] as List<TypeNotation>)
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (type in types) {
            sb.append(type.toString())
            sb.append("\n")
        }
        return sb.toString()
    }
}

data class Descriptor(val name: String?, val code: UnsignedLong?) : DescribedType {
    companion object {
        val DESCRIPTOR = UnsignedLong(0x0003L or DESCRIPTOR_MSW)

        fun get(obj: Any): Descriptor {
            val describedType = obj as DescribedType
            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
            return Constructor.newInstance(describedType.described)
        }
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(name, code)

    object Constructor : DescribedTypeConstructor<Descriptor> {
        override fun getTypeClass(): Class<*> = Descriptor::class.java

        override fun newInstance(described: Any?): Descriptor {
            val list = described as? List<Any> ?: throw IllegalStateException("Was expecting a list")
            return Descriptor(list[0] as? String, list[1] as? UnsignedLong)
        }
    }

    override fun toString(): String {
        val sb = StringBuilder("<descriptor")
        if (name != null) {
            sb.append(" name=\"$name\"")
        }
        if (code != null) {
            val code = String.format("0x%08x:0x%08x", code.toLong().shr(32), code.toLong().and(0xffff))
            sb.append(" code=\"$code\"")
        }
        sb.append("/>")
        return sb.toString()
    }
}

data class Field(val name: String, val type: String, val requires: Array<String>?, val default: String?, val label: String?, val mandatory: Boolean, val multiple: Boolean) : DescribedType {
    companion object {
        val DESCRIPTOR = UnsignedLong(0x0004L or DESCRIPTOR_MSW)

        fun get(obj: Any): Field {
            val describedType = obj as DescribedType
            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
            return Constructor.newInstance(describedType.described)
        }
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(name, type, requires, default, label, mandatory, multiple)

    object Constructor : DescribedTypeConstructor<Field> {
        override fun getTypeClass(): Class<*> = Field::class.java

        override fun newInstance(described: Any?): Field {
            val list = described as? List<Any> ?: throw IllegalStateException("Was expecting a list")
            return Field(list[0] as String, list[1] as String, list[2] as? Array<String>, list[3] as? String, list[4] as? String, list[5] as Boolean, list[6] as Boolean)
        }
    }

    override fun toString(): String {
        val sb = StringBuilder("<field name=\"$name\" type=\"$type\"")
        if (requires != null && requires.size > 0) {
            sb.append(" requires=\"")
            sb.append(requires.joinToString(","))
            sb.append("\"")
        }
        if (!default.isNullOrBlank()) {
            sb.append(" default=\"$default\"")
        }
        if (!label.isNullOrBlank()) {
            sb.append(" label=\"$label\"")
        }
        sb.append(" mandatory=\"$mandatory\" multiple=\"$multiple\"/>")
        return sb.toString()
    }
}

interface TypeNotation : DescribedType {
    companion object {
        fun get(obj: Any): TypeNotation {
            val describedType = obj as DescribedType
            if (describedType.descriptor == CompositeType.DESCRIPTOR) {
                return CompositeType.get(describedType)
            } else if (describedType.descriptor == RestrictedType.DESCRIPTOR) {
                return RestrictedType.get(describedType)
            } else {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
        }
    }

    val name: String
    val label: String?
    val provides: Array<String>?
    val descriptor: Descriptor
}

data class CompositeType(override val name: String, override val label: String?, override val provides: Array<String>?, override val descriptor: Descriptor, val fields: List<Field>) : TypeNotation {
    companion object {
        val DESCRIPTOR = UnsignedLong(0x0005L or DESCRIPTOR_MSW)

        fun get(describedType: DescribedType): CompositeType {
            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
            val list = describedType.described as List<*>
            return Constructor.newInstance(listOf(list[0], list[1], list[2], Descriptor.get(list[3]!!), (list[4] as List<*>).map { Field.get(it!!) }))
        }
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(name, label, provides, descriptor, fields)

    object Constructor : DescribedTypeConstructor<CompositeType> {
        override fun getTypeClass(): Class<*> = CompositeType::class.java

        override fun newInstance(described: Any?): CompositeType {
            val list = described as? List<Any> ?: throw IllegalStateException("Was expecting a list")
            return CompositeType(list[0] as String, list[1] as? String, list[2] as? Array<String>, list[3] as Descriptor, list[4] as List<Field>)
        }
    }

    override fun toString(): String {
        val sb = StringBuilder("<type class=\"composite\" name=\"$name\"")
        if (!label.isNullOrBlank()) {
            sb.append(" label=\"$label\"")
        }
        if (provides != null && provides.size > 0) {
            sb.append(" provides=\"")
            sb.append(provides.joinToString(","))
            sb.append("\"")
        }
        sb.append(">\n")
        sb.append("  $descriptor\n")
        if (fields != null) {
            for (field in fields) {
                sb.append("  $field\n")
            }
        }
        sb.append("</type>")
        return sb.toString()
    }
}

data class RestrictedType(override val name: String, override val label: String?, override val provides: Array<String>?, val source: String, override val descriptor: Descriptor, val choices: Array<Choice>?) : TypeNotation {
    companion object {
        val DESCRIPTOR = UnsignedLong(0x0006L or DESCRIPTOR_MSW)

        fun get(describedType: DescribedType): RestrictedType {
            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
            val list = describedType.described as List<*>
            return Constructor.newInstance(listOf(list[0], list[1], list[2], list[3], Descriptor.get(list[4]!!), (list[5] as List<*>).map { Choice.get(it!!) }))
        }
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(name, label, provides, source, descriptor, choices)

    object Constructor : DescribedTypeConstructor<RestrictedType> {
        override fun getTypeClass(): Class<*> = RestrictedType::class.java

        override fun newInstance(described: Any?): RestrictedType {
            val list = described as? List<Any> ?: throw IllegalStateException("Was expecting a list")
            return RestrictedType(list[0] as String, list[1] as? String, list[2] as? Array<String>, list[3] as String, list[4] as Descriptor, list[5] as? Array<Choice>)
        }
    }

    override fun toString(): String {
        val sb = StringBuilder("<type class=\"restricted\" name=\"$name\"")
        if (!label.isNullOrBlank()) {
            sb.append(" label=\"$label\"")
        }
        sb.append(" source=\"$source\"")
        if (provides != null && provides.size > 0) {
            sb.append(" provides=\"")
            sb.append(provides.joinToString(","))
            sb.append("\"")
        }
        sb.append(">\n")
        sb.append("  $descriptor\n")
        sb.append("</type>")
        return sb.toString()
    }
}

data class Choice(val name: String, val value: String) : DescribedType {
    companion object {
        val DESCRIPTOR = UnsignedLong(0x0007L or DESCRIPTOR_MSW)

        fun get(obj: Any): Choice {
            val describedType = obj as DescribedType
            if (describedType.descriptor != DESCRIPTOR) {
                throw NotSerializableException("Unexpected descriptor ${describedType.descriptor}.")
            }
            return Constructor.newInstance(describedType.described)
        }
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(name, value)

    object Constructor : DescribedTypeConstructor<Choice> {
        override fun getTypeClass(): Class<*> = Choice::class.java

        override fun newInstance(described: Any?): Choice {
            val list = described as? List<Any> ?: throw IllegalStateException("Was expecting a list")
            return Choice(list[0] as String, list[1] as String)
        }
    }

    override fun toString(): String {
        return "<choice name=\"$name\" value=\"$value\"/>"
    }
}
