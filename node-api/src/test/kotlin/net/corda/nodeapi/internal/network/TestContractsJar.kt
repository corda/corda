package net.corda.nodeapi.internal.network

import net.corda.core.contracts.ContractClassName
import net.corda.core.crypto.SecureHash
import net.corda.nodeapi.internal.ContractsJar

data class TestContractsJar(override val hash: SecureHash = SecureHash.randomSHA256(),
                            private val contractClassNames: List<ContractClassName>) : ContractsJar {
    override fun scan(): List<ContractClassName> = contractClassNames
}
