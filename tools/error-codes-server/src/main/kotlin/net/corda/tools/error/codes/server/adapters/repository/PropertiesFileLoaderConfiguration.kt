package net.corda.tools.error.codes.server.adapters.repository

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import java.io.File
import java.nio.file.Paths
import javax.inject.Inject
import javax.inject.Named

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