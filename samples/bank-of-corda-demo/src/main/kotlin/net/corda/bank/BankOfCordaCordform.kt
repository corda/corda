package net.corda.bank

import joptsimple.OptionParser
import net.corda.bank.api.BankOfCordaClientApi
import net.corda.bank.api.BankOfCordaWebApi.IssueRequestParams
import net.corda.cordform.CordappDependency
import net.corda.cordform.CordformContext
import net.corda.cordform.CordformDefinition
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.VisibleForTesting
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.Permissions.Companion.all
import net.corda.node.services.config.NotaryConfig
import net.corda.testing.node.internal.demorun.*
import net.corda.testing.core.BOC_NAME
import net.corda.testing.node.User
import java.util.*
import kotlin.system.exitProcess

val BIGCORP_NAME = CordaX500Name(organisation = "BigCorporation", locality = "New York", country = "US")
private val NOTARY_NAME = CordaX500Name(organisation = "Notary Service", locality = "Zurich", country = "CH")
const val BOC_RPC_PORT = 10006
const val BIGCORP_RPC_PORT = 10009
private const val BOC_RPC_ADMIN_PORT = 10015
private const val BOC_WEB_PORT = 10007

const val BOC_RPC_USER = "bankUser"
const val BOC_RPC_PWD = "test"

const val BIGCORP_RPC_USER = "bigCorpUser"
const val BIGCORP_RPC_PWD = "test"

class BankOfCordaCordform : CordformDefinition() {

    init {
        node {
            name(NOTARY_NAME)
            notary(NotaryConfig(validating = true))
            p2pPort(10002)
            rpcSettings {
                address("localhost:10003")
                adminAddress("localhost:10004")
            }
            devMode(true)
            extraConfig = mapOf("h2Settings" to mapOf("address" to "localhost:10016"))
        }
        node {
            name(BOC_NAME)
            extraConfig = mapOf("custom" to mapOf("issuableCurrencies" to listOf("USD")),
                    "h2Settings" to mapOf("address" to "localhost:10017"))
            p2pPort(10005)
            rpcSettings {
                address("localhost:$BOC_RPC_PORT")
                adminAddress("localhost:$BOC_RPC_ADMIN_PORT")
            }
            webPort(BOC_WEB_PORT)
            rpcUsers(User(BOC_RPC_USER, BOC_RPC_PWD, setOf(all())))
            devMode(true)
        }
        node {
            name(BIGCORP_NAME)
            p2pPort(10008)
            rpcSettings {
                address("localhost:$BIGCORP_RPC_PORT")
                adminAddress("localhost:10011")
            }
            webPort(10010)
            rpcUsers(User(BIGCORP_RPC_USER, BIGCORP_RPC_PWD, setOf(all())))
            devMode(true)
            extraConfig = mapOf("h2Settings" to mapOf("address" to "localhost:10018"))
        }
    }

    override fun setup(context: CordformContext) = Unit

    override fun getCordappDependencies(): List<CordappDependency> {
        return listOf(CordappDependency(":finance"))
    }
}

object DeployNodes {
    @JvmStatic
    fun main(args: Array<String>) {
        BankOfCordaCordform().nodeRunner().scanPackages(listOf("net.corda.finance")).deployAndRunNodes()
    }
}

object IssueCash {
    @JvmStatic
    fun main(args: Array<String>) {
        val parser = OptionParser()
        val roleArg = parser.accepts("role").withRequiredArg().ofType(Role::class.java).describedAs("[ISSUER|ISSUE_CASH_RPC|ISSUE_CASH_WEB]")
        val quantity = parser.accepts("quantity").withOptionalArg().ofType(Long::class.java)
        val currency = parser.accepts("currency").withOptionalArg().ofType(String::class.java).describedAs("[GBP|USD|CHF|EUR]")
        val options = try {
            parser.parse(*args)
        } catch (e: Exception) {
            println(e.message)
            printHelp(parser)
            exitProcess(1)
        }

        val role = options.valueOf(roleArg)!!
        val amount = Amount(options.valueOf(quantity), Currency.getInstance(options.valueOf(currency)))
        when (role) {
            Role.ISSUE_CASH_RPC -> {
                println("Requesting Cash via RPC ...")
                val result = requestRpcIssue(amount)
                println("Success!! Your transaction receipt is ${result.tx.id}")
            }
            Role.ISSUE_CASH_WEB -> {
                println("Requesting Cash via Web ...")
                requestWebIssue(amount)
                println("Successfully processed Cash Issue request")
            }
        }
    }

    @VisibleForTesting
    fun requestRpcIssue(amount: Amount<Currency>): SignedTransaction {
        return BankOfCordaClientApi.requestRPCIssue(NetworkHostAndPort("localhost", BOC_RPC_PORT), createParams(amount, NOTARY_NAME))
    }

    private fun requestWebIssue(amount: Amount<Currency>) {
        BankOfCordaClientApi.requestWebIssue(NetworkHostAndPort("localhost", BOC_WEB_PORT), createParams(amount, NOTARY_NAME))
    }

    private fun createParams(amount: Amount<Currency>, notaryName: CordaX500Name): IssueRequestParams {
        return IssueRequestParams(amount, BIGCORP_NAME, "1", BOC_NAME, notaryName)
    }

    private fun printHelp(parser: OptionParser) {
        println("""
        Usage: bank-of-corda --role ISSUER
               bank-of-corda --role (ISSUE_CASH_RPC|ISSUE_CASH_WEB) --quantity <quantity> --currency <currency>

        Please refer to the documentation in docs/build/index.html for more info.

        """.trimIndent())
        parser.printHelpOn(System.out)
    }

    enum class Role {
        ISSUE_CASH_RPC,
        ISSUE_CASH_WEB,
    }
}
