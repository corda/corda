@file:JvmName("MessageUtils")
package net.corda.attestation.message

import java.util.*

fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)
