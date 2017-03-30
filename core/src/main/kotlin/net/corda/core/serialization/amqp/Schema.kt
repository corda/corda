package net.corda.core.serialization.amqp

import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.amqp.UnsignedLong
import org.apache.qpid.proton.codec.DescribedTypeConstructor

val DESCRIPTOR_MSW: Long = 0xc0da0000

data class Envelope(val obj: Any?, val schema: Schema) : DescribedType {
    companion object {
        val DESCRIPTOR = UnsignedLong(0x0001L or DESCRIPTOR_MSW)
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(obj, schema)

    class Constructor : DescribedTypeConstructor<Envelope> {
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
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(types)

    class Constructor : DescribedTypeConstructor<Schema> {
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
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(name, code)

    class Constructor : DescribedTypeConstructor<Descriptor> {
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
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(name, type, requires, default, label, mandatory, multiple)

    class Constructor : DescribedTypeConstructor<Field> {
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
    val name: String
    val label: String?
    val provides: Array<String>?
    val descriptor: Descriptor

    //fun encode(): Any?
}

data class CompositeType(override val name: String, override val label: String?, override val provides: Array<String>?, override val descriptor: Descriptor, val fields: Array<Field>?) : TypeNotation {
    companion object {
        val DESCRIPTOR = UnsignedLong(0x0005L or DESCRIPTOR_MSW)
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(name, label, provides, descriptor, fields)

    class Constructor : DescribedTypeConstructor<CompositeType> {
        override fun getTypeClass(): Class<*> = CompositeType::class.java

        override fun newInstance(described: Any?): CompositeType {
            val list = described as? List<Any> ?: throw IllegalStateException("Was expecting a list")
            return CompositeType(list[0] as String, list[1] as? String, list[2] as? Array<String>, list[3] as Descriptor, list[4] as? Array<Field>)
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
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(name, label, provides, source, descriptor, choices)

    class Constructor : DescribedTypeConstructor<RestrictedType> {
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

data class Choice(val name: Symbol, val value: String) : DescribedType {
    companion object {
        val DESCRIPTOR = UnsignedLong(0x0007L or DESCRIPTOR_MSW)
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(name, value)

    class Constructor : DescribedTypeConstructor<Choice> {
        override fun getTypeClass(): Class<*> = Choice::class.java

        override fun newInstance(described: Any?): Choice {
            val list = described as? List<Any> ?: throw IllegalStateException("Was expecting a list")
            return Choice(list[0] as Symbol, list[1] as String)
        }
    }

    override fun toString(): String {
        return "<choice name=\"$name\" value=\"$value\"/>"
    }
}

data class ObjectRef(val ref: Int) : DescribedType {
    companion object {
        val DESCRIPTOR = UnsignedLong(0x0008L or DESCRIPTOR_MSW)
    }

    override fun getDescriptor(): Any = DESCRIPTOR

    override fun getDescribed(): Any = listOf(ref)

    class Constructor : DescribedTypeConstructor<ObjectRef> {
        override fun getTypeClass(): Class<*> = ObjectRef::class.java

        override fun newInstance(described: Any?): ObjectRef {
            val list = described as? List<Any> ?: throw IllegalStateException("Was expecting a list")
            return ObjectRef(list[0] as Int)
        }
    }

    override fun toString(): String {
        return "<ref value=\"$ref\"/>"
    }
}
