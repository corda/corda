package net.corda.nodeapi.internal.persistence

import liquibase.logging.core.AbstractLogService
import org.slf4j.LoggerFactory

class Slf4jLiquibaseLogService : AbstractLogService() {

    override fun getPriority() = Integer.MAX_VALUE
    override fun getLog(clazz: Class<*>?) : liquibase.logging.Logger {
        return Slf4jLiquibaseLogger(LoggerFactory.getLogger(clazz))
    }

}
