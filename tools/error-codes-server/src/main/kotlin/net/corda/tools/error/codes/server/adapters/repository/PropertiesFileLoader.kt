package net.corda.tools.error.codes.server.adapters.repository

import net.corda.tools.error.codes.server.domain.annotations.Adapter
import net.corda.tools.error.codes.server.domain.loggerFor
import java.io.File
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