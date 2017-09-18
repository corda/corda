package net.corda.bank

import joptsimple.OptionParser
import net.corda.bank.api.BankOfCordaClientApi
import net.corda.core.identity.CordaX500Name
import net.corda.bank.api.IssueAndPaymentRequestParams
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.finance.flows.CashExitFlow
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.services.FlowPermissions.Companion.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import net.corda.testing.BOC
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.driver.driver
import java.util.*
import kotlin.system.exitProcess

/**
 * This entry point allows for command line running of the Bank of Corda functions on nodes started by BankOfCordaDriver.kt.
 */
fun main(args: Array<String>) {
    BankOfCordaDriver().main(args)
}

val BANK_USERNAME = "bankUser"
val BIGCORP_USERNAME = "bigCorpUser"

val BIGCORP_LEGAL_NAME = CordaX500Name(organisation = "BigCorporation", locality = "London", country = "GB")

private class BankOfCordaDriver {
    enum class Role {
        ISSUE_CASH_RPC,
        ISSUE_PAY_CASH_RPC,
        ISSUE_PAY_CASH_WEB,
        ISSUER,
        PAY_CASH_RPC
    }

    fun main(args: Array<String>) {
        val parser = OptionParser()
        val roleArgSyntax = EnumSet.allOf(Role::class.java).map(Enum<*>::name).joinToString("|", "[", "]")
        val roleArg = parser.accepts("role").withRequiredArg().ofType(Role::class.java).describedAs(roleArgSyntax)
        val quantity = parser.accepts("quantity").withOptionalArg().ofType(Long::class.java)
        val currency = parser.accepts("currency").withOptionalArg().ofType(String::class.java).describedAs("[GBP|USD|CHF|EUR]")
        val options = try {
            parser.parse(*args)
        } catch (e: Exception) {
            println(e.message)
            printHelp(parser)
            exitProcess(1)
        }

        // What happens next depends on the role.
        // The ISSUER will launch a Bank of Corda node
        // The ISSUE_CASH will request some Cash from the ISSUER on behalf of Big Corporation node
        val role = options.valueOf(roleArg)!!

        val requestParams = IssueAndPaymentRequestParams(options.valueOf(quantity), options.valueOf(currency), BIGCORP_LEGAL_NAME, "1", BOC.name, DUMMY_NOTARY.name)

        try {
            when (role) {
                Role.ISSUER -> {
                    driver(dsl = {
                        val bankUser = User(
                                BANK_USERNAME,
                                "test",
                                permissions = setOf(
                                        startFlowPermission<CashIssueAndPaymentFlow>(),
                                        startFlowPermission<CashExitFlow>()))
                        val bigCorpUser = User(BIGCORP_USERNAME, "test",
                                permissions = setOf(
                                        startFlowPermission<CashPaymentFlow>()))
                        startNode(providedName = DUMMY_NOTARY.name,
                                advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type)))
                        val bankOfCorda = startNode(
                                providedName = BOC.name,
                                rpcUsers = listOf(bankUser),
                                advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("issuer.USD"))))
                        startNode(providedName = BIGCORP_LEGAL_NAME, rpcUsers = listOf(bigCorpUser))
                        startWebserver(bankOfCorda.get())
                        waitForAllNodesToFinish()
                    }, isDebug = true)
                }
                Role.ISSUE_CASH_RPC -> {
                    println("Requesting Cash issue and payment via RPC ...")
                    val result = BankOfCordaClientApi(NetworkHostAndPort("localhost", 10006)).requestRPCIssue(requestParams)
                    println("Success!! You transaction receipt is ${result.tx.id}")
                }
                Role.ISSUE_PAY_CASH_RPC -> {
                    println("Requesting Cash issue and payment via RPC ...")
                    val result = BankOfCordaClientApi(NetworkHostAndPort("localhost", 10006)).requestRPCIssueAndPayment(requestParams)
                    println("Success!! You transaction receipt is ${result.tx.id}")
                }
                Role.ISSUE_PAY_CASH_WEB -> {
                    println("Requesting Cash issue and payment via Web ...")
                    val result = BankOfCordaClientApi(NetworkHostAndPort("localhost", 10007)).requestWebIssueAndPayment(requestParams)
                    if (result)
                        println("Successfully processed Cash Issue request")
                }
                Role.PAY_CASH_RPC -> {
                    println("Requesting Cash issue and payment via RPC ...")
                    val result = BankOfCordaClientApi(NetworkHostAndPort("localhost", 10006)).requestRPCPayment(requestParams)
                    println("Success!! You transaction receipt is ${result.tx.id}")
                }
            }
        } catch (e: Exception) {
            println("Exception occurred: $e \n ${e.printStackTrace()}")
            exitProcess(1)
        }
    }

    fun printHelp(parser: OptionParser) {
        println("""
        Usage: bank-of-corda --role ISSUER
               bank-of-corda --role (ISSUE_CASH_RPC|ISSUE_CASH_WEB) --quantity <quantity> --currency <currency>

        Please refer to the documentation in docs/build/index.html for more info.

        """.trimIndent())
        parser.printHelpOn(System.out)
    }
}


