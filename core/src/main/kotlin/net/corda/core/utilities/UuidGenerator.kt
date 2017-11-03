package net.corda.core.utilities

import java.util.*

class UuidGenerator {

    companion object {
        // TODO sollecitom perhaps switch to time-based
        fun next() : UUID = UUID.randomUUID()
    }
}