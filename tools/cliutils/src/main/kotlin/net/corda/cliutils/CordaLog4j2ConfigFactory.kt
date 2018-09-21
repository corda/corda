package net.corda.cliutils

import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.Order
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.xml.XmlConfiguration
import org.apache.logging.log4j.core.impl.LogEventFactory

@Plugin(name = "CordaLog4j2ConfigFactory", category = "ConfigurationFactory")
@Order(10)
class CordaLog4j2ConfigFactory : ConfigurationFactory() {

    private companion object {
        private val SUPPORTED_TYPES = arrayOf(".xml", "*")
    }

    override fun getConfiguration(loggerContext: LoggerContext, source: ConfigurationSource): Configuration = ErrorCodeAppendingConfiguration(loggerContext, source)

    override fun getSupportedTypes() = SUPPORTED_TYPES

    private class ErrorCodeAppendingConfiguration(loggerContext: LoggerContext, source: ConfigurationSource) : XmlConfiguration(loggerContext, source) {

        override fun doConfigure() {

            super.doConfigure()
            loggers.values.forEach {
                val existingFactory = it.logEventFactory
                it.logEventFactory = LogEventFactory { loggerName, marker, fqcn, level, message, properties, error -> existingFactory.createEvent(loggerName, marker, fqcn, level, message?.withErrorCodeFor(error, level), properties, error) }
            }
        }
    }
}