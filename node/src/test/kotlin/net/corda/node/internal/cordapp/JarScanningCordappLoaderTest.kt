package net.corda.node.internal.cordapp

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.cordapp.Cordapp
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SchedulableFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.packageName_
import net.corda.core.internal.toPath
import net.corda.coretesting.internal.delete
import net.corda.coretesting.internal.modifyJarManifest
import net.corda.finance.contracts.CommercialPaper
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.internal.ConfigHolder
import net.corda.finance.schemas.CashSchemaV1
import net.corda.finance.schemas.CommercialPaperSchemaV1
import net.corda.node.VersionInfo
import net.corda.nodeapi.internal.DEV_PUB_KEY_HASHES
import net.corda.serialization.internal.DefaultWhitelist
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.internal.ContractJarTestUtils.makeTestContractJar
import net.corda.testing.core.internal.JarSignatureTestUtils.generateKey
import net.corda.testing.core.internal.JarSignatureTestUtils.getJarSigners
import net.corda.testing.core.internal.JarSignatureTestUtils.signJar
import net.corda.testing.core.internal.JarSignatureTestUtils.unsignJar
import net.corda.testing.internal.LogHelper
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.Manifest
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.name
import kotlin.test.assertFailsWith

@InitiatingFlow
class DummyFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = Unit
}

@InitiatedBy(DummyFlow::class)
class LoaderTestFlow(@Suppress("UNUSED_PARAMETER") unusedSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = Unit
}

@SchedulableFlow
class DummySchedulableFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = Unit
}

@StartableByRPC
class DummyRPCFlow : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = Unit
}

class JarScanningCordappLoaderTest {
    private companion object {
        val legacyFinanceContractsJar = this::class.java.getResource("/corda-finance-contracts-4.11.jar")!!.toPath()
        val currentFinanceContractsJar = this::class.java.getResource("/corda-finance-contracts.jar")!!.toPath()
        val currentFinanceWorkflowsJar = this::class.java.getResource("/corda-finance-workflows.jar")!!.toPath()

        init {
            LogHelper.setLevel(JarScanningCordappLoaderTest::class)
        }
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test(timeout=300_000)
	fun `classes that aren't in cordapps aren't loaded`() {
        // Basedir will not be a corda node directory so the dummy flow shouldn't be recognised as a part of a cordapp
        val loader = JarScanningCordappLoader.fromDirectories(listOf(Paths.get(".")))
        assertThat(loader.cordapps).isEmpty()
    }

    @Test(timeout=300_000)
    fun `constructed CordappImpls contains the right classes`() {
        val loader = JarScanningCordappLoader(setOf(currentFinanceContractsJar, currentFinanceWorkflowsJar))
        val (contractsCordapp, workflowsCordapp) = loader.cordapps

        assertThat(contractsCordapp.contractClassNames).contains(Cash::class.java.name, CommercialPaper::class.java.name)
        assertThat(contractsCordapp.customSchemas).contains(CashSchemaV1, CommercialPaperSchemaV1)
        assertThat(contractsCordapp.info).isInstanceOf(Cordapp.Info.Contract::class.java)
        assertThat(contractsCordapp.allFlows).isEmpty()
        assertThat(contractsCordapp.jarFile).isEqualTo(currentFinanceContractsJar)

        assertThat(workflowsCordapp.allFlows).contains(CashIssueFlow::class.java, CashPaymentFlow::class.java)
        assertThat(workflowsCordapp.services).contains(ConfigHolder::class.java)
        assertThat(workflowsCordapp.info).isInstanceOf(Cordapp.Info.Workflow::class.java)
        assertThat(workflowsCordapp.contractClassNames).isEmpty()
        assertThat(workflowsCordapp.jarFile).isEqualTo(currentFinanceWorkflowsJar)

        for (actualCordapp in loader.cordapps) {
            assertThat(actualCordapp.cordappClasses)
                    .containsAll(actualCordapp.contractClassNames)
                    .containsAll(actualCordapp.initiatedFlows.map { it.name })
                    .containsAll(actualCordapp.rpcFlows.map { it.name })
                    .containsAll(actualCordapp.serviceFlows.map { it.name })
                    .containsAll(actualCordapp.schedulableFlows.map { it.name })
                    .containsAll(actualCordapp.services.map { it.name })
                    .containsAll(actualCordapp.telemetryComponents.map { it.name })
                    .containsAll(actualCordapp.serializationCustomSerializers.map { it.javaClass.name })
                    .containsAll(actualCordapp.checkpointCustomSerializers.map { it.javaClass.name })
                    .containsAll(actualCordapp.customSchemas.map { it.name })
            assertThat(actualCordapp.serializationWhitelists).contains(DefaultWhitelist)
        }
    }

    @Test(timeout=300_000)
    fun `flows are loaded by loader`() {
        val jarFile = cordappWithPackages(javaClass.packageName_).jarFile
        val loader = JarScanningCordappLoader(setOf(jarFile))

        // One cordapp from this source tree. In gradle it will also pick up the node jar.
        assertThat(loader.cordapps).isNotEmpty

        val actualCordapp = loader.cordapps.single { it.initiatedFlows.isNotEmpty() }
        assertThat(actualCordapp.initiatedFlows.first()).hasSameClassAs(DummyFlow::class.java)
        assertThat(actualCordapp.rpcFlows).first().hasSameClassAs(DummyRPCFlow::class.java)
        assertThat(actualCordapp.schedulableFlows).first().hasSameClassAs(DummySchedulableFlow::class.java)
    }

    // This test exists because the appClassLoader is used by serialisation and we need to ensure it is the classloader
    // being used internally. Later iterations will use a classloader per cordapp and this test can be retired.
    @Test(timeout=300_000)
    fun `cordapp classloader can load cordapp classes`() {
        val testJar = this::class.java.getResource("/testing-cashobservers-cordapp.jar")!!.toPath()
        val loader = JarScanningCordappLoader(setOf(testJar))

        loader.appClassLoader.loadClass("net.corda.finance.test.flows.CashIssueWithObserversFlow")
    }

    @Test(timeout=300_000)
    fun `sets target and min version to 1 if not specified`() {
        val loader = JarScanningCordappLoader(setOf(minAndTargetCordapp(minVersion = null, targetVersion = null)))
        loader.cordapps.forEach {
            assertThat(it.targetPlatformVersion).isEqualTo(1)
            assertThat(it.minimumPlatformVersion).isEqualTo(1)
        }
    }

    @Test(timeout=300_000)
    fun `returns correct values for minPlatformVersion and targetVersion`() {
        val loader = JarScanningCordappLoader(setOf(minAndTargetCordapp(minVersion = 2, targetVersion = 3)))
        val cordapp = loader.cordapps.first()
        assertThat(cordapp.targetPlatformVersion).isEqualTo(3)
        assertThat(cordapp.minimumPlatformVersion).isEqualTo(2)
    }

    @Test(timeout=300_000)
    fun `sets target version to min version if target version is not specified`() {
        val loader = JarScanningCordappLoader(setOf(minAndTargetCordapp(minVersion = 2, targetVersion = null)))
        // exclude the core cordapp
        val cordapp = loader.cordapps.first()
        assertThat(cordapp.targetPlatformVersion).isEqualTo(2)
        assertThat(cordapp.minimumPlatformVersion).isEqualTo(2)
    }

    @Test(timeout = 300_000)
	fun `does not load apps when their min platform version is greater than the node platform version`() {
        val jar = minAndTargetCordapp(minVersion = 2, targetVersion = null)
        val cordappLoader = JarScanningCordappLoader(setOf(jar), versionInfo = VersionInfo.UNKNOWN.copy(platformVersion = 1))
        assertThatExceptionOfType(InvalidCordappException::class.java).isThrownBy {
            cordappLoader.cordapps
        }
    }

    @Test(timeout=300_000)
    fun `does load apps when their min platform version is less than the platform version`() {
        val jar = minAndTargetCordapp(minVersion = 2, targetVersion = 3)
        val loader = JarScanningCordappLoader(setOf(jar), versionInfo = VersionInfo.UNKNOWN.copy(platformVersion = 1000))
        assertThat(loader.cordapps).hasSize(1)
    }

    @Test(timeout=300_000)
    fun `does load apps when their min platform version is equal to the platform version`() {
        val jar = minAndTargetCordapp(minVersion = 2, targetVersion = 3)
        val loader = JarScanningCordappLoader(setOf(jar), versionInfo = VersionInfo.UNKNOWN.copy(platformVersion = 2))
        assertThat(loader.cordapps).hasSize(1)
    }

    @Test(timeout=300_000)
    fun `loads app signed by allowed certificate`() {
        val loader = JarScanningCordappLoader(setOf(currentFinanceContractsJar), signerKeyFingerprintBlacklist = emptyList())
        assertThat(loader.cordapps).hasSize(1)
    }

    @Test(timeout = 300_000)
	fun `does not load app signed by blacklisted certificate`() {
        val cordappLoader = JarScanningCordappLoader(setOf(currentFinanceContractsJar), signerKeyFingerprintBlacklist = DEV_PUB_KEY_HASHES)
        assertThatExceptionOfType(InvalidCordappException::class.java).isThrownBy {
            cordappLoader.cordapps
        }
    }

    @Test(timeout=300_000)
    fun `does not load legacy contract CorDapp signed by blacklisted certificate`() {
        val unsignedJar = currentFinanceContractsJar.duplicate { unsignJar() }
        val loader = JarScanningCordappLoader(setOf(unsignedJar), setOf(legacyFinanceContractsJar), signerKeyFingerprintBlacklist = DEV_PUB_KEY_HASHES)
        assertThatExceptionOfType(InvalidCordappException::class.java)
                .isThrownBy { loader.cordapps }
                .withMessageContaining("Corresponding contracts are signed by blacklisted key(s)")
                .withMessageContaining(legacyFinanceContractsJar.name)
    }

    @Test(timeout=300_000)
    fun `does not load duplicate CorDapps`() {
        val duplicateJar = currentFinanceWorkflowsJar.duplicate()
        val loader = JarScanningCordappLoader(setOf(currentFinanceWorkflowsJar, duplicateJar))
        assertFailsWith<DuplicateCordappsInstalledException> {
            loader.cordapps
        }
    }

    @Test(timeout=300_000)
    fun `does not load contract shared across CorDapps`() {
        val cordappJars = (1..2).map {
            makeTestContractJar(
                    tempFolder.root.toPath(),
                    listOf("com.example.MyContract", "com.example.AnotherContractFor$it"),
                    generateManifest = false,
                    jarFileName = "sample$it.jar"
            )
        }.toSet()
        val loader = JarScanningCordappLoader(cordappJars)
        assertThatIllegalStateException()
                .isThrownBy { loader.cordapps }
                .withMessageContaining("Contract com.example.MyContract occuring in multiple CorDapps")
    }

    @Test(timeout=300_000)
    fun `loads app signed by both allowed and non-blacklisted certificate`() {
        val jar = currentFinanceWorkflowsJar.duplicate {
            tempFolder.root.toPath().generateKey("testAlias", "testPassword", ALICE_NAME.toString())
            tempFolder.root.toPath().signJar(absolutePathString(), "testAlias", "testPassword")
        }
        assertThat(jar.parent.getJarSigners(jar.name)).hasSize(2)
        val loader = JarScanningCordappLoader(setOf(jar), signerKeyFingerprintBlacklist = DEV_PUB_KEY_HASHES)
        assertThat(loader.cordapps).hasSize(1)
    }

    @Test(timeout=300_000)
    fun `loads both legacy and current versions of the same contracts CorDapp`() {
        val loader = JarScanningCordappLoader(setOf(currentFinanceContractsJar), setOf(legacyFinanceContractsJar))
        assertThat(loader.cordapps).hasSize(1)  // Legacy contract CorDapps are not part of the main list
        assertThat(loader.legacyContractCordapps).hasSize(1)
        assertThat(loader.legacyContractCordapps.single().jarFile).isEqualTo(legacyFinanceContractsJar)
    }

    @Test(timeout=300_000)
    fun `exception raised if legacy and non legacy version of same contract signed by differet keys`() {
        val jar = currentFinanceContractsJar.duplicate {
            tempFolder.root.toPath().generateKey("testAlias", "testPassword", ALICE_NAME.toString())
            tempFolder.root.toPath().signJar(absolutePathString(), "testAlias", "testPassword")
        }
        assertThatIllegalStateException()
                .isThrownBy { JarScanningCordappLoader(setOf(jar), setOf(legacyFinanceContractsJar)).cordapps }
                .withMessageContaining("signers do not match legacy contract CorDapp")
    }

    @Test(timeout=300_000)
    fun `loads legacy and non legacy version of same contract both signed by 2 keys`() {
        val jar = currentFinanceContractsJar.duplicate {
            tempFolder.root.toPath().generateKey("testAlias", "testPassword", ALICE_NAME.toString())
            tempFolder.root.toPath().signJar(absolutePathString(), "testAlias", "testPassword")
        }
        val legacyJar = legacyFinanceContractsJar.duplicate(name = "duplicate2.jar") {
            tempFolder.root.toPath().signJar(absolutePathString(), "testAlias", "testPassword")
        }
        val loader = JarScanningCordappLoader(setOf(jar), setOf(legacyJar))
        assertThat(jar.parent.getJarSigners(jar.name)).hasSize(2)
        assertThat(legacyJar.parent.getJarSigners(legacyJar.name)).hasSize(2)
        assertThat(loader.cordapps).hasSize(1)
        assertThat(loader.legacyContractCordapps).hasSize(1)
    }

    @Test(timeout=300_000)
    fun `does not load legacy contracts CorDapp without the corresponding current version`() {
        val loader = JarScanningCordappLoader(setOf(currentFinanceWorkflowsJar), setOf(legacyFinanceContractsJar))
        assertThatIllegalStateException()
                .isThrownBy { loader.legacyContractCordapps }
                .withMessageContaining("does not have a corresponding newer version (4.12 or later). Please add this corresponding CorDapp or remove the legacy one.")
    }

    @Test(timeout=300_000)
    fun `checks if legacy contract CorDapp is actually legacy`() {
        val loader = JarScanningCordappLoader(setOf(currentFinanceContractsJar), setOf(currentFinanceContractsJar))
        assertThatIllegalStateException()
                .isThrownBy { loader.legacyContractCordapps }
                .withMessageContaining("${currentFinanceContractsJar.name} is not legacy; please remove or place it in the node's CorDapps directory.")
    }

    @Test(timeout=300_000)
    fun `does not load if legacy CorDapp present in general list`() {
        val loader = JarScanningCordappLoader(setOf(legacyFinanceContractsJar))
        assertThatIllegalStateException()
                .isThrownBy { loader.cordapps }
                .withMessageContaining("${legacyFinanceContractsJar.name} is legacy contracts; please place it in the node's 'legacy-contracts' directory.")
    }

    private inline fun Path.duplicate(name: String = "duplicate.jar", modify: Path.() -> Unit = { }): Path {
        val copy = tempFolder.newFile(name).toPath()
        copyTo(copy, overwrite = true)
        modify(copy)
        return copy
    }

    private fun minAndTargetCordapp(minVersion: Int?, targetVersion: Int?): Path {
        return currentFinanceWorkflowsJar.duplicate {
            modifyJarManifest { manifest ->
                manifest.setOrDeleteAttribute("Min-Platform-Version", minVersion?.toString())
                manifest.setOrDeleteAttribute("Target-Platform-Version", targetVersion?.toString())
            }
        }
    }

    private fun Manifest.setOrDeleteAttribute(name: String, value: String?) {
        if (value != null) {
            mainAttributes.putValue(name, value.toString())
        } else {
            mainAttributes.delete(name)
        }
    }
}
