package net.corda.serialization.internal.model

import net.corda.serialization.internal.amqp.CustomSerializerRegistry
import java.lang.reflect.Type

data class FingerprintedLocalTypeInformation(val fingerprint: String, val localTypeInformation: LocalTypeInformation)

interface FingerprintingLocalTypeModel {

    companion object {
        fun build(customSerializerRegistry: CustomSerializerRegistry, typeModel: LocalTypeModel): FingerprintingLocalTypeModel {
            val fingerprinter = CustomisableLocalTypeInformationFingerPrinter(customSerializerRegistry, typeModel)
            return DefaultFingerprintingLocalTypeModel(typeModel, fingerprinter)
        }
    }

    fun inspect(type: Type): FingerprintedLocalTypeInformation

}

class DefaultFingerprintingLocalTypeModel(
        val typeModel: LocalTypeModel,
        val fingerprinter: FingerPrinter): FingerprintingLocalTypeModel {

    private val cache: MutableMap<TypeIdentifier, FingerprintedLocalTypeInformation> = DefaultCacheProvider.createCache()

    override fun inspect(type: Type): FingerprintedLocalTypeInformation {
        val identifier = TypeIdentifier.forGenericType(type)
        return cache.computeIfAbsent(identifier) { _ ->
            val typeInformation = typeModel.inspect(type)
            val fingerprint = fingerprinter.fingerprint(typeInformation)
            FingerprintedLocalTypeInformation(fingerprint, typeInformation)
        }
    }
}