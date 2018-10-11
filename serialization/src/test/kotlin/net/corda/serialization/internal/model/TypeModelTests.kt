package net.corda.serialization.internal.model

import net.corda.serialization.internal.model.LocalTypeInformation.*
import org.junit.Test
import kotlin.test.assertEquals

class TypeModelTests {

    open class CollectionHolder<T>(val list: List<T>, val map: Map<String, T>)

    class StringCollectionHolder(list: List<String>, map: Map<String, String>): CollectionHolder<String>(list, map)

    @Test
    fun `Primitives and collections`() {
        val string = interpret<String>()
        assertEquals(APrimitive(classOf<String>(), TypeIdentifier.Unparameterised("java.lang.String")), string)

        println(interpret<StringCollectionHolder>().prettyPrint())
        println(interpret<CollectionHolder<Int>>().prettyPrint())
    }

    fun TypeIdentifier.prettyPrint(indent: Int = 0): String =
        "  ".repeat(indent) + when(this) {
            is TypeIdentifier.Unknown -> "Object"
            is TypeIdentifier.Any -> "?"
            is TypeIdentifier.Unparameterised -> "${className}"
            is TypeIdentifier.Erased -> "${className} (erased)"
            is TypeIdentifier.ArrayOf -> this.componentType.prettyPrint() + "[]"
            is TypeIdentifier.Parameterised ->
                "${this.className}" + this.parameters.joinToString(", ", "[", "]") {
                    it.prettyPrint()
                }
        }

    fun LocalTypeInformation.prettyPrint(indent: Int = 0): String {
        return when(this) {
            is LocalTypeInformation.Any,
            is LocalTypeInformation.Unknown,
            is LocalTypeInformation.APrimitive,
            is LocalTypeInformation.AnInterface,
            is LocalTypeInformation.AnArray,
            is LocalTypeInformation.ACollection -> typeIdentifier.prettyPrint(indent)
            is LocalTypeInformation.AnObject -> typeIdentifier.prettyPrint(indent) +
                    this.interfaces.joinToString("\n", "\n-\n", "\n-\n") { it.prettyPrint(indent + 1) } + "\n" +
                    this.properties.entries.joinToString("\n", "\n", "") { it.prettyPrint(indent + 1) }
            else -> typeIdentifier.prettyPrint(indent)
        }
    }

    fun Map.Entry<String, LocalTypeInformation>.prettyPrint(indent: Int): String =
            "  ".repeat(indent) + key + ": " + value.prettyPrint(indent + 1).trimStart()

    private inline fun <reified T> interpret() = LocalTypeModel().interpret(T::class.java)
    private inline fun <reified T> classOf(): Class<*> = T::class.java
}