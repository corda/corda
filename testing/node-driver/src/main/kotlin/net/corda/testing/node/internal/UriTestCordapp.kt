package net.corda.testing.node.internal

import net.corda.core.internal.copyTo
import net.corda.core.utilities.Try
import net.corda.core.utilities.Try.Failure
import net.corda.core.utilities.Try.Success
import net.corda.testing.node.TestCordapp
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.toPath

data class UriTestCordapp(val uri: URI,
                          override val config: Map<String, Any> = emptyMap(),
                          val signed: Boolean = false) : TestCordappInternal() {
    override fun withConfig(config: Map<String, Any>): TestCordapp = copy(config = config)

    override fun asSigned(): TestCordapp = copy(signed = true)

    override fun withOnlyJarContents(): TestCordappInternal = copy(config = emptyMap(), signed = false)

    override val jarFile: Path by lazy {
        val toPathAttempt = Try.on(uri::toPath)
        when (toPathAttempt) {
            is Success -> if (signed) TestCordappSigner.signJarCopy(toPathAttempt.value) else toPathAttempt.value
            is Failure -> {
                // URI is not a local path, so we copy it to a temp file and use that.
                val downloaded = Files.createTempFile("test-cordapp-${uri.path.substringAfterLast("/").substringBeforeLast(".jar")}", ".jar")
                downloaded.toFile().deleteOnExit()
                uri.toURL().openStream().use { it.copyTo(downloaded, REPLACE_EXISTING) }
                if (signed) {
                    TestCordappSigner.signJar(downloaded)
                }
                downloaded
            }
        }
    }
}
