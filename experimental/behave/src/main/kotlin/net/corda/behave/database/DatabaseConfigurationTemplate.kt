/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.database

import net.corda.behave.node.configuration.DatabaseConfiguration

open class DatabaseConfigurationTemplate {

    open val connectionString: (DatabaseConfiguration) -> String = { "" }

    protected open val config: (DatabaseConfiguration) -> String = { "" }

    fun generate(config: DatabaseConfiguration) = config(config).trimMargin()
}