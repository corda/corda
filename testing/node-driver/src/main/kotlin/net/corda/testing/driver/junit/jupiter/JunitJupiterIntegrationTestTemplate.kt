package net.corda.testing.driver.junit.jupiter

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party

/**
 * An abstract test template for integration tests that employ
 * the Junit Jupiter Corda Driver. It ensures that the at least
 * the Junit Jupiter Corda Driver is instantiated.
 * **NOTE**: Make sure the instance of the Junit Jupiter Corda Driver
 * is already loaded in the companion object of the test class,
 * then hand over this driver instance to [cordaDriverJunitJupiter].
 * @property cordaDriverJunitJupiter The instance of the initialized
 * Junit Jupiter Corda Driver as a property of the test class instance.
 * @property partyCordaX500Names The [CordaX500Name]s of the started nodes.
 * @property partiesByCordaX500Names A map of [CordaX500Name]s as keys mapped to
 * its corresponding [Party] instance, respectively.
 */
abstract class JunitJupiterIntegrationTestTemplate {

    abstract val cordaDriverJunitJupiter: CordaDriverJunitJupiter

    val partyCordaX500Names: List<CordaX500Name> by lazy { cordaDriverJunitJupiter.cordaX500Names }

    val partiesByCordaX500Names: Map<CordaX500Name, Party> by lazy {
        cordaDriverJunitJupiter.getCordaNodeHandlesOrThrow().map {
            val party = it.nodeInfo.legalIdentities.first()
            Pair(party.name, party)
        }.associateBy({ it.first }, { it.second })
    }
}
