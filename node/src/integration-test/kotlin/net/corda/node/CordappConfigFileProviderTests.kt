package net.corda.node

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.node.internal.cordapp.CordappConfigFileProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CordappConfigFileProviderTests {
    private companion object {
        val cordappConfDir = File("build/tmp/cordapps/config")
        val cordappName = "test"
    }

    @Test
    fun `test that config can be loaded`() {
        val provider = CordappConfigFileProvider(cordappConfDir)
        val config = ConfigFactory.parseString("a=b")
        assertThat(config.getString("a")).isEqualTo("b")
        Files.write(File(cordappConfDir, cordappName + ".conf").toPath(), config.root().render(ConfigRenderOptions.concise()).toByteArray())

        assertThat(provider.getConfigByName(cordappName).getString("a")).isEqualTo("b")
    }
}