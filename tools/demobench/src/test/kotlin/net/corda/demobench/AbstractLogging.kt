package net.corda.demobench

import net.corda.demobench.config.LoggingConfig
import org.junit.BeforeClass

abstract class AbstractLogging {

    /*
     * Workaround for bug in Gradle?
     * @see http://issues.gradle.org/browse/GRADLE-2524
     */
    companion object {
        @BeforeClass
        @JvmStatic fun `setup logging`() {
            LoggingConfig()
        }
    }

}