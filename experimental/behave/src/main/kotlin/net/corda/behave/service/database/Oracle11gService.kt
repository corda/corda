/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.service.database

import net.corda.behave.database.DatabaseType
import net.corda.behave.service.ServiceSettings
import net.corda.core.utilities.minutes

class Oracle11gService(
        name: String,
        port: Int,
        password: String,
        settings: ServiceSettings = ServiceSettings(startupTimeout = 2.minutes)
) : OracleService(name, port,"Oracle Database 11g Express Edition instance is already started", password, settings) {

    override val baseImage = "sath89/oracle-xe-11g"
    override val type = DatabaseType.ORACLE_11G

    companion object {
        const val driver = "ojdbc6.jar"
    }
}