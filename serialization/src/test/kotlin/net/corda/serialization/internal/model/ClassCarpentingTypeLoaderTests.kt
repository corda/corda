package net.corda.serialization.internal.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.reflect.TypeToken
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.asClass
import net.corda.serialization.internal.carpenter.ClassCarpenterImpl
import org.junit.Test
import java.lang.reflect.Type
import kotlin.test.assertEquals

class ClassCarpentingTypeLoaderTests {

    val carpenter = ClassCarpenterImpl(AllWhitelist)
    val remoteTypeCarpenter = SchemaBuildingRemoteTypeCarpenter(carpenter)
    val typeLoader = ClassCarpentingTypeLoader(remoteTypeCarpenter, carpenter.classloader)

    @Test
    fun `carpent some related classes`() {
        val addressInformation = RemoteTypeInformation.Composable(
                "address",
                typeIdentifierOf("net.corda.test.Address"),
                mapOf(
                        "addressLines" to remoteType<Array<String>>().mandatory,
                        "postcode" to remoteType<String>().optional
                ), emptyList(), emptyList()
        )

        val listOfAddresses = RemoteTypeInformation.Parameterised(
                "list<Address>",
                TypeIdentifier.Parameterised(
                        "java.util.List",
                        null,
                        listOf(addressInformation.typeIdentifier)),
                listOf(addressInformation))

        val personInformation = RemoteTypeInformation.Composable(
                "person",
                typeIdentifierOf("net.corda.test.Person"),
                mapOf(
                        "name" to remoteType<String>().mandatory,
                        "age" to remoteType(TypeIdentifier.forClass(Int::class.javaPrimitiveType!!)).mandatory,
                        "address" to addressInformation.mandatory,
                        "previousAddresses" to listOfAddresses.mandatory
                ), emptyList(), emptyList())

        val types = typeLoader.load(listOf(personInformation, addressInformation, listOfAddresses))
        val addressType = types[addressInformation.typeIdentifier]!!
        val personType = types[personInformation.typeIdentifier]!!

        val address = addressType.make(arrayOf("23 Acacia Avenue", "Surbiton"), "VB6 5UX")
        val previousAddress = addressType.make(arrayOf("99 Penguin Lane", "Doncaster"), "RA8 81T")

        val person = personType.make("Arthur Putey", 42, address, listOf(previousAddress))
        val personJson = ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(person)
        assertEquals("""
            {
              "name" : "Arthur Putey",
              "age" : 42,
              "address" : {
                "addressLines" : [ "23 Acacia Avenue", "Surbiton" ],
                "postcode" : "VB6 5UX"
              },
              "previousAddresses" : [ {
                "addressLines" : [ "99 Penguin Lane", "Doncaster" ],
                "postcode" : "RA8 81T"
              } ]
            }
        """.trimIndent(), personJson)
    }

    private fun Type.make(vararg params: Any): Any {
        val cls = this.asClass()
        val paramTypes = params.map { it::class.javaPrimitiveType ?: it::class.javaObjectType }.toTypedArray()
        val constructor = cls.constructors.find { it.parameterTypes.zip(paramTypes).all {
            (expected, actual) -> expected.isAssignableFrom(actual)
        } }!!
        return constructor.newInstance(*params)
    }

    private fun typeIdentifierOf(typeName: String, vararg parameters: TypeIdentifier) =
            if (parameters.isEmpty()) TypeIdentifier.Unparameterised(typeName)
            else TypeIdentifier.Parameterised(typeName, null, parameters.toList())

    private inline fun <reified T> typeOf(): Type = object : TypeToken<T>() {}.type
    private inline fun <reified T> typeIdentifierOf(): TypeIdentifier = TypeIdentifier.forGenericType(typeOf<T>())
    private inline fun <reified T> remoteType(): RemoteTypeInformation = remoteType(typeIdentifierOf<T>())

    private fun remoteType(typeIdentifier: TypeIdentifier): RemoteTypeInformation =
        when (typeIdentifier) {
            is TypeIdentifier.Unparameterised -> RemoteTypeInformation.Unparameterised(typeIdentifier.prettyPrint(), typeIdentifier)
            is TypeIdentifier.Parameterised -> RemoteTypeInformation.Parameterised(
                    typeIdentifier.prettyPrint(),
                    typeIdentifier,
                    typeIdentifier.parameters.map { remoteType(it) })
            is TypeIdentifier.ArrayOf -> RemoteTypeInformation.AnArray(
                    typeIdentifier.prettyPrint(),
                    typeIdentifier,
                    remoteType(typeIdentifier.componentType))
            is TypeIdentifier.Erased -> RemoteTypeInformation.Unparameterised(
                typeIdentifier.prettyPrint(),
                TypeIdentifier.Unparameterised(typeIdentifier.name))
            is TypeIdentifier.TopType -> RemoteTypeInformation.Top
            is TypeIdentifier.UnknownType -> RemoteTypeInformation.Unknown
        }

    private val RemoteTypeInformation.optional: RemotePropertyInformation get() =
            RemotePropertyInformation(this, false)

    private val RemoteTypeInformation.mandatory: RemotePropertyInformation get() =
        RemotePropertyInformation(this, true)
}