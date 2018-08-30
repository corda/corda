/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.AbstractAttachment
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.node.cordapp.CordappLoader
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.node.internal.cordapp.JarScanningCordappLoader
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.MockCordappConfigProvider
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.internal.cordappsForPackages
import net.corda.testing.node.internal.getTimestampAsDirectoryName
import net.corda.testing.node.internal.packageInDirectory
import net.corda.testing.services.MockAttachmentStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class AttachmentsClassLoaderStaticContractTests {
    private companion object {
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val MEGA_CORP = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    class AttachmentDummyContract : Contract {
        companion object {
            private const val ATTACHMENT_PROGRAM_ID = "net.corda.nodeapi.internal.AttachmentsClassLoaderStaticContractTests\$AttachmentDummyContract"
        }

        data class State(val magicNumber: Int = 0) : ContractState {
            override val participants: List<AbstractParty>
                get() = listOf()
        }

        interface Commands : CommandData {
            class Create : TypeOnlyCommandData(), Commands
        }

        override fun verify(tx: LedgerTransaction) {
            // Always accepts.
        }

        fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party): TransactionBuilder {
            val state = State(magicNumber)
            return TransactionBuilder(notary)
                    .withItems(StateAndContract(state, ATTACHMENT_PROGRAM_ID), Command(Commands.Create(), owner.party.owningKey))
        }
    }

    private val unsignedAttachment = object : AbstractAttachment({ byteArrayOf() }) {
        override val id: SecureHash get() = throw UnsupportedOperationException()
    }

    private val attachments = rigorousMock<AttachmentStorage>().also {
            doReturn(unsignedAttachment).whenever(it).openAttachment(any())
    }

    private val serviceHub = rigorousMock<ServicesForResolution>().also {
        val cordappProviderImpl = CordappProviderImpl(cordappLoaderForPackages(listOf("net.corda.nodeapi.internal")), MockCordappConfigProvider(), MockAttachmentStorage())
        cordappProviderImpl.start(testNetworkParameters().whitelistedContractImplementations)
        doReturn(cordappProviderImpl).whenever(it).cordappProvider
        doReturn(testNetworkParameters()).whenever(it).networkParameters
        doReturn(attachments).whenever(it).attachments
    }

    @Test
    fun `test serialization of WireTransaction with statically loaded contract`() {
        val tx = AttachmentDummyContract().generateInitial(MEGA_CORP.ref(0), 42, DUMMY_NOTARY)
        val wireTransaction = tx.toWireTransaction(serviceHub)
        val bytes = wireTransaction.serialize()
        val copiedWireTransaction = bytes.deserialize()

        assertEquals(1, copiedWireTransaction.outputs.size)
        assertEquals(42, (copiedWireTransaction.outputs[0].data as AttachmentDummyContract.State).magicNumber)
    }

    @Test
    fun `verify that contract DummyContract is in classPath`() {
        val contractClass = Class.forName("net.corda.nodeapi.internal.AttachmentsClassLoaderStaticContractTests\$AttachmentDummyContract")
        val contract = contractClass.newInstance() as Contract

        assertNotNull(contract)
    }

    private fun cordappLoaderForPackages(packages: Iterable<String>): CordappLoader {

        val cordapps = cordappsForPackages(packages)
        return testDirectory().let { directory ->
            cordapps.packageInDirectory(directory)
            JarScanningCordappLoader.fromDirectories(listOf(directory))
        }
    }

    private fun testDirectory(): Path {

        return Paths.get("build", getTimestampAsDirectoryName())
    }
}