package net.corda.networkbuilder

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.microsoft.azure.management.resources.ResourceGroup
import com.microsoft.azure.management.resources.fluentcore.arm.Region
import java.util.Locale

class Constants {

    companion object {
        const val NODE_P2P_PORT = 10020
        const val NODE_SSHD_PORT = 12222
        const val NODE_RPC_PORT = 10003
        const val NODE_RPC_ADMIN_PORT = 10005

        const val BOOTSTRAPPER_DIR_NAME = ".bootstrapper"

        fun getContextMapper(): ObjectMapper {
            val objectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            objectMapper.registerModule(object : SimpleModule() {}.let {
                it.addSerializer(Region::class.java, object : JsonSerializer<Region>() {
                    override fun serialize(value: Region, gen: JsonGenerator, serializers: SerializerProvider?) {
                        gen.writeString(value.name())
                    }
                })
                it.addDeserializer(Region::class.java, object : JsonDeserializer<Region>() {
                    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Region {
                        return Region.findByLabelOrName(p.valueAsString)
                    }
                })
            })
            return objectMapper
        }

        val ALPHA_NUMERIC_ONLY_REGEX = "[^\\p{IsAlphabetic}\\p{IsDigit}]".toRegex()
        val ALPHA_NUMERIC_DOT_AND_UNDERSCORE_ONLY_REGEX = "[^\\p{IsAlphabetic}\\p{IsDigit}._]".toRegex()
        const val REGION_ARG_NAME = "REGION"

        fun ResourceGroup.restFriendlyName(): String {
            return this.name().replace(ALPHA_NUMERIC_ONLY_REGEX, "").lowercase(Locale.getDefault())
        }
    }
}