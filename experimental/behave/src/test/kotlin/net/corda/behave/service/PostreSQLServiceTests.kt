/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.service

import net.corda.behave.service.database.PostgreSQLService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Test

class PostreSQLServiceTests {

    @Ignore
    @Test
    fun `postgres can be started and stopped`() {
        val service = PostgreSQLService("test-postgres", 12345, "postgres")
        val didStart = service.start()
        service.stop()
        assertThat(didStart).isTrue()
    }

}