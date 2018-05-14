/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.internal

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.node.internal.cordapp.CordappConfigProvider

class MockCordappConfigProvider : CordappConfigProvider {
    val cordappConfigs = mutableMapOf<String, Config>()

    override fun getConfigByName(name: String): Config {
        return if (cordappConfigs.containsKey(name)) {
            cordappConfigs[name]!!
        } else {
            ConfigFactory.empty()
        }
    }
}