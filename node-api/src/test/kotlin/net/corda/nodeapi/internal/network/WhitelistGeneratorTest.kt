package net.corda.nodeapi.internal.network

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import net.corda.core.crypto.SecureHash
import net.corda.nodeapi.internal.ContractsJar
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhitelistGeneratorTest {

    @Test
    fun `whitelist generator builds the correct whitelist map`() {
        // given
        val jars = (0..9).map {
            val index = it
            mock<ContractsJar> {
                val secureHash = SecureHash.randomSHA256()
                on { scan() }.then {
                    listOf(index.toString())
                }
                on { hash }.then {
                    secureHash
                }
            }
        }

        // when
        val result = generateWhitelist(null, emptyList(), jars)

        // then
        jars.forEachIndexed { index, item ->
            verify(item).scan()
            val attachmentIds = requireNotNull(result[index.toString()])
            assertEquals(1, attachmentIds.size)
            assertTrue { attachmentIds.contains(item.hash) }
        }
    }

}