/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node.services

import core.*
import core.crypto.DigitalSignature
import core.crypto.signWithECDSA
import core.messaging.send
import core.node.AbstractNode
import core.node.AcceptsFileUpload
import core.serialization.deserialize
import protocols.RatesFixProtocol
import java.io.InputStream
import java.math.BigDecimal
import java.security.KeyPair
import java.time.LocalDate
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * An interest rates service is an oracle that signs transactions which contain embedded assertions about an interest
 * rate fix (e.g. LIBOR, EURIBOR ...).
 *
 * The oracle has two functions. It can be queried for a fix for the given day. And it can sign a transaction that
 * includes a fix that it finds acceptable. So to use it you would query the oracle, incorporate its answer into the
 * transaction you are building, and then (after possibly extra steps) hand the final transaction back to the oracle
 * for signing.
 */
object NodeInterestRates {
    /** Parses a string of the form "LIBOR 16-March-2016 30 = 0.678" into a [FixOf] and [Fix] */
    fun parseOneRate(s: String): Pair<FixOf, Fix> {
        val (key, value) = s.split('=').map { it.trim() }
        val of = parseFixOf(key)
        val rate = BigDecimal(value)
        return of to Fix(of, rate)
    }

    /** Parses a string of the form "LIBOR 16-March-2016 30" into a [FixOf] */
    fun parseFixOf(key: String): FixOf {
        val (name, date, days) = key.split(' ')
        return FixOf(name, LocalDate.parse(date), days.toInt().days)
    }

    /** Parses lines containing fixes */
    fun parseFile(s: String): Map<FixOf, Fix> {
        val results = HashMap<FixOf, Fix>()
        for (line in s.lines()) {
            val (fixOf, fix) = parseOneRate(line.trim())
            results[fixOf] = fix
        }
        return results
    }

    /**
     * The Service that wraps [Oracle] and handles messages/network interaction/request scrubbing.
     */
    class Service(node: AbstractNode) : AcceptsFileUpload {
        val ss = node.services.storageService
        val oracle = Oracle(ss.myLegalIdentity, ss.myLegalIdentityKey)
        val net = node.services.networkService

        init {
            handleQueries()
            handleSignRequests()
        }

        private fun handleSignRequests() {
            net.addMessageHandler(RatesFixProtocol.TOPIC + ".sign.0") { message, registration ->
                val request = message.data.deserialize<RatesFixProtocol.SignRequest>()
                val sig = oracle.sign(request.tx)
                net.send("${RatesFixProtocol.TOPIC}.sign.${request.sessionID}", request.replyTo, sig)
            }
        }

        private fun handleQueries() {
            net.addMessageHandler(RatesFixProtocol.TOPIC + ".query.0") { message, registration ->
                val request = message.data.deserialize<RatesFixProtocol.QueryRequest>()
                val answers = oracle.query(request.queries)
                net.send("${RatesFixProtocol.TOPIC}.query.${request.sessionID}", request.replyTo, answers)
            }
        }

        // File upload support
        override val dataTypePrefix = "interest-rates"
        override val acceptableFileExtensions = listOf(".rates", ".txt")

        override fun upload(data: InputStream): String {
            val fixes: Map<FixOf, Fix> = data.
                    bufferedReader().
                    readLines().
                    map { it.trim() }.
                    // Filter out comment and empty lines.
                    filterNot { it.startsWith("#") || it.isBlank() }.
                    map { parseOneRate(it) }.
                    associate { it.first to it.second }

            // TODO: Save the uploaded fixes to the storage service and reload on construction.

            // This assignment is thread safe because knownFixes is volatile and the oracle code always snapshots
            // the pointer to the stack before working with the map.
            oracle.knownFixes = fixes

            return "Accepted ${fixes.size} new interest rate fixes"
        }
    }

    /**
     * An implementation of an interest rate fix oracle which is given data in a simple string format.
     */
    @ThreadSafe
    class Oracle(val identity: Party, private val signingKey: KeyPair) {
        init {
            require(signingKey.public == identity.owningKey)
        }

        /** The fix data being served by this oracle. */
        @Transient var knownFixes = emptyMap<FixOf, Fix>()
            set(value) {
                require(value.isNotEmpty())
                field = value
            }

        fun query(queries: List<FixOf>): List<Fix> {
            require(queries.isNotEmpty())
            val knownFixes = knownFixes   // Snapshot

            val answers: List<Fix?> = queries.map { knownFixes[it] }
            val firstNull = answers.indexOf(null)
            if (firstNull != -1)
                throw UnknownFix(queries[firstNull])
            return answers.filterNotNull()
        }

        fun sign(wtx: WireTransaction): DigitalSignature.LegallyIdentifiable {
            // Extract the fix commands marked as being signable by us.
            val fixes: List<Fix> = wtx.commands.
                    filter { identity.owningKey in it.pubkeys && it.data is Fix }.
                    map { it.data as Fix }

            // Reject this signing attempt if there are no commands of the right kind.
            if (fixes.isEmpty())
                throw IllegalArgumentException()

            // For each fix, verify that the data is correct.
            val knownFixes = knownFixes   // Snapshot
            for (fix in fixes) {
                val known = knownFixes[fix.of]
                if (known == null || known != fix)
                    throw UnknownFix(fix.of)
            }

            // It all checks out, so we can return a signature.
            //
            // Note that we will happily sign an invalid transaction: we don't bother trying to validate the whole
            // thing. This is so that later on we can start using tear-offs.
            return signingKey.signWithECDSA(wtx.serialized, identity)
        }
    }

    class UnknownFix(val fix: FixOf) : Exception() {
        override fun toString() = "Unknown fix: $fix"
    }
}