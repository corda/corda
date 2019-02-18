package net.corda.common.configuration.parsing.internal

import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Test
import java.security.Security

class ConfigObfuscatorTest {

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    @Test
    fun `can encrypt single field in config`() {
        val secret = "thisismysecret"
        val configuration = """
        {
            address = "localhost"
            username = "user"
            password = "<encrypt{$secret}>"
        }
        """.trimIndent()

        val obfuscatedConfiguration = ConfigObfuscator.obfuscateConfiguration(configuration).content

        assertThat(configuration)
                .contains(secret)
                .containsPattern("<encrypt\\{[^\\}]+\\}>")

        assertThat(obfuscatedConfiguration)
                .doesNotContain(secret)
                .doesNotContainPattern("<encrypt\\{[^\\}]+\\}>")
                .containsPattern("<\\{[^\\}]+\\}>")
    }

    @Test
    fun `can decrypt single field in config`() {
        val secret = "thisismysecret"
        val configuration = """
        {
            address = "localhost"
            username = "user"
            password = "<encrypt{$secret}>"
        }
        """.trimIndent()

        val obfuscatedConfiguration = ConfigObfuscator.obfuscateConfiguration(configuration).content
        val deobfuscatedConfiguration = ConfigObfuscator.deobfuscateConfiguration(obfuscatedConfiguration).content

        assertThat(obfuscatedConfiguration)
                .doesNotContain(secret)
                .doesNotContainPattern("<encrypt\\{[^\\}]+\\}>")
                .containsPattern("<\\{[^\\}]+\\}>")

        assertThat(deobfuscatedConfiguration)
                .contains(secret)
                .doesNotContainPattern("<encrypt\\{[^\\}]+\\}>")
                .doesNotContainPattern("<\\{[^\\}]+\\}>")
    }

    @Test
    fun `can decrypt field in config obfuscated with an input provider`() {
        val secret = "thisismysecret"
        val overriddenSecret = "anothersecret"
        val configuration = """
        {
            address = "localhost"
            username = "user"
            password = "<encrypt{$secret}>"
        }
        """.trimIndent()

        val obfuscatedConfiguration = ConfigObfuscator.obfuscateConfiguration(configuration, null) { overriddenSecret }.content
        val deobfuscatedConfiguration = ConfigObfuscator.deobfuscateConfiguration(obfuscatedConfiguration, null).content

        assertThat(obfuscatedConfiguration)
                .doesNotContain(secret)
                .doesNotContain(overriddenSecret)
                .doesNotContainPattern("<encrypt\\{[^\\}]+\\}>")
                .containsPattern("<\\{[^\\}]+\\}>")

        assertThat(deobfuscatedConfiguration)
                .contains(overriddenSecret)
                .doesNotContain(secret)
                .doesNotContainPattern("<encrypt\\{[^\\}]+\\}>")
                .doesNotContainPattern("<\\{[^\\}]+\\}>")
    }

    @Test
    fun `can encrypt multiple fields in config`() {
        val secret = "thisismysecret"
        val dbUser = "dba"
        val dbPassword = "dbasecret"
        val configuration = """
        {
            address = "localhost"
            username = "user"
            password = "<encrypt{$secret}>"
            connectionString = "somehost?user=$dbUser&password=<encrypt{$dbPassword}>&persist=true"
        }
        """.trimIndent()

        val obfuscatedConfiguration = ConfigObfuscator.obfuscateConfiguration(configuration).content

        assertThat(configuration)
                .contains(secret)
                .contains(dbUser)
                .contains(dbPassword)
                .containsPattern("<encrypt\\{[^\\}]+\\}>")

        assertThat(obfuscatedConfiguration)
                .doesNotContain(secret)
                .doesNotContain(dbPassword)
                .doesNotContainPattern("<encrypt\\{[^\\}]+\\}>")
                .containsPattern("<\\{[^\\}]+\\}>")
                .contains("?user=")
                .contains(dbUser)
                .contains("&password=")
                .contains("&persist=true")
    }

    @Test
    fun `can decrypt multiple fields in config`() {
        val secret = "thisismysecret"
        val dbUser = "dba"
        val dbPassword = "dbasecret"
        val configuration = """
        {
            address = "localhost"
            username = "user"
            password = "<encrypt{$secret}>"
            connectionString = "somehost?user=$dbUser&password=<encrypt{$dbPassword}>&persist=true"
        }
        """.trimIndent()

        val obfuscatedConfiguration = ConfigObfuscator.obfuscateConfiguration(configuration).content
        val deobfuscatedConfiguration = ConfigObfuscator.deobfuscateConfiguration(obfuscatedConfiguration).content

        assertThat(obfuscatedConfiguration)
                .doesNotContain(secret)
                .doesNotContain(dbPassword)
                .doesNotContainPattern("<encrypt\\{[^\\}]+\\}>")
                .containsPattern("<\\{[^\\}]+\\}>")
                .contains("?user=")
                .contains(dbUser)
                .contains("&password=")
                .contains("&persist=true")

        assertThat(deobfuscatedConfiguration)
                .contains(secret)
                .contains(dbPassword)
                .doesNotContainPattern("<encrypt\\{[^\\}]+\\}>")
                .doesNotContainPattern("<\\{[^\\}]+\\}>")
                .contains("?user=")
                .contains(dbUser)
                .contains("&password=")
                .contains("&persist=true")
    }

    @Test
    fun `can detect field overlaps in config`() {
        val configuration = """
        {
            connectionString = "somehost?user=<encrypt{foo}>&password=<encrypt{bar}>&persist=true"
        }
        """.trimIndent()

        val result = ConfigObfuscator.obfuscateConfiguration(configuration)
        assertThat(result.rawFields.single())
                .contains("}>")
                .contains("<encrypt{")
    }

    @Test
    fun `can encrypt field in config with overridden hardware address`() {
        val secret = "thisismysecret"
        val configuration = """
        {
            address = "localhost"
            username = "user"
            password = "<encrypt{$secret}>"
        }
        """.trimIndent()

        val hardwareAddress = byteArrayOf(1, 2, 3, 4, 5, 6)
        val obfuscatedConfiguration = ConfigObfuscator.obfuscateConfiguration(configuration, hardwareAddress).content

        assertThat(configuration)
                .contains(secret)
                .containsPattern("<encrypt\\{[^\\}]+\\}>")

        assertThat(obfuscatedConfiguration)
                .doesNotContain(secret)
                .doesNotContainPattern("<encrypt\\{[^\\}]+\\}>")
                .containsPattern("<\\{[^\\}]+\\}>")
    }

    @Test
    fun `can decrypt field in config with overridden hardware address`() {
        val secret = "thisismysecret"
        val configuration = """
        {
            address = "localhost"
            username = "user"
            password = "<encrypt{$secret}>"
        }
        """.trimIndent()

        val hardwareAddress = byteArrayOf(1, 2, 3, 4, 5, 6)
        val obfuscatedConfiguration = ConfigObfuscator.obfuscateConfiguration(configuration, hardwareAddress).content
        val deobfuscatedConfiguration = ConfigObfuscator.deobfuscateConfiguration(obfuscatedConfiguration, hardwareAddress).content

        assertThat(obfuscatedConfiguration)
                .doesNotContain(secret)
                .doesNotContainPattern("<encrypt\\{[^\\}]+\\}>")
                .containsPattern("<\\{[^\\}]+\\}>")

        assertThat(deobfuscatedConfiguration)
                .contains(secret)
                .doesNotContainPattern("<encrypt\\{[^\\}]+\\}>")
                .doesNotContainPattern("<\\{[^\\}]+\\}>")
    }

    @Test(expected = ConfigObfuscator.DeobfuscationFailedException::class)
    fun `cannot decrypt field in config with different hardware address`() {
        val secret = "thisismysecret"
        val configuration = """
        {
            address = "localhost"
            username = "user"
            password = "<encrypt{$secret}>"
        }
        """.trimIndent()

        val hardwareAddress1 = byteArrayOf(1, 2, 3, 4, 5, 6)
        val hardwareAddress2 = byteArrayOf(6, 5, 4, 3, 2, 1)
        val obfuscatedConfiguration = ConfigObfuscator.obfuscateConfiguration(configuration, hardwareAddress1).content

        assertThat(obfuscatedConfiguration)
                .doesNotContain(secret)
                .doesNotContainPattern("<encrypt\\{[^\\}]+\\}>")
                .containsPattern("<\\{[^\\}]+\\}>")

        ConfigObfuscator.deobfuscateConfiguration(obfuscatedConfiguration, hardwareAddress2)
    }

    @Test
    fun `can decrypt field in config with the same seed`() {
        val secret = "thisismysecret"
        val configuration = """
        {
            address = "localhost"
            username = "user"
            password = "<encrypt{$secret}>"
        }
        """.trimIndent()

        val hardwareAddress = byteArrayOf(1, 2, 3, 4, 5, 6)
        val seed = byteArrayOf(1, 2, 3, 4)
        val obfuscatedConfiguration = ConfigObfuscator.obfuscateConfiguration(configuration, hardwareAddress, seed).content

        assertThat(obfuscatedConfiguration)
                .doesNotContain(secret)
                .doesNotContainPattern("<encrypt\\{[^\\}]+\\}>")
                .containsPattern("<\\{[^\\}]+\\}>")

        ConfigObfuscator.deobfuscateConfiguration(obfuscatedConfiguration, hardwareAddress, seed)
    }


    @Test(expected = ConfigObfuscator.DeobfuscationFailedException::class)
    fun `cannot decrypt field in config with different seed`() {
        val secret = "thisismysecret"
        val configuration = """
        {
            address = "localhost"
            username = "user"
            password = "<encrypt{$secret}>"
        }
        """.trimIndent()

        val hardwareAddress = byteArrayOf(1, 2, 3, 4, 5, 6)
        val obfuscatedConfiguration = ConfigObfuscator.obfuscateConfiguration(configuration, hardwareAddress, byteArrayOf(1, 2, 3, 4)).content

        assertThat(obfuscatedConfiguration)
                .doesNotContain(secret)
                .doesNotContainPattern("<encrypt\\{[^\\}]+\\}>")
                .containsPattern("<\\{[^\\}]+\\}>")

        ConfigObfuscator.deobfuscateConfiguration(obfuscatedConfiguration, hardwareAddress, byteArrayOf(10, 20, 30, 40))
    }

}