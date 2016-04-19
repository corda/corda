package core.node.services

import core.*
import core.crypto.DigitalSignature
import core.crypto.signWithECDSA
import core.math.CubicSplineInterpolator
import core.math.Interpolator
import core.math.InterpolatorFactory
import core.messaging.Message
import core.messaging.MessagingService
import core.messaging.send
import core.node.AbstractNode
import core.node.AcceptsFileUpload
import core.serialization.deserialize
import org.slf4j.LoggerFactory
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
    object Type : ServiceType("corda.interest_rates")
    /**
     * The Service that wraps [Oracle] and handles messages/network interaction/request scrubbing.
     */
    class Service(node: AbstractNode) : AcceptsFileUpload, AbstractNodeService(node.services.networkService) {
        val ss = node.services.storageService
        val oracle = Oracle(ss.myLegalIdentity, ss.myLegalIdentityKey)

        private val logger = LoggerFactory.getLogger(NodeInterestRates.Service::class.java)

        init {
            addMessageHandler(RatesFixProtocol.TOPIC_SIGN,
                    { req: RatesFixProtocol.SignRequest -> oracle.sign(req.tx) },
                    { message, e -> logger.error("Exception during interest rate oracle request processing", e) }
            )
            addMessageHandler(RatesFixProtocol.TOPIC_QUERY,
                    { req: RatesFixProtocol.QueryRequest -> oracle.query(req.queries) },
                    { message, e -> logger.error("Exception during interest rate oracle request processing", e) }
            )
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

            return "Accepted $fixes.size new interest rate fixes"
        }
    }

    /**
     * An implementation of an interest rate fix oracle which is given data in a simple string format.
     *
     * The oracle will try to interpolate the missing value of a tenor for the given fix name and date.
     */
    @ThreadSafe
    class Oracle(val identity: Party, private val signingKey: KeyPair) {
        init {
            require(signingKey.public == identity.owningKey)
        }

        @Volatile var knownFixes = FixContainer(emptyList<Fix>())
            set(value) {
                require(value.size > 0)
                field = value
            }

        fun query(queries: List<FixOf>): List<Fix> {
            require(queries.isNotEmpty())
            val knownFixes = knownFixes // Snapshot
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

    class UnknownFix(val fix: FixOf) : Exception() {
        override fun toString() = "Unknown fix: $fix"
    }

    /** Fix container, for every fix name & date pair stores a tenor to interest rate map - [InterpolatingRateMap] */
    class FixContainer(val fixes: List<Fix>, val factory: InterpolatorFactory = CubicSplineInterpolator.Factory) {
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
                               val inputRates: Map<Tenor, BigDecimal>,
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
         * or _null_ if the rate is not found and cannot be interpolated
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
                map { it.trim() }.
                // Filter out comment and empty lines.
                filterNot { it.startsWith("#") || it.isBlank() }.
                map { parseFix(it) }
        return FixContainer(fixes)
    }

    /** Parses a string of the form "LIBOR 16-March-2016 1M = 0.678" into a [Fix] */
    fun parseFix(s: String): Fix {
        val (key, value) = s.split('=').map { it.trim() }
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