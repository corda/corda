package net.corda.blobinspector

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.testing.common.internal.checkNotOnClasspath
import org.apache.commons.io.output.WriterOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.PrintStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets.UTF_8

class BlobInspectorTest {
    private val blobInspector = BlobInspector()

    @Test
    fun `network-parameters file`() {
        val output = run("network-parameters")
        assertThat(output)
                .startsWith(SignedDataWithCert::class.java.name)
                .contains(NetworkParameters::class.java.name)
                .contains(CordaX500Name("Notary Service", "Zurich", "CH").toString()) // Name of the notary in the network parameters
    }

    @Test
    fun `node-info file`() {
        checkNotOnClassPath("net.corda.nodeapi.internal.SignedNodeInfo")
        val output = run("node-info")
        assertThat(output)
                .startsWith("net.corda.nodeapi.internal.SignedNodeInfo")
                .contains(CordaX500Name("BankOfCorda", "New York", "US").toString())
    }

    @Test
    fun `WireTransaction with Cash state`() {
        checkNotOnClassPath("net.corda.finance.contracts.asset.Cash\$State")
        val output = run("cash-wtx.blob")
        assertThat(output)
                .startsWith(WireTransaction::class.java.name)
                .contains("net.corda.finance.contracts.asset.Cash\$State")
    }

    @Test
    fun `SignedTransaction with Cash state taken from node db`() {
        checkNotOnClassPath("net.corda.finance.contracts.asset.Cash\$State")
        val output = run("cash-stx-db.blob")
        assertThat(output)
                .startsWith(SignedTransaction::class.java.name)
                .contains("net.corda.finance.contracts.asset.Cash\$State")
    }

    private fun run(resourceName: String): String {
        blobInspector.source = javaClass.getResource(resourceName)
        val writer = StringWriter()
        blobInspector.run(PrintStream(WriterOutputStream(writer, UTF_8)))
        val output = writer.toString()
        println(output)
        return output
    }

    private fun checkNotOnClassPath(className: String) {
        checkNotOnClasspath(className) { "The Blob Inspector does not have this as a dependency." }
    }
}
