package net.corda.irs.api

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.RetryableException
import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.flows.FlowLogic
import net.corda.core.math.CubicSplineInterpolator
import net.corda.core.math.Interpolator
import net.corda.core.math.InterpolatorFactory
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.PluginServiceHub
import net.corda.core.node.services.ServiceType
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.irs.flows.FixingFlow
import net.corda.irs.flows.RatesFixFlow
import net.corda.node.services.api.AcceptsFileUpload
import net.corda.node.utilities.AbstractJDBCHashSet
import net.corda.node.utilities.FiberBox
import net.corda.node.utilities.JDBCHashedTable
import net.corda.node.utilities.localDate
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.InputStream
import java.math.BigDecimal
import java.security.KeyPair
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.*
import java.util.function.Function
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
    val type = ServiceType.corda.getSubType("interest_rates")

    /**
     * Register the flow that is used with the Fixing integration tests.
     */
    class Plugin : CordaPluginRegistry() {
        override val requiredFlows = mapOf(Pair(FixingFlow.FixingRoleDecider::class.java.name, setOf(Duration::class.java.name, StateRef::class.java.name)))
        override val servicePlugins = listOf(Function(::Service))
    }

    /**
     * The Service that wraps [Oracle] and handles messages/network interaction/request scrubbing.
     */
    // DOCSTART 2
    class Service(val services: PluginServiceHub) : AcceptsFileUpload, SingletonSerializeAsToken() {
        val oracle: Oracle by lazy {
            val myNodeInfo = services.myInfo
            val myIdentity = myNodeInfo.serviceIdentities(type).first()
            val mySigningKey = services.keyManagementService.toKeyPair(myIdentity.owningKey.keys)
            Oracle(myIdentity, mySigningKey, services.clock)
        }

        init {
            // Note: access to the singleton oracle property is via the registered SingletonSerializeAsToken Service.
            // Otherwise the Kryo serialisation of the call stack in the Quasar Fiber extends to include
            // the framework Oracle and the flow will crash.
            services.registerFlowInitiator(RatesFixFlow.FixSignFlow::class) { FixSignHandler(it, this) }
            services.registerFlowInitiator(RatesFixFlow.FixQueryFlow::class) { FixQueryHandler(it, this) }
        }

        private class FixSignHandler(val otherParty: Party, val service: Service) : FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                val request = receive<RatesFixFlow.SignRequest>(otherParty).unwrap { it }
                send(otherParty, service.oracle.sign(request.ftx, request.rootHash))
            }
        }

        private class FixQueryHandler(val otherParty: Party, val service: Service) : FlowLogic<Unit>() {
            companion object {
                object RECEIVED : ProgressTracker.Step("Received fix request")
                object SENDING : ProgressTracker.Step("Sending fix response")
            }

            override val progressTracker = ProgressTracker(RECEIVED, SENDING)

            init {
                progressTracker.currentStep = RECEIVED
            }

            @Suspendable
            override fun call(): Unit {
                val request = receive<RatesFixFlow.QueryRequest>(otherParty).unwrap { it }
                val answers = service.oracle.query(request.queries, request.deadline)
                progressTracker.currentStep = SENDING
                send(otherParty, answers)
            }
        }
        // DOCEND 2

        // File upload support
        override val dataTypePrefix = "interest-rates"
        override val acceptableFileExtensions = listOf(".rates", ".txt")

        override fun upload(data: InputStream): String {
            val fixes = parseFile(data.bufferedReader().readText())
            // TODO: Look into why knownFixes requires a transaction
            transaction {
                oracle.knownFixes = fixes
            }
            val msg = "Interest rates oracle accepted ${fixes.size} new interest rate fixes"
            println(msg)
            return msg
        }
    }

    /**
     * An implementation of an interest rate fix oracle which is given data in a simple string format.
     *
     * The oracle will try to interpolate the missing value of a tenor for the given fix name and date.
     */
    @ThreadSafe
    class Oracle(val identity: Party, private val signingKey: KeyPair, val clock: Clock) {
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
            require(signingKey.public in identity.owningKey.keys)
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

        // TODO There is security problem with that. What if transaction contains several commands of the same type, but
        //      Oracle gets signing request for only some of them with a valid partial tree? We sign over a whole transaction.
        //      It will be fixed by adding partial signatures later.
        // DOCSTART 1
        fun sign(ftx: FilteredTransaction, merkleRoot: SecureHash): DigitalSignature.LegallyIdentifiable {
            if (!ftx.verify(merkleRoot)) {
                throw MerkleTreeException("Rate Fix Oracle: Couldn't verify partial Merkle tree.")
            }

            // Reject if we have something different than only commands.
            val leaves = ftx.filteredLeaves
            require(leaves.inputs.isEmpty() && leaves.outputs.isEmpty() && leaves.attachments.isEmpty())

            val fixes: List<Fix> = ftx.filteredLeaves.commands.
                    filter { identity.owningKey in it.signers && it.value is Fix }.
                    map { it.value as Fix }

            // Reject signing attempt if we received more commands than we should.
            if (fixes.size != ftx.filteredLeaves.commands.size)
                throw IllegalArgumentException()

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
            // Note that we will happily sign an invalid transaction, as we are only being presented with a filtered
            // version so we can't resolve or check it ourselves. However, that doesn't matter much, as if we sign
            // an invalid transaction the signature is worthless.
            return signingKey.signWithECDSA(merkleRoot.bytes, identity)
        }
        // DOCEND 1
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
