package net.corda.confidential.service

import net.corda.core.serialization.CordaSerializable
import java.util.*

@CordaSerializable
class CreateKeyForAccount(private val _uuid: UUID) {
    val uuid: UUID
        get() = _uuid
}