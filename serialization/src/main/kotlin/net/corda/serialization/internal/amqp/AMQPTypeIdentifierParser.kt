package net.corda.serialization.internal.amqp

import com.google.common.primitives.Primitives
import net.corda.serialization.internal.model.TypeIdentifier
import org.apache.qpid.proton.amqp.*
import java.io.NotSerializableException
import java.lang.StringBuilder
import java.util.*

/**
 * Thrown if the type string parser enters an illegal state.
 */
class IllegalTypeNameParserStateException(message: String): NotSerializableException(message)

object AMQPTypeIdentifierParser {

    internal const val MAX_TYPE_PARAM_DEPTH = 32
    internal const val MAX_ARRAY_DEPTH = 32

    /**
     * Given a string representing a serialized AMQP type, construct a TypeIdentifier for that string.
     *
     * @param typeString The AMQP type string to parse
     * @return A [TypeIdentifier] representing the type represented by the input string.
     */
    fun parse(typeString: String): TypeIdentifier {
        validate(typeString)
        return typeString.fold<ParseState>(ParseState.ParsingRawType(null)) { state, c ->
                    state.accept(c)
                }.getTypeIdentifier()
    }

    // Make sure our inputs aren't designed to blow things up.
    private fun validate(typeString: String) {
        var maxTypeParamDepth = 0
        var typeParamdepth = 0

        var maxArrayDepth = 0
        var wasArray = false
        var arrayDepth = 0

        for (c in typeString) {
            if (c.isWhitespace() || c.isJavaIdentifierPart() || c.isJavaIdentifierStart() ||
                    c == '.' || c == ',' || c == '?' || c == '*') continue

            when(c) {
                '<' -> maxTypeParamDepth = Math.max(++typeParamdepth, typeParamdepth)
                '>' -> typeParamdepth--
                '[' -> {
                    arrayDepth = if (wasArray) arrayDepth + 2 else 1
                    maxArrayDepth = Math.max(maxArrayDepth,arrayDepth)
                }
                ']' -> arrayDepth--
                else -> throw IllegalTypeNameParserStateException("Type name '$typeString' contains illegal character '$c'")
            }
            wasArray = c == ']'
        }
        if (maxTypeParamDepth >= MAX_TYPE_PARAM_DEPTH)
            throw IllegalTypeNameParserStateException("Nested depth of type parameters exceeds maximum of $MAX_TYPE_PARAM_DEPTH")

        if (maxArrayDepth >= MAX_ARRAY_DEPTH)
            throw IllegalTypeNameParserStateException("Nested depth of arrays exceeds maximum of $MAX_ARRAY_DEPTH")
    }

    private sealed class ParseState {
        abstract val parent: ParseState.ParsingParameterList?
        abstract fun accept(c: Char): ParseState
        abstract fun getTypeIdentifier(): TypeIdentifier

        fun unexpected(c: Char): ParseState = throw IllegalTypeNameParserStateException("Unexpected character: '$c'")
        fun notInParameterList(c: Char): ParseState =
                throw IllegalTypeNameParserStateException("'$c' encountered, but not parsing type parameter list")

        /**
         * We are parsing a raw type name, either at the top level or as part of a list of type parameters.
         */
        data class ParsingRawType(override val parent: ParseState.ParsingParameterList?, val buffer: StringBuilder = StringBuilder()) : ParseState() {
            override fun accept(c: Char) = when (c) {
                ',' ->
                    if (parent == null) notInParameterList(c)
                    else ParsingRawType(parent.addParameter(getTypeIdentifier()))
                '[' -> ParsingArray(getTypeIdentifier(), parent)
                ']' -> unexpected(c)
                '<' -> ParsingRawType(ParsingParameterList(getTypeName(), parent))
                '>' -> parent?.addParameter(getTypeIdentifier())?.accept(c) ?: notInParameterList(c)
                else -> apply { buffer.append(c) }
            }

            private fun getTypeName(): String {
                val typeName = buffer.toString().trim()
                if (typeName.contains(' '))
                    throw IllegalTypeNameParserStateException("Illegal whitespace in type name $typeName")
                return typeName
            }

            override fun getTypeIdentifier(): TypeIdentifier {
                val typeName = getTypeName()
                return when (typeName) {
                    "*" -> TypeIdentifier.TopType
                    "?" -> TypeIdentifier.UnknownType
                    in simplified -> simplified[typeName]!!
                    else -> TypeIdentifier.Unparameterised(typeName)
                }
            }
        }

        /**
         * We are parsing a parameter list, and expect either to start a new parameter, add array-ness to the last
         * parameter we have, or end the list.
         */
        data class ParsingParameterList(val typeName: String, override val parent: ParsingParameterList?, val parameters: List<TypeIdentifier> = emptyList()) : ParseState() {
            override fun accept(c: Char) = when (c) {
                ' ' -> this
                ',' -> ParsingRawType(this)
                '[' ->
                    if (parameters.isEmpty()) unexpected(c)
                    else ParsingArray(
                            parameters[parameters.lastIndex],
                            copy(parameters = parameters.subList(0, parameters.lastIndex)))
                '>' -> parent?.addParameter(getTypeIdentifier()) ?: Complete(getTypeIdentifier())
                else -> unexpected(c)
            }

            fun addParameter(parameter: TypeIdentifier) = copy(parameters = parameters + parameter)

            override fun getTypeIdentifier() = TypeIdentifier.Parameterised(typeName, parameters)
        }

        /**
         * We are adding array-ness to some type identifier.
         */
        data class ParsingArray(val componentType: TypeIdentifier, override val parent: ParseState.ParsingParameterList?) : ParseState() {
            override fun accept(c: Char) = when (c) {
                ' ' -> this
                'p' -> ParsingArray(forcePrimitive(componentType), parent)
                ']' -> parent?.addParameter(getTypeIdentifier()) ?: Complete(getTypeIdentifier())
                else -> unexpected(c)
            }

            override fun getTypeIdentifier() = TypeIdentifier.ArrayOf(componentType)

            private fun forcePrimitive(componentType: TypeIdentifier): TypeIdentifier =
                    TypeIdentifier.forClass(Primitives.unwrap(componentType.getLocalType().asClass()))
        }

        /**
         * We have a complete type identifier, and all we can do to it is add array-ness.
         */
        data class Complete(val identifier: TypeIdentifier) : ParseState() {
            override val parent: ParseState.ParsingParameterList? get() = null
            override fun accept(c: Char): ParseState = when (c) {
                ' ' -> this
                '[' -> ParsingArray(identifier, null)
                else -> unexpected(c)
            }

            override fun getTypeIdentifier() = identifier
        }
    }

    private val simplified = mapOf(
            "string" to String::class,
            "boolean" to Boolean::class,
            "byte" to Byte::class,
            "char" to Char::class,
            "int" to Int::class,
            "short" to Short::class,
            "long" to Long::class,
            "double" to Double::class,
            "float" to Float::class,
            "ubyte" to UnsignedByte::class,
            "uint" to UnsignedInteger::class,
            "ushort" to UnsignedShort::class,
            "ulong" to UnsignedLong::class,
            "decimal32" to Decimal32::class,
            "decimal64" to Decimal64::class,
            "decimal128" to Decimal128::class,
            "binary" to ByteArray::class,
            "timestamp" to Date::class,
            "uuid" to UUID::class,
            "symbol" to Symbol::class).mapValues { (_, v) ->
        TypeIdentifier.forClass(v.javaObjectType)
    }
}