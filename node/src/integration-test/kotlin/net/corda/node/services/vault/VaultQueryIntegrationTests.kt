//package net.corda.node.services.vault
//
//
//import net.corda.core.identity.CordaX500Name
//import net.corda.testing.core.TestIdentity
//import net.corda.testing.internal.GlobalDatabaseRule
//import net.corda.testing.internal.toDatabaseSchemaName
//import org.junit.ClassRule
//import org.junit.Rule
//import org.junit.rules.RuleChain
//
//class VaultQueryIntegrationTests : VaultQueryTestsBase(), VaultQueryParties by vaultQueryTestRule {
//
//    companion object {
//        val MEGA_CORP = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).name
//        val databaseSchemas = listOf(Companion.MEGA_CORP.toDatabaseSchemaName())
//
//        val globalDatabaseRule = GlobalDatabaseRule(databaseSchemas)
//        val vaultQueryTestRule = VaultQueryTestRule()
//
//        @ClassRule @JvmField
//        val ruleChain = RuleChain.outerRule(globalDatabaseRule).around(vaultQueryTestRule)
//    }
//
//    @Suppress("LeakingThis")
//    @Rule
//    @JvmField
//    val transactionRule = VaultQueryRollbackRule(this)
//}
