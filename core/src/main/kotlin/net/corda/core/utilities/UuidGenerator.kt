package net.corda.core.utilities

import net.corda.core.NonDeterministic
import java.util.*

@NonDeterministic
class UuidGenerator {

    companion object {
        fun next(): UUID = UUID.randomUUID()
    }
}