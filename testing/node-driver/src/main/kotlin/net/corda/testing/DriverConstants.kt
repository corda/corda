@file:JvmName("DriverConstants")

package net.corda.testing

import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.CordaRPCOps
import net.corda.nodeapi.User
import net.corda.testing.driver.DriverDSLExposedInterface

//
// Extensions to the Driver DSL to auto-manufacture nodes by name.
//

/**
 * A simple wrapper for objects provided by the integration test driver DSL. The fields are lazy so
 * node construction won't start until you access the members. You can get one of these from the
 * [alice], [bob] and [aliceBobAndNotary] functions.
 */
class PredefinedTestNode internal constructor(party: Party, driver: DriverDSLExposedInterface, ifNotaryIsValidating: Boolean?) {
    val rpcUsers = listOf(User("admin", "admin", setOf("ALL")))  // TODO: Randomize?
    val nodeFuture by lazy {
        if (ifNotaryIsValidating != null) {
            driver.startNotaryNode(providedName = party.name, rpcUsers = rpcUsers, validating = ifNotaryIsValidating)
        } else {
            driver.startNode(providedName = party.name, rpcUsers = rpcUsers)
        }
    }
    val node by lazy { nodeFuture.get()!! }
    val rpc by lazy { node.rpcClientToNode() }

    fun <R> useRPC(block: (CordaRPCOps) -> R) = rpc.use(rpcUsers[0].username, rpcUsers[0].password) { block(it.proxy) }
}

// TODO: Probably we should inject the above keys through the driver to make the nodes use it, rather than have the warnings below.

/**
 * Returns a plain, entirely stock node pre-configured with the [ALICE] identity. Note that a random key will be generated
 * for it: you won't have [ALICE_KEY].
 */
fun DriverDSLExposedInterface.alice(): PredefinedTestNode = PredefinedTestNode(ALICE, this, null)

/**
 * Returns a plain, entirely stock node pre-configured with the [BOB] identity. Note that a random key will be generated
 * for it: you won't have [BOB_KEY].
 */
fun DriverDSLExposedInterface.bob(): PredefinedTestNode = PredefinedTestNode(BOB, this, null)

/**
 * Returns a plain single node notary pre-configured with the [DUMMY_NOTARY] identity. Note that a random key will be generated
 * for it: you won't have [DUMMY_NOTARY_KEY].
 */
fun DriverDSLExposedInterface.notary(): PredefinedTestNode = PredefinedTestNode(DUMMY_NOTARY, this, true)

/**
 * Returns plain, entirely stock nodes pre-configured with the [ALICE], [BOB] and [DUMMY_NOTARY] X.500 names in that
 * order. They have been started up in parallel and are now ready to use.
 */
fun DriverDSLExposedInterface.aliceBobAndNotary(): List<PredefinedTestNode> {
    val alice = alice()
    val bob = bob()
    val notary = notary()
    listOf(alice.nodeFuture, bob.nodeFuture, notary.nodeFuture).transpose().get()
    return listOf(alice, bob, notary)
}
