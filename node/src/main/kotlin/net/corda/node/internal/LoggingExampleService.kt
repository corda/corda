package net.corda.node.internal

import net.corda.core.utilities.loggerFor
import javax.inject.Named

@Named
internal class LoggingExampleService : ExampleService {

    companion object {
        val logger = loggerFor<LoggingExampleService>()
    }

    override fun doStuff() {
        logger.info("DOING INCREDIBLY INTERESTING STUFF HERE!")
    }
}