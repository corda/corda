package net.corda.irs.api

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.RetryableException
import net.corda.core.contracts.*
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.MerkleTreeException
import net.corda.core.crypto.keys
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.identity.Party
import net.corda.core.math.CubicSplineInterpolator
import net.corda.core.math.Interpolator
import net.corda.core.math.InterpolatorFactory
import net.corda.core.node.PluginServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.ServiceType
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.irs.flows.RatesFixFlow
import net.corda.node.services.api.AcceptsFileUpload
import net.corda.node.utilities.AbstractJDBCHashSet
import net.corda.node.utilities.FiberBox
import net.corda.node.utilities.JDBCHashedTable
import net.corda.node.utilities.localDate
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import java.io.InputStream
import java.math.BigDecimal
import java.security.PublicKey
import java.time.Instant
import java.time.LocalDate
import java.util.*
import javax.annotation.concurrent.ThreadSafe
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

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
    // DOCSTART 2
    @InitiatedBy(RatesFixFlow.FixSignFlow::class)
    class FixSignHandler(val otherParty: Party) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val request = receive<RatesFixFlow.SignRequest>(otherParty).unwrap { it }
            val oracle = serviceHub.cordaService(Oracle::class.java)
            send(otherParty, oracle.sign(request.ftx))
        }
    }

    @InitiatedBy(RatesFixFlow.FixQueryFlow::class)
    class FixQueryHandler(val otherParty: Party) : FlowLogic<Unit>() {
        object RECEIVED : ProgressTracker.Step("Received fix request")
        object SENDING : ProgressTracker.Step("Sending fix response")

        override val progressTracker = ProgressTracker(RECEIVED, SENDING)

        @Suspendable
        override fun call(): Unit {
            val request = receive<RatesFixFlow.QueryRequest>(otherParty).unwrap { it }
            progressTracker.currentStep = RECEIVED
            val oracle = serviceHub.cordaService(Oracle::class.java)
            val answers = oracle.query(request.queries, request.deadline)
            progressTracker.currentStep = SENDING
            send(otherParty, answers)
        }
    }
    // DOCEND 2

    /**
     * An implementation of an interest rate fix oracle which is given data in a simple string format.
     *
     * The oracle will try to interpolate the missing value of a tenor for the given fix name and date.
     */
    @ThreadSafe
    // DOCSTART 3
    @CordaService
    class Oracle(val identity: Party, private val signingKey: PublicKey, val services: ServiceHub) : AcceptsFileUpload, SingletonSerializeAsToken() {
        constructor(services: PluginServiceHub) : this(
            services.myInfo.serviceIdentities(type).first(),
            services.myInfo.serviceIdentities(type).first().owningKey.keys.first { services.keyManagementService.keys.contains(it) },
            services
        )
        // DOCEND 3

        companion object {
            @JvmField
            val type = ServiceType.corda.getSubType("interest_rates")
        }

        private object Table : JDBCHashedTable("demo_interest_rate_fixes") {
            val name = varchar("index_name", length = 255)
            val forDay = localDate("for_day")
            val ofTenor = varchar("of_tenor", length = 16)
            val value = decimal("value", scale = 20, precision = 16)
        }

        private class InnerState {
            val fixes = object : AbstractJDBCHashSet<Fix, Table>(Table) {
                override fun elementFromRow(row: ResultRow): Fix {
                    return Fix(FixOf(row[table.name], row[table.forDay], Tenor(row[table.ofTenor])), row[table.value])
                }

                override fun addElementToInsert(insert: InsertStatement, entry: Fix, finalizables: MutableList<() -> Unit>) {
                    insert[table.name] = entry.of.name
                    insert[table.forDay] = entry.of.forDay
                    insert[table.ofTenor] = entry.of.ofTenor.name
                    insert[table.value] = entry.value
                }
            }
            var container: FixContainer = FixContainer(fixes)
        }

        private val mutex = FiberBox(InnerState())

        var knownFixes: FixContainer
            set(value) {
                require(value.size > 0)
                mutex.write {
                    fixes.clear()
                    fixes.addAll(value.fixes)
                    container = value
                }
            }
            get() = mutex.read { container }

        // Make this the last bit of initialisation logic so fully constructed when entered into instances map
        init {
            require(signingKey in identity.owningKey.keys)
        }

        /**
         * This method will now wait until the given deadline if the fix for the given [FixOf] is not immediately
         * available.  To implement this, [FiberBox.readWithDeadline] will loop if the deadline is not reached and we throw
         * [UnknownFix] as it implements [RetryableException] which has special meaning to this function.
         */
        @Suspendable
        fun query(queries: List<FixOf>, deadline: Instant): List<Fix> {
            require(queries.isNotEmpty())
            return mutex.readWithDeadline(services.clock, deadline) {
                val answers: List<Fix?> = queries.map { container[it] }
                val firstNull = answers.indexOf(null)
                if (firstNull != -1) {
                    throw UnknownFix(queries[firstNull])
                } else {
                    answers.filterNotNull()
                }
            }
        }

        // TODO There is security problem with that. What if transaction contains several commands of the same type, but
        //      Oracle gets signing request for only some of them with a valid partial tree? We sign over a whole transaction.
        //      It will be fixed by adding partial signatures later.
        // DOCSTART 1
        fun sign(ftx: FilteredTransaction): DigitalSignature.LegallyIdentifiable {
            if (!ftx.verify()) {
                throw MerkleTreeException("Rate Fix Oracle: Couldn't verify partial Merkle tree.")
            }
            // Performing validation of obtained FilteredLeaves.
            fun commandValidator(elem: Command): Boolean {
                if (!(identity.owningKey in elem.signers && elem.value is Fix))
                    throw IllegalArgumentException("Oracle received unknown command (not in signers or not Fix).")
                val fix = elem.value as Fix
                val known = knownFixes[fix.of]
                if (known == null || known != fix)
                    throw UnknownFix(fix.of)
                return true
            }

            fun check(elem: Any): Boolean {
                return when (elem) {
                    is Command -> commandValidator(elem)
                    else -> throw IllegalArgumentException("Oracle received data of different type than expected.")
                }
            }

            val leaves = ftx.filteredLeaves
            if (!leaves.checkWithFun(::check))
                throw IllegalArgumentException()

            // It all checks out, so we can return a signature.
            //
            // Note that we will happily sign an invalid transaction, as we are only being presented with a filtered
            // version so we can't resolve or check it ourselves. However, that doesn't matter much, as if we sign
            // an invalid transaction the signature is worthless.
            val signature = services.keyManagementService.sign(ftx.rootHash.bytes, signingKey)
            return DigitalSignature.LegallyIdentifiable(identity, signature.bytes)
        }
        // DOCEND 1

        // File upload support
        override val dataTypePrefix = "interest-rates"
        override val acceptableFileExtensions = listOf(".rates", ".txt")

        override fun upload(file: InputStream): String {
            val fixes = parseFile(file.bufferedReader().readText())
            knownFixes = fixes
            return "Interest rates oracle accepted ${fixes.size} new interest rate fixes"
        }
    }

    // TODO: can we split into two?  Fix not available (retryable/transient) and unknown (permanent)
    class UnknownFix(val fix: FixOf) : RetryableException("Unknown fix: $fix")

    /** Fix container, for every fix name & date pair stores a tenor to interest rate map - [InterpolatingRateMap] */
    class FixContainer(val fixes: Set<Fix>, val factory: InterpolatorFactory = CubicSplineInterpolator) {
        private val container = buildContainer(fixes)
        val size: Int get() = fixes.size

        operator fun get(fixOf: FixOf): Fix? {
            val rates = container[fixOf.name to fixOf.forDay]
            val fixValue = rates?.getRate(fixOf.ofTenor) ?: return null
            return Fix(fixOf, fixValue)
        }

        private fun buildContainer(fixes: Set<Fix>): Map<Pair<String, LocalDate>, InterpolatingRateMap> {
            val tempContainer = HashMap<Pair<String, LocalDate>, HashMap<Tenor, BigDecimal>>()
            for ((fixOf, value) in fixes) {
                val rates = tempContainer.getOrPut(fixOf.name to fixOf.forDay) { HashMap<Tenor, BigDecimal>() }
                rates[fixOf.ofTenor] = value
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
                map { parseFix(it) }.
                toSet()
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
