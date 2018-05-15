/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.upgrade

import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UpgradedContract
import net.corda.core.node.services.ContractUpgradeService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import net.corda.node.utilities.PersistentMap
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

class ContractUpgradeServiceImpl : ContractUpgradeService, SingletonSerializeAsToken() {

    @Entity
    @Table(name = "${NODE_DATABASE_PREFIX}contract_upgrades")
    class DBContractUpgrade(
            @Id
            @Column(name = "state_ref", length = 96)
            var stateRef: String = "",

            /** refers to the UpgradedContract class name*/
            @Column(name = "contract_class_name")
            var upgradedContractClassName: String = ""
    ) : Serializable

    private companion object {
        fun createContractUpgradesMap(): PersistentMap<String, String, DBContractUpgrade, String> {
            return PersistentMap(
                    toPersistentEntityKey = { it },
                    fromPersistentEntity = { Pair(it.stateRef, it.upgradedContractClassName) },
                    toPersistentEntity = { key: String, value: String ->
                        DBContractUpgrade().apply {
                            stateRef = key
                            upgradedContractClassName = value
                        }
                    },
                    persistentEntityClass = DBContractUpgrade::class.java
            )
        }
    }

    private val authorisedUpgrade = createContractUpgradesMap()

    override fun getAuthorisedContractUpgrade(ref: StateRef) = authorisedUpgrade[ref.toString()]

    override fun storeAuthorisedContractUpgrade(ref: StateRef, upgradedContractClass: Class<out UpgradedContract<*, *>>) {
        authorisedUpgrade[ref.toString()] = upgradedContractClass.name
    }

    override fun removeAuthorisedContractUpgrade(ref: StateRef) {
        authorisedUpgrade.remove(ref.toString())
    }
}
