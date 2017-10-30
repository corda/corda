package net.corda.observerdemo

// Observer DEMO
//
// This program is a simple demonstration of sending a receivable a registry, as a simplified version of trade
// finance
import joptsimple.OptionParser
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.GBP
import net.corda.observerdemo.contracts.Receivable
import net.corda.observerdemo.flow.IssueReceivableFlow
import java.time.Instant
import java.time.ZoneId

fun main(args: Array<String>) {
    ObserverDemo().main(args)
}

private class ObserverDemo {
    private companion object {
        val log = loggerFor<ObserverDemo>()
    }

    enum class Role(val legalName: CordaX500Name, val rpcPort: Int?) {
        FILER(CordaX500Name.parse("O=Bank A,L=London,C=GB"), 10006),
        REGISTRY(CordaX500Name.parse("O=Registry Service,L=New York,C=US"), 10009),
        DEBTOR(CordaX500Name.parse("O=Bank B,L=New York,C=US"), 10012),
        NOTARY(CordaX500Name.parse("O=Notary Service,L=Zurich,C=CH"), null);
    }

    fun main(args: Array<String>) {
        var registry: Party?
        var debtor: Party?
        var notary: Party?
        val filerClient = CordaRPCClient(NetworkHostAndPort.parse("localhost:${Role.FILER.rpcPort}"))
        val receivable = filerClient.start("demo", "demo").use { connection ->
            val rpc = connection.proxy
            rpc.waitUntilNetworkReady().getOrThrow()
            val value = Amount(1000, GBP)

            // Resolve node identities
            registry = rpc.wellKnownPartyFromX500Name(Role.REGISTRY.legalName)!!
            debtor = rpc.wellKnownPartyFromX500Name(Role.DEBTOR.legalName)!!
            // TODO: This should be name, not just grabbing the first notary
            notary = rpc.notaryIdentities().first()

            val ref = "Test receivable ref"
            val created = Instant.now().atZone(ZoneId.of("UTC"))
            val flow = rpc.startFlow(::IssueReceivableFlow, registry!!, ref, created, debtor!!, value, notary!!)
            flow.returnValue.getOrThrow()
        }
        println("Successfully issued receivable ${receivable.linearId}")
        require(Role.REGISTRY.legalName !in receivable.participants.map(AbstractParty::nameOrNull))
        require(Role.REGISTRY.legalName in receivable.observers.map(AbstractParty::nameOrNull))
        // The registry should now have a copy of this state
        val registryClient = CordaRPCClient(NetworkHostAndPort.parse("localhost:${Role.REGISTRY.rpcPort}"))
        filerClient.start("demo", "demo").use { connection ->
            val rpc = connection.proxy
            rpc.waitUntilNetworkReady().getOrThrow()
            val criteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(receivable.linearId))
            val results = rpc.vaultQueryByCriteria<Receivable>(criteria, Receivable::class.java)
            val actual = results.states.single().state.data
            require(receivable == actual) { "Vault state must match transaction state" }
            println("Verified registry has as copy of receivable ${receivable.linearId}")
        }
    }

    fun printHelp(parser: OptionParser) {
        println("""
        Usage: observer-demo
        Please refer to the documentation in docs/build/index.html for more info.

        """.trimIndent())
        parser.printHelpOn(System.out)
    }
}