package com.r3.corda.networkmanage.common.persistence

import net.corda.core.crypto.sha256
import java.security.PublicKey

// TODO: replace this with Crypto.hash when its available.
fun PublicKey.hash() = encoded.sha256().toString()
