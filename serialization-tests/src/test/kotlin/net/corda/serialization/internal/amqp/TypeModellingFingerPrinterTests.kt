package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.NotSerializable
import net.corda.serialization.internal.model.ConfigurableLocalTypeModel
import net.corda.serialization.internal.model.LocalTypeInformation
import net.corda.serialization.internal.model.TypeModellingFingerPrinter
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TypeModellingFingerPrinterTests {

    val descriptorBasedSerializerRegistry = DefaultDescriptorBasedSerializerRegistry()
    val customRegistry = CachingCustomSerializerRegistry(descriptorBasedSerializerRegistry)
    val fingerprinter = TypeModellingFingerPrinter(customRegistry, true)

    // See https://r3-cev.atlassian.net/browse/CORDA-2266
    @Test
    fun `Object and wildcard are fingerprinted differently`() {
        val objectType = LocalTypeInformation.Top
        val anyType = LocalTypeInformation.Unknown

        assertNotEquals(fingerprinter.fingerprint(objectType), fingerprinter.fingerprint(anyType))
    }

    // Not serializable, because there is no readable property corresponding to the constructor parameter
    class NonSerializable(a: String)

    class HasTypeParameter<T>
    data class SuppliesTypeParameter(val value: HasTypeParameter<NonSerializable>)

    // See https://r3-cev.atlassian.net/browse/CORDA-2848
    @Test
    fun `can fingerprint type with non-serializable type parameter`() {
        val typeModel = ConfigurableLocalTypeModel(WhitelistBasedTypeModelConfiguration(AllWhitelist, customRegistry))
        val typeInfo = typeModel.inspect(SuppliesTypeParameter::class.java)

        assertThat(typeInfo).isInstanceOf(LocalTypeInformation.Composable::class.java)
        val propertyTypeInfo = typeInfo.propertiesOrEmptyMap["value"]?.type as LocalTypeInformation.Composable
        assertThat(propertyTypeInfo.typeParameters[0]).isInstanceOf(LocalTypeInformation.NonComposable::class.java)

        fingerprinter.fingerprint(typeInfo)
    }
}