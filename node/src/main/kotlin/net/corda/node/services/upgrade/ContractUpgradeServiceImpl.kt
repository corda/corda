package net.corda.node.services.upgrade

import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UpgradedContract
import net.corda.core.node.services.ContractUpgradeService
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.node.utilities.NODE_DATABASE_PREFIX
import net.corda.node.utilities.PersistentMap
import javax.persistence.*

class ContractUpgradeServiceImpl : ContractUpgradeService {

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}contract_upgrades")
    class DBContractUpgrade(
            @Id
            @Column(name = "state_ref", length = 96)
            var stateRef: String = "",

            /** refers to serialized UpgradedContract */
            @Lob
            @Column(nullable = true)
            var upgradedContract: ByteArray? = ByteArray(0)
    )

    private companion object {
        fun createContractUpgradesMap(): PersistentMap<String, Class<out UpgradedContract<*, *>>?, DBContractUpgrade, String> {
            return PersistentMap(
                    toPersistentEntityKey = { it },
                    fromPersistentEntity = { Pair(it.stateRef,
                            it.upgradedContract?.deserialize( context = SerializationDefaults.STORAGE_CONTEXT)) },
                    toPersistentEntity = { key: String, value: Class<out UpgradedContract<*, *>>? ->
                        DBContractUpgrade().apply {
                            stateRef = key
                            upgradedContract = value?.let {  serialize(context = SerializationDefaults.STORAGE_CONTEXT).bytes }
                        }
                    },
                    persistentEntityClass = DBContractUpgrade::class.java
            )
        }
    }

    private val authorisedUpgrade = createContractUpgradesMap()

    override fun getAuthorisedContractUpgrade(ref: StateRef) = authorisedUpgrade[ref.toString()]

    override fun storeAuthorisedContractUpgrade(ref: StateRef, upgradedContractClass: Class<out UpgradedContract<*, *>>) {
        authorisedUpgrade.put(ref.toString(), upgradedContractClass)
    }

    override fun removeAuthorisedContractUpgrade(ref: StateRef) {
        authorisedUpgrade.remove(ref.toString())
    }
}