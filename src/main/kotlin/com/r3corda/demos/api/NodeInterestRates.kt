package com.r3corda.demos.api

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.RetryableException
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.signWithECDSA
import com.r3corda.core.math.CubicSplineInterpolator
import com.r3corda.core.math.Interpolator
import com.r3corda.core.math.InterpolatorFactory
import com.r3corda.core.node.CordaPluginRegistry
import com.r3corda.core.node.services.ServiceType
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.transactions.WireTransaction
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.node.services.api.AbstractNodeService
import com.r3corda.node.services.api.AcceptsFileUpload
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.node.utilities.FiberBox
import com.r3corda.protocols.RatesFixProtocol
import com.r3corda.protocols.ServiceRequestMessage
import com.r3corda.protocols.TwoPartyDealProtocol
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.math.BigDecimal
import java.security.KeyPair
import java.time.Clock
import java.time.Duration
import java.time.Instant
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
    object Type : ServiceType("corda.interest_rates")

    /**
     * Register the protocol that is used with the Fixing integration tests.
     */
    class Plugin : CordaPluginRegistry() {
        override val requiredProtocols: Map<String, Set<String>> = mapOf(Pair(TwoPartyDealProtocol.FixingRoleDecider::class.java.name, setOf(Duration::class.java.name, StateRef::class.java.name)))
        override val servicePlugins: List<Class<*>> = listOf(Service::class.java)
    }

    /**
     * The Service that wraps [Oracle] and handles messages/network interaction/request scrubbing.
     */
    class Service(services: ServiceHubInternal) : AcceptsFileUpload, AbstractNodeService(services.networkService, services.networkMapCache) {
        val ss = services.storageService
        val oracle = Oracle(ss.myLegalIdentity, ss.myLegalIdentityKey, services.clock)

        private val logger = LoggerFactory.getLogger(Service::class.java)

        init {
            addMessageHandler(RatesFixProtocol.TOPIC,
                    { req: ServiceRequestMessage ->
                        if (req is RatesFixProtocol.SignRequest) {
                            oracle.sign(req.tx)
                        }
                        else {
                            /**
                             * We put this into a protocol so that if it blocks waiting for the interest rate to become
                             * available, we a) don't block this thread and b) allow the fact we are waiting
                             * to be persisted/checkpointed.
                             * Interest rates become available when they are uploaded via the web as per [DataUploadServlet],
                             * if they haven't already been uploaded that way.
                             */
                            services.startProtocol("fixing", FixQueryHandler(this, req as RatesFixProtocol.QueryRequest))
                            Unit
                        }
                    },
                    { message, e -> logger.error("Exception during interest rate oracle request processing", e) }
            )
        }

        private class FixQueryHandler(val service: Service,
                                      val request: RatesFixProtocol.QueryRequest) : ProtocolLogic<Unit>() {

            companion object {
                object RECEIVED : ProgressTracker.Step("Received fix request")
                object SENDING : ProgressTracker.Step("Sending fix response")
            }

            override val topic: String get() = RatesFixProtocol.TOPIC
            override val progressTracker = ProgressTracker(RECEIVED, SENDING)

            init {
                progressTracker.currentStep = RECEIVED
            }

            @Suspendable
            override fun call(): Unit {
                val answers = service.oracle.query(request.queries, request.deadline)
                progressTracker.currentStep = SENDING
                send(request.replyToParty, request.sessionID, answers)
            }
        }

        // File upload support
        override val dataTypePrefix = "interest-rates"
        override val acceptableFileExtensions = listOf(".rates", ".txt")

        override fun upload(data: InputStream): String {
            val fixes = parseFile(data.bufferedReader().readText())
            // TODO: Save the uploaded fixes to the storage service and reload on construction.

            // This assignment is thread safe because knownFixes is volatile and the oracle code always snapshots
            // the pointer to the stack before working with the map.
            oracle.knownFixes = fixes

            return "Accepted ${fixes.size} new interest rate fixes"
        }
    }

    /**
     * An implementation of an interest rate fix oracle which is given data in a simple string format.
     *
     * The oracle will try to interpolate the missing value of a tenor for the given fix name and date.
     */
    @ThreadSafe
    class Oracle(val identity: Party, private val signingKey: KeyPair, val clock: Clock) {
        private class InnerState {
            var container: FixContainer = FixContainer(emptyList<Fix>())

        }
        private val mutex = FiberBox(InnerState())

        var knownFixes: FixContainer
            set(value) {
                require(value.size > 0)
                mutex.write {
                    container = value
                }
            }
            get() = mutex.read { container }

        // Make this the last bit of initialisation logic so fully constructed when entered into instances map
        init {
            require(signingKey.public == identity.owningKey)
        }

        /**
         * This method will now wait until the given deadline if the fix for the given [FixOf] is not immediately
         * available.  To implement this, [readWithDeadline] will loop if the deadline is not reached and we throw
         * [UnknownFix] as it implements [RetryableException] which has special meaning to this function.
         */
        @Suspendable
        fun query(queries: List<FixOf>, deadline: Instant): List<Fix> {
            require(queries.isNotEmpty())
            return mutex.readWithDeadline(clock, deadline) {
                val answers: List<Fix?> = queries.map { container[it] }
                val firstNull = answers.indexOf(null)
                if (firstNull != -1) {
                    throw UnknownFix(queries[firstNull])
                } else {
                    answers.filterNotNull()
                }
            }
        }

        fun sign(wtx: WireTransaction): DigitalSignature.LegallyIdentifiable {
            // Extract the fix commands marked as being signable by us.
            val fixes: List<Fix> = wtx.commands.
                    filter { identity.owningKey in it.signers && it.value is Fix }.
                    map { it.value as Fix }

            // Reject this signing attempt if there are no commands of the right kind.
            if (fixes.isEmpty())
                throw IllegalArgumentException()

            // For each fix, verify that the data is correct.
            val knownFixes = knownFixes // Snapshot
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

    // TODO: can we split into two?  Fix not available (retryable/transient) and unknown (permanent)
    class UnknownFix(val fix: FixOf) : RetryableException("Unknown fix: $fix")

    /** Fix container, for every fix name & date pair stores a tenor to interest rate map - [InterpolatingRateMap] */
    class FixContainer(fixes: List<Fix>, val factory: InterpolatorFactory = CubicSplineInterpolator) {
        private val container = buildContainer(fixes)
        val size = fixes.size

        operator fun get(fixOf: FixOf): Fix? {
            val rates = container[fixOf.name to fixOf.forDay]
            val fixValue = rates?.getRate(fixOf.ofTenor) ?: return null
            return Fix(fixOf, fixValue)
        }

        private fun buildContainer(fixes: List<Fix>): Map<Pair<String, LocalDate>, InterpolatingRateMap> {
            val tempContainer = HashMap<Pair<String, LocalDate>, HashMap<Tenor, BigDecimal>>()
            for (fix in fixes) {
                val fixOf = fix.of
                val rates = tempContainer.getOrPut(fixOf.name to fixOf.forDay) { HashMap<Tenor, BigDecimal>() }
                rates[fixOf.ofTenor] = fix.value
            }

            // TODO: the calendar data needs to be specified for every fix type in the input string
            val calendar = BusinessCalendar.getInstance("London", "NewYork")

            return tempContainer.mapValues { InterpolatingRateMap(it.key.second, it.value, calendar, factory) }
        }
    }

    /**
     * Stores a mapping between tenors and interest rates.
     * Interpolates missing values using the provided interpolation mechanism.
     */
    class InterpolatingRateMap(val date: LocalDate,
                               inputRates: Map<Tenor, BigDecimal>,
                               val calendar: BusinessCalendar,
                               val factory: InterpolatorFactory) {

        /** Snapshot of the input */
        private val rates = HashMap(inputRates)

        /** Number of rates excluding the interpolated ones */
        val size = inputRates.size

        private val interpolator: Interpolator? by lazy {
            // Need to convert tenors to doubles for interpolation
            val numericMap = rates.mapKeys { daysToMaturity(it.key) }.toSortedMap()
            val keys = numericMap.keys.map { it.toDouble() }.toDoubleArray()
            val values = numericMap.values.map { it.toDouble() }.toDoubleArray()

            try {
                factory.create(keys, values)
            } catch (e: IllegalArgumentException) {
                null // Not enough data points for interpolation
            }
        }

        /**
         * Returns the interest rate for a given [Tenor],
         * or _null_ if the rate is not found and cannot be interpolated.
         */
        fun getRate(tenor: Tenor): BigDecimal? {
            return rates.getOrElse(tenor) {
                val rate = interpolate(tenor)
                if (rate != null) rates.put(tenor, rate)
                return rate
            }
        }

        private fun daysToMaturity(tenor: Tenor) = tenor.daysToMaturity(date, calendar)

        private fun interpolate(tenor: Tenor): BigDecimal? {
            val key = daysToMaturity(tenor).toDouble()
            val value = interpolator?.interpolate(key) ?: return null
            return BigDecimal(value)
        }
    }

    /** Parses lines containing fixes */
    fun parseFile(s: String): FixContainer {
        val fixes = s.lines().
                map(String::trim).
                // Filter out comment and empty lines.
                filterNot { it.startsWith("#") || it.isBlank() }.
                map { parseFix(it) }
        return FixContainer(fixes)
    }

    /** Parses a string of the form "LIBOR 16-March-2016 1M = 0.678" into a [Fix] */
    fun parseFix(s: String): Fix {
        val (key, value) = s.split('=').map(String::trim)
        val of = parseFixOf(key)
        val rate = BigDecimal(value)
        return Fix(of, rate)
    }

    /** Parses a string of the form "LIBOR 16-March-2016 1M" into a [FixOf] */
    fun parseFixOf(key: String): FixOf {
        val words = key.split(' ')
        val tenorString = words.last()
        val date = words.dropLast(1).last()
        val name = words.dropLast(2).joinToString(" ")
        return FixOf(name, LocalDate.parse(date), Tenor(tenorString))
    }
}
