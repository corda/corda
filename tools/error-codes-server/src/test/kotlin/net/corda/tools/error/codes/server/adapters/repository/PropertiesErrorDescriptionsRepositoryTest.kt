package net.corda.tools.error.codes.server.adapters.repository

import net.corda.tools.error.codes.server.domain.ErrorCode
import net.corda.tools.error.codes.server.domain.ErrorDescription
import net.corda.tools.error.codes.server.domain.ErrorDescriptionLocation
import net.corda.tools.error.codes.server.domain.PlatformEdition
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.test.context.junit.jupiter.SpringJUnitJupiterConfig
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File
import java.nio.file.Files
import java.util.*
import javax.inject.Inject

@SpringJUnitJupiterConfig(PropertiesErrorDescriptionsRepositoryTest.Configuration::class)
internal class PropertiesErrorDescriptionsRepositoryTest : ErrorDescriptionsRepositoryTestSpecification {

    private companion object {

        private var propertiesFile: File = Files.createTempFile("error_code", ".properties").toFile().also(File::deleteOnExit)
    }

    @Inject
    override lateinit var repository: PropertiesErrorDescriptionsRepository

    override fun prepareDownstream(vararg values: Pair<ErrorCode, Flux<out ErrorDescription>>) {

        val properties = values.map { pair -> pair.toEntry() }.fold(Properties()) { props, entry ->
            props[entry.first] = entry.second
            props
        }
        propertiesFile.outputStream().use {
            properties.store(it, null)
        }
    }

    private fun Pair<ErrorCode, Flux<out ErrorDescription>>.toEntry(): Pair<String, String> {

        return first.value to serialise(second.collectList().switchIfEmpty(Mono.empty()).block()!!)
    }

    private fun serialise(descriptions: List<ErrorDescription>): String {

        return descriptions.joinToString("|", transform = ::serialise)
    }

    private fun serialise(description: ErrorDescription): String {

        val serialised = StringBuilder()
        val location = description.location
        when (location) {
            is ErrorDescriptionLocation.External -> serialised.append(location.uri.toASCIIString())
        }
        serialised.append(",")
        when (description.coordinates.platformEdition) {
            is PlatformEdition.OpenSource -> serialised.append("OS")
            else -> serialised.append("ENT")
        }
        serialised.append(",")
        description.coordinates.releaseVersion.apply {
            serialised.append("$major.$minor.$patch")
        }
        return serialised.toString()
    }

    @ComponentScan(basePackageClasses = [PropertiesErrorDescriptionsRepository::class], excludeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PropertiesFileLoader.Configuration::class])])
    @SpringBootApplication
    internal open class Configuration {

        @Bean
        open fun propertiesFileLoaderConfiguration(): PropertiesFileLoader.Configuration {

            return object : PropertiesFileLoader.Configuration {

                override val propertiesFile = PropertiesErrorDescriptionsRepositoryTest.propertiesFile
            }
        }
    }
}