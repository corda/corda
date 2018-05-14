/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.demobench

import net.corda.demobench.config.LoggingConfig
import net.corda.demobench.model.JVMConfigTest
import net.corda.demobench.model.NodeControllerTest
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.junit.runners.Suite

/*
 * Declare all test classes that need to configure Java Util Logging.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
        NodeControllerTest::class,
        JVMConfigTest::class
)
class LoggingTestSuite {

    /*
     * Workaround for bug in Gradle?
     * @see http://issues.gradle.org/browse/GRADLE-2524
     */
    companion object {
        @BeforeClass
        @JvmStatic
        fun `setup logging`() {
            LoggingConfig()
        }
    }
}
