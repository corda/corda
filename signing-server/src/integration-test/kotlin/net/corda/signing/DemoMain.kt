package net.corda.signing

import net.corda.signing.configuration.Parameters
import java.util.*
import net.corda.signing.SigningServiceIntegrationTest.Companion.DB_NAME
import net.corda.signing.SigningServiceIntegrationTest.Companion.HOST
import net.corda.signing.SigningServiceIntegrationTest.Companion.H2_TCP_PORT

/**
 * The main method for an interactive HSM signing service test/demo. It is supposed to be executed with the
 * `DEMO - Create CSR and poll` method located in the [SigningServiceIntegrationTest], which is responsible for simulating
 * CSR creation on the Doorman side.
 * Execution instructions:
 * 1) It is assumed that the HSM simulator is installed locally (or via means of the VM) and accessible under the address
 * configured under the 'device' parameter (defaults to 3001@127.0.0.1). If that is not the case please specify
 * a correct 'device' parameter value. Also, it is assumed that the HSM setup consists of a cryptographic user eligible to
 * sign the CSRs (and potentially to generate new root and intermediate certificates).
 * 2) Run the `DEMO - Create CSR and poll` as a regular test from your IntelliJ.
 * The method starts the doorman, creates 3 CSRs for ALICE, BOB and CHARLIE
 * and then polls the doorman until all 3 requests are signed.
 * 3) Once the `DEMO - Create CSR and poll` is started, execute the following main method
 * and interact with console menu options presented.
 */
fun main(args: Array<String>) {
    run(Parameters(
            dataSourceProperties = makeTestDataSourceProperties("localhost"),
            databaseProperties = makeNotInitialisingTestDatabaseProperties()
    ))
}

private fun makeTestDataSourceProperties(nodeName: String): Properties {
    val props = Properties()
    props.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
    props.setProperty("dataSource.url", "jdbc:h2:tcp://$HOST:$H2_TCP_PORT/mem:$DB_NAME;DB_CLOSE_DELAY=-1")
    props.setProperty("dataSource.user", "sa")
    props.setProperty("dataSource.password", "")
    return props
}
