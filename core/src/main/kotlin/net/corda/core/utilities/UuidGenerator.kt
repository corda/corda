package net.corda.core.utilities

import net.corda.core.DeleteForDJVM
import java.util.*

@DeleteForDJVM
class UuidGenerator {

    companion object {
        fun next(): UUID = UUID.randomUUID()
    }
}