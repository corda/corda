package com.r3.dbfailure.workflows

import com.r3.dbfailure.contracts.DbFailureContract
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import java.security.InvalidParameterException

@CordaService
class DbListenerService(services: AppServiceHub) : SingletonSerializeAsToken() {

    companion object {
        val log = contextLogger()
    }

    init {
        services.vaultService.rawUpdates.subscribe { (_, produced) ->
            produced.forEach {
                val contractState = it.state.data as? DbFailureContract.TestState
                @Suppress("TooGenericExceptionCaught") // this is fully intentional here, to allow twiddling with exceptions
                try {
                    when (CreateStateFlow.getServiceTarget(contractState?.errorTarget)) {
                        CreateStateFlow.ErrorTarget.ServiceSqlSyntaxError -> {
                            log.info("Fail with syntax error on raw statement")
                            val session = services.jdbcSession()
                            val statement = session.createStatement()
                            statement.execute(
                                    "UPDATE FAIL_TEST_STATES \n" +
                                            "BLAAA RANDOM_VALUE = NULL\n" +
                                            "WHERE transaction_id = '${it.ref.txhash}' AND output_index = ${it.ref.index};"
                            )
                            log.info("SQL result: ${statement.resultSet}")
                        }
                        CreateStateFlow.ErrorTarget.ServiceNullConstraintViolation -> {
                            log.info("Fail with null constraint violation on raw statement")
                            val session = services.jdbcSession()
                            val statement = session.createStatement()
                            statement.execute(
                                    "UPDATE FAIL_TEST_STATES \n" +
                                            "SET RANDOM_VALUE = NULL\n" +
                                            "WHERE transaction_id = '${it.ref.txhash}' AND output_index = ${it.ref.index};"
                            )
                            log.info("SQL result: ${statement.resultSet}")
                        }
                        CreateStateFlow.ErrorTarget.ServiceValidUpdate -> {
                            log.info("Update current statement")
                            val session = services.jdbcSession()
                            val statement = session.createStatement()
                            statement.execute(
                                    "UPDATE FAIL_TEST_STATES \n" +
                                            "SET RANDOM_VALUE = '${contractState!!.randomValue} Updated by service'\n" +
                                            "WHERE transaction_id = '${it.ref.txhash}' AND output_index = ${it.ref.index};"
                            )
                            log.info("SQL result: ${statement.resultSet}")
                        }
                        CreateStateFlow.ErrorTarget.ServiceReadState -> {
                            log.info("Read current state from db")
                            val session = services.jdbcSession()
                            val statement = session.createStatement()
                            statement.execute(
                                    "SELECT * FROM FAIL_TEST_STATES \n" +
                                            "WHERE transaction_id = '${it.ref.txhash}' AND output_index = ${it.ref.index};"
                            )
                            log.info("SQL result: ${statement.resultSet}")
                        }
                        CreateStateFlow.ErrorTarget.ServiceCheckForState -> {
                            log.info("Check for currently written state in the db")
                            val session = services.jdbcSession()
                            val statement = session.createStatement()
                            val rs = statement.executeQuery(
                                    "SELECT COUNT(*) FROM FAIL_TEST_STATES \n" +
                                            "WHERE transaction_id = '${it.ref.txhash}' AND output_index = ${it.ref.index};"
                            )
                            val numOfRows = if (rs.next()) rs.getInt("COUNT(*)") else 0
                            log.info("Found a state with tx:ind ${it.ref.txhash}:${it.ref.index} in " +
                                    "TEST_FAIL_STATES: ${if (numOfRows > 0) "Yes" else "No"}")
                        }
                        CreateStateFlow.ErrorTarget.ServiceThrowInvalidParameter -> {
                            log.info("Throw InvalidParameterException")
                            throw InvalidParameterException("Toys out of pram")
                        }
                        CreateStateFlow.ErrorTarget.ServiceThrowMotherOfAllExceptions -> {
                            log.info("Throw Exception")
                            throw Exception("Mother of all exceptions")
                        }
                        else -> {
                            // do nothing, everything else must be handled elsewhere
                        }
                    }
                } catch (t: Throwable) {
                    if (CreateStateFlow.getServiceExceptionHandlingTarget(contractState?.errorTarget)
                            == CreateStateFlow.ErrorTarget.ServiceSwallowErrors) {
                        log.warn("Service not letting errors escape", t)
                    } else {
                        throw t
                    }
                }
            }
        }
    }
}