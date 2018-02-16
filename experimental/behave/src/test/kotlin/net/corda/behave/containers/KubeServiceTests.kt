package net.corda.behave.containers

import net.corda.behave.service.containers.KubeContainerService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class KubeServiceTests {

    @Test
    fun `kubernetes service can be started and stopped`() {
        val service = KubeContainerService("test-kubernetes", 12345)
        val didStart = service.start()
        service.stop()
        assertThat(didStart).isTrue()
    }
}