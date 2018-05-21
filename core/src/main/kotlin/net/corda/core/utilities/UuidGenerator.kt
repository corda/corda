package net.corda.core.utilities

import java.util.*

class UuidGenerator {

    companion object {
        fun next(): UUID = UUID.randomUUID()
    }
}