package net.corda.node.internal.cordapp

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.hash
import net.corda.core.internal.toPath
import net.corda.core.node.services.AttachmentId
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.node.services.persistence.AttachmentStorageInternal
import net.corda.node.services.persistence.toInternal
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.internal.JarSignatureTestUtils.unsignJar
import net.corda.testing.internal.MockCordappConfigProvider
import net.corda.testing.services.MockAttachmentStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.jar.JarOutputStream
import java.util.zip.Deflater.NO_COMPRESSION
import java.util.zip.ZipEntry
import java.util.zip.ZipEntry.DEFLATED
import java.util.zip.ZipEntry.STORED
import kotlin.io.path.copyTo
import kotlin.test.assertFailsWith

class CordappProviderImplTests {
    private companion object {
        val financeContractsJar = this::class.java.getResource("/corda-finance-contracts.jar")!!.toPath()
        val financeWorkflowsJar = this::class.java.getResource("/corda-finance-workflows.jar")!!.toPath()

        @JvmField
        val ID1 = AttachmentId.randomSHA256()
        @JvmField
        val ID2 = AttachmentId.randomSHA256()
        @JvmField
        val ID3 = AttachmentId.randomSHA256()
        @JvmField
        val ID4 = AttachmentId.randomSHA256()

        val stubConfigProvider = object : CordappConfigProvider {
            override fun getConfigByName(name: String): Config = ConfigFactory.empty()
        }

        fun directoryEntry(internalName: String) = ZipEntry("$internalName/").apply {
            method = STORED
            compressedSize = 0
            size = 0
            crc = 0
        }

        fun fileEntry(internalName: String) = ZipEntry(internalName).apply {
            method = DEFLATED
        }
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var attachmentStore: AttachmentStorageInternal

    @Before
    fun setup() {
        attachmentStore = MockAttachmentStorage().toInternal()
    }

    @Test(timeout=300_000)
	fun `empty jar is not loaded into the attachment store`() {
        val provider = newCordappProvider(setOf(Companion::class.java.getResource("empty.jar")!!.toPath()))
        assertThat(attachmentStore.openAttachment(provider.cordapps.single().jarHash)).isNull()
    }

    @Test(timeout=300_000)
	fun `test that we find a cordapp class that is loaded into the store`() {
        val provider = newCordappProvider(setOf(financeContractsJar))

        val expected = provider.cordapps.first()
        val actual = provider.getCordappForClass(Cash::class.java.name)

        assertNotNull(actual)
        assertEquals(expected, actual)
    }

    @Test(timeout=300_000)
	fun `test that we find an attachment for a cordapp contract class`() {
        val provider = newCordappProvider(setOf(financeContractsJar))
        val expected = provider.getAppContext(provider.cordapps.first()).attachmentId
        val actual = provider.getContractAttachmentID(Cash::class.java.name)

        assertNotNull(actual)
        assertEquals(actual!!, expected)
    }

    @Test(timeout=300_000)
    fun `test cordapp configuration`() {
        val configProvider = MockCordappConfigProvider()
        configProvider.cordappConfigs["corda-finance-contracts"] = ConfigFactory.parseString("key=value")
        val provider = newCordappProvider(setOf(financeContractsJar), cordappConfigProvider = configProvider)

        val expected = provider.getAppContext(provider.cordapps.first()).config

        assertThat(expected.getString("key")).isEqualTo("value")
    }

    @Test(timeout=300_000)
    fun getCordappForFlow() {
        val provider = newCordappProvider(setOf(financeWorkflowsJar))
        val cashIssueFlow = CashIssueFlow(10.DOLLARS, OpaqueBytes.of(0x00), TestIdentity(ALICE_NAME).party)
        assertThat(provider.getCordappForFlow(cashIssueFlow)?.jarPath?.toPath()).isEqualTo(financeWorkflowsJar)
    }

    @Test(timeout=300_000)
    fun `does not load the same flow across different CorDapps`() {
        val unsignedJar = tempFolder.newFile("duplicate.jar").toPath()
        financeWorkflowsJar.copyTo(unsignedJar, overwrite = true)
        // We just need to change the file's hash and thus avoid the duplicate CorDapp check
        unsignedJar.unsignJar()
        assertThat(unsignedJar.hash).isNotEqualTo(financeWorkflowsJar.hash)
        assertFailsWith<MultipleCordappsForFlowException> {
            newCordappProvider(setOf(financeWorkflowsJar, unsignedJar))
        }
    }

    @Test(timeout=300_000)
	fun `test fixup rule that adds attachment`() {
        val fixupJar = File.createTempFile("fixup", ".jar")
            .writeFixupRules("$ID1 => $ID2, $ID3")
        val fixedIDs = with(newCordappProvider(setOf(fixupJar.toPath()))) {
            attachmentFixups.fixupAttachmentIds(listOf(ID1))
        }
        assertThat(fixedIDs).containsExactly(ID2, ID3)
    }

    @Test(timeout=300_000)
	fun `test fixup rule that deletes attachment`() {
        val fixupJar = File.createTempFile("fixup", ".jar")
            .writeFixupRules("$ID1 =>")
        val fixedIDs = with(newCordappProvider(setOf(fixupJar.toPath()))) {
            attachmentFixups.fixupAttachmentIds(listOf(ID1))
        }
        assertThat(fixedIDs).isEmpty()
    }

    @Test(timeout=300_000)
	fun `test fixup rule with blank LHS`() {
        val fixupJar = File.createTempFile("fixup", ".jar")
            .writeFixupRules(" => $ID2")
        val ex = assertFailsWith<IllegalArgumentException> {
            newCordappProvider(setOf(fixupJar.toPath()))
        }
        assertThat(ex).hasMessageContaining(
            "Forbidden empty list of source attachment IDs in '${fixupJar.absolutePath}'"
        )
    }

    @Test(timeout=300_000)
	fun `test fixup rule without arrows`() {
        val rule = " $ID1 "
        val fixupJar = File.createTempFile("fixup", ".jar")
            .writeFixupRules(rule)
        val ex = assertFailsWith<IllegalArgumentException> {
            newCordappProvider(setOf(fixupJar.toPath()))
        }
        assertThat(ex).hasMessageContaining(
            "Invalid fix-up line '${rule.trim()}' in '${fixupJar.absolutePath}'"
        )
    }

    @Test(timeout=300_000)
	fun `test fixup rule with too many arrows`() {
        val rule = " $ID1 => $ID2 => $ID3 "
        val fixupJar = File.createTempFile("fixup", ".jar")
            .writeFixupRules(rule)
        val ex = assertFailsWith<IllegalArgumentException> {
            newCordappProvider(setOf(fixupJar.toPath()))
        }
        assertThat(ex).hasMessageContaining(
            "Invalid fix-up line '${rule.trim()}' in '${fixupJar.absolutePath}'"
        )
    }

    @Test(timeout=300_000)
	fun `test fixup file containing multiple rules and comments`() {
        val fixupJar = File.createTempFile("fixup", ".jar").writeFixupRules(
            "# Whole line comment",
            "\t$ID1,$ID2 =>  $ID2,,  $ID3 # EOl comment",
            "   # Empty line with comment",
            "",
            "$ID3 => $ID4"
        )
        val fixedIDs = with(newCordappProvider(setOf(fixupJar.toPath()))) {
            attachmentFixups.fixupAttachmentIds(listOf(ID2, ID1))
        }
        assertThat(fixedIDs).containsExactlyInAnyOrder(ID2, ID4)
    }

    private fun File.writeFixupRules(vararg lines: String): File {
        JarOutputStream(FileOutputStream(this)).use { jar ->
            jar.setMethod(DEFLATED)
            jar.setLevel(NO_COMPRESSION)
            jar.putNextEntry(directoryEntry("META-INF"))
            jar.putNextEntry(fileEntry("META-INF/Corda-Fixups"))
            for (line in lines) {
                jar.write(line.toByteArray())
                jar.write('\r'.code)
                jar.write('\n'.code)
            }
        }
        return this
    }

    private fun newCordappProvider(cordappJars: Set<Path>, cordappConfigProvider: CordappConfigProvider = stubConfigProvider): CordappProviderImpl {
        val loader = JarScanningCordappLoader(cordappJars)
        return CordappProviderImpl(loader, cordappConfigProvider, attachmentStore).apply { start() }
    }
}
