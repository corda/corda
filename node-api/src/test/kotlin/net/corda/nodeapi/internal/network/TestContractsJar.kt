package net.corda.nodeapi.internal.network

import net.corda.core.contracts.ContractClassName
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.randomHash
import net.corda.nodeapi.internal.ContractsJar

data class TestContractsJar(override val hash: SecureHash = DigestService.default.randomHash(),
                            private val contractClassNames: List<ContractClassName>) : ContractsJar {
    override fun scan(): List<ContractClassName> = contractClassNames
}
