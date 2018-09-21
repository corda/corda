package net.corda.tools.error.codes.server.commons.domain.identity

import com.fasterxml.uuid.Generators
import java.util.*

class UuidGenerator {

    companion object {

        private val generator = Generators.timeBasedGenerator()

        @JvmStatic
        fun next(): UUID = generator.generate()
    }
}