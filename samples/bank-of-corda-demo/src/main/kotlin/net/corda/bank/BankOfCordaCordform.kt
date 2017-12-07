package net.corda.bank

import joptsimple.OptionParser
import net.corda.bank.api.BankOfCordaClientApi
import net.corda.bank.api.BankOfCordaWebApi.IssueRequestParams
import net.corda.cordform.CordformContext
import net.corda.cordform.CordformDefinition
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.VisibleForTesting
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.Permissions.Companion.all
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.nodeapi.internal.config.User
import net.corda.testing.BOC
import net.corda.testing.internal.demorun.*
import java.util.*
import kotlin.system.exitProcess

val BIGCORP_NAME = CordaX500Name(organisation = "BigCorporation", locality = "New York", country = "US")
private val NOTARY_NAME = CordaX500Name(organisation = "Notary Service", locality = "Zurich", country = "CH")
private val BOC_RPC_PORT = 10006
private val BOC_WEB_PORT = 10007

class BankOfCordaCordform : CordformDefinition() {
    init {
        cordappPackages += "net.corda.finance"
        node {
            name(NOTARY_NAME)
            notary(NotaryConfig(validating = true))
            p2pPort(10002)
            rpcPort(10003)
        }
        node {
            name(CordaX500Name(organisation = "BankOfCorda", locality = "London", country = "GB"))
            extraConfig = mapOf("issuableCurrencies" to listOf("USD"))
            p2pPort(10005)
            rpcPort(BOC_RPC_PORT)
            webPort(BOC_WEB_PORT)
            rpcUsers(User("bankUser", "test", setOf(all())))
        }
        node {
            name(BIGCORP_NAME)
            p2pPort(10008)
            rpcPort(10009)
            webPort(10010)
            rpcUsers(User("bigCorpUser", "test", setOf(all())))
        }
    }

    override fun setup(context: CordformContext) = Unit
}

object DeployNodes {
    @JvmStatic
    fun main(args: Array<String>) {
        BankOfCordaCordform().deployNodes()
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

    @VisibleForTesting
    fun requestWebIssue(amount: Amount<Currency>) {
        BankOfCordaClientApi.requestWebIssue(NetworkHostAndPort("localhost", BOC_WEB_PORT), createParams(amount, NOTARY_NAME))
    }

    private fun createParams(amount: Amount<Currency>, notaryName: CordaX500Name): IssueRequestParams {
        return IssueRequestParams(amount, BIGCORP_NAME, "1", BOC.name, notaryName.copy(commonName = ValidatingNotaryService.id))
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
