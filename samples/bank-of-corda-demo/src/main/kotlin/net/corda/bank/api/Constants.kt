package net.corda.bank.api

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Party
import net.corda.core.crypto.composite
import net.corda.core.crypto.generateKeyPair
import net.corda.core.serialization.OpaqueBytes
import net.corda.testing.MEGA_CORP
import java.security.KeyPair
import java.security.PublicKey

val defaultRef = OpaqueBytes.of(1)

/*
 * Bank Of Corda (BOC_ISSUER_PARTY)
 */
val BOC_KEY: KeyPair by lazy { generateKeyPair() }
val BOC_PUBKEY: CompositeKey get() = BOC_KEY.public.composite
val BOC_ISSUER_PARTY: Party get() = Party("BankOfCorda", BOC_PUBKEY)
val BOC_ISSUER_PARTY_AND_REF = BOC_ISSUER_PARTY.ref(defaultRef)
val BOC_ISSUER_PARTY_REF = BOC_ISSUER_PARTY_AND_REF.reference