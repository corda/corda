package net.corda.tools.error.codes.server.adapters.repository

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import net.corda.tools.error.codes.server.domain.annotations.Adapter
import net.corda.tools.error.codes.server.domain.loggerFor
import java.io.File
import java.nio.file.Paths
import java.util.*
import javax.inject.Inject
import javax.inject.Named

@Adapter
@Named
internal class PropertiesFileLoader @Inject constructor(private val configuration: PropertiesFileLoader.Configuration) {

    private companion object {

        private val logger = loggerFor<PropertiesFileLoader>()
    }

    internal fun load(): Properties {

        val properties = Properties()
        logger.info("Loading error code locations from file \"${configuration.propertiesFile.toPath().toAbsolutePath()}\"")
        configuration.propertiesFile.inputStream().use {
            properties.load(it)
        }
        return properties
    }

    interface Configuration {

        val propertiesFile: File
    }
}

// This allows injecting functions instead of types.
@Adapter
@Named
internal class LoadedProperties @Inject constructor(private val loader: PropertiesFileLoader) : () -> Properties {

    override fun invoke() = loader.load()
}

@Adapter
@Named
internal class PropertiesFileLoaderConfiguration @Inject constructor(applyConfigStandards: (Config) -> Config) : PropertiesFileLoader.Configuration {

    private companion object {

        private const val CONFIGURATION_SECTION_PATH = "configuration.adapters.repositories.properties.file_based"

        private object Spec : ConfigSpec(CONFIGURATION_SECTION_PATH) {

            val properties_file_path by optional<String?>(null)
            val properties_file_resource_name by required<String>()
        }
    }

    private val config = applyConfigStandards.invoke(Config { addSpec(Spec) })

    override val propertiesFile: File by lazy {

        (config[Spec.properties_file_path]?.let { Paths.get(it).toAbsolutePath() } ?: Paths.get(this.javaClass.classLoader.getResource(config[Spec.properties_file_resource_name]).toURI())).toFile()
    }
}