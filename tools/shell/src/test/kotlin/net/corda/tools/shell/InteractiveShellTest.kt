package net.corda.tools.shell

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import net.corda.client.jackson.JacksonSupport
import net.corda.client.jackson.internal.ToStringSerialize
import net.corda.core.contracts.Amount
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.generateKeyPair
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.messaging.InternalCordaRPCOps
import net.corda.core.messaging.FlowProgressHandleImpl
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.ProgressTracker
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.getTestPartyAndCertificate
import net.corda.testing.internal.DEV_ROOT_CA
import org.crsh.command.InvocationContext
import org.crsh.text.Color
import org.crsh.text.RenderPrintWriter
import org.junit.Before
import org.junit.Test
import rx.Observable
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InteractiveShellTest {
    lateinit var inputObjectMapper: ObjectMapper
    lateinit var cordaRpcOps: InternalCordaRPCOps
    lateinit var invocationContext: InvocationContext<Map<Any, Any>>
    lateinit var printWriter: RenderPrintWriter

    @Before
    fun setup() {
        inputObjectMapper = objectMapperWithClassLoader(InteractiveShell.getCordappsClassloader())
        cordaRpcOps = mock()
        invocationContext = mock()
        printWriter = mock()
    }

    companion object {
        private val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))

        private val ALICE = getTestPartyAndCertificate(ALICE_NAME, generateKeyPair().public)
        private val BOB = getTestPartyAndCertificate(BOB_NAME, generateKeyPair().public)
        private val ALICE_NODE_INFO = NodeInfo(listOf(NetworkHostAndPort("localhost", 8080)), listOf(ALICE), 1, 1)
        private val BOB_NODE_INFO = NodeInfo(listOf(NetworkHostAndPort("localhost", 80)), listOf(BOB), 1, 1)
        private val NODE_INFO_JSON_PAYLOAD =
                """
                {
                  "addresses" : [ "localhost:8080" ],
                  "legalIdentitiesAndCerts" : [ "O=Alice Corp, L=Madrid, C=ES" ],
                  "platformVersion" : 1,
                  "serial" : 1
                }
                """.trimIndent()
        private val NODE_INFO_YAML_PAYLOAD =
                """
                    addresses:
                    - "localhost:8080"
                    legalIdentitiesAndCerts:
                    - "O=Alice Corp, L=Madrid, C=ES"
                    platformVersion: 1
                    serial: 1

                """.trimIndent()
        private val NETWORK_MAP_JSON_PAYLOAD =
                """
                    [ {
                      "addresses" : [ "localhost:8080" ],
                      "legalIdentitiesAndCerts" : [ "O=Alice Corp, L=Madrid, C=ES" ],
                      "platformVersion" : 1,
                      "serial" : 1
                    }, {
                      "addresses" : [ "localhost:80" ],
                      "legalIdentitiesAndCerts" : [ "O=Bob Plc, L=Rome, C=IT" ],
                      "platformVersion" : 1,
                      "serial" : 1
                    } ]
                """.trimIndent()
        private val NETWORK_MAP_YAML_PAYLOAD =
                """
                    - addresses:
                      - "localhost:8080"
                      legalIdentitiesAndCerts:
                      - "O=Alice Corp, L=Madrid, C=ES"
                      platformVersion: 1
                      serial: 1
                    - addresses:
                      - "localhost:80"
                      legalIdentitiesAndCerts:
                      - "O=Bob Plc, L=Rome, C=IT"
                      platformVersion: 1
                      serial: 1

                """.trimIndent()
    }


    private val ids = InMemoryIdentityService(listOf(megaCorp.identity), DEV_ROOT_CA.certificate)
    @Suppress("DEPRECATION")
    private val om = JacksonSupport.createInMemoryMapper(ids, YAMLFactory())

    private fun check(input: String, expected: String) {
        var output: String? = null
        InteractiveShell.runFlowFromString({ clazz, args ->
            val instance = clazz.getConstructor(*args.map { it!!::class.java }.toTypedArray()).newInstance(*args) as FlowA
            output = instance.a
            val future = openFuture<String>()
            future.set("ABC")
            FlowProgressHandleImpl(StateMachineRunId.createRandom(), future, Observable.just("Some string"))
        }, input, FlowA::class.java, om)
        assertEquals(expected, output!!, input)
    }

    private fun objectMapperWithClassLoader(classLoader: ClassLoader?): ObjectMapper {
        val objectMapper = JacksonSupport.createNonRpcMapper()
        val tf = TypeFactory.defaultInstance().withClassLoader(classLoader)
        objectMapper.typeFactory = tf

        return objectMapper
    }

    @Test
    fun flowStartSimple() {
        check("a: Hi there", "Hi there")
        check("b: 12", "12")
        check("b: 12, c: Yo", "12Yo")
    }

    @Test
    fun flowStartWithComplexTypes() = check("amount: £10", "10.00 GBP")

    @Test
    fun flowStartWithNestedTypes() = check(
            input = "pair: { first: $100.12, second: df489807f81c8c8829e509e1bcb92e6692b9dd9d624b7456435cb2f51dc82587 }",
            expected = "(100.12 USD, DF489807F81C8C8829E509E1BCB92E6692B9DD9D624B7456435CB2F51DC82587)"
    )

    @Test
    fun flowStartWithArrayType() = check(
            input = "c: [ One, Two, Three, Four ]",
            expected = "One+Two+Three+Four"
    )

    @Test
    fun flowStartWithUserAmount() = check(
            input = """b: 500, amount: { "quantity": 10001, "token":{ "label": "of value" } }""",
            expected = "10501 of value"
    )

    @Test
    fun flowStartWithArrayOfNestedTypes() = check(
            input = """amounts: [ { "quantity": 10, "token": { "label": "(1)" } }, { "quantity": 200, "token": { "label": "(2)" } } ]""",
            expected = "10 (1)++200 (2)"
    )

    @Test(expected = InteractiveShell.NoApplicableConstructor::class)
    fun flowStartNoArgs() = check("", "")

    @Test(expected = InteractiveShell.NoApplicableConstructor::class)
    fun flowMissingParam() = check("d: Yo", "")

    @Test(expected = InteractiveShell.NoApplicableConstructor::class)
    fun flowTooManyParams() = check("b: 12, c: Yo, d: Bar", "")

    @Test
    fun niceTypeNamesInErrors() {
        val e = assertFailsWith<InteractiveShell.NoApplicableConstructor> {
            check("", expected = "")
        }
        val correct = setOf(
                "[amounts: Amount<UserValue>[]]: missing parameter amounts",
                "[amount: Amount<Currency>]: missing parameter amount",
                "[pair: Pair<Amount<Currency>, SecureHash.SHA256>]: missing parameter pair",
                "[party: Party]: missing parameter party",
                "[b: Integer, amount: Amount<UserValue>]: missing parameter b",
                "[c: String[]]: missing parameter c",
                "[b: Integer, c: String]: missing parameter b",
                "[a: String]: missing parameter a",
                "[b: Integer]: missing parameter b"
        )
        val errors = e.errors.toHashSet()
        errors.removeAll(correct)
        assert(errors.isEmpty()) { errors.joinToString(", ") }
    }

    @Test
    fun party() = check("party: \"${megaCorp.name}\"", megaCorp.name.toString())

    @Test
    fun runRpcFromStringWithCustomTypeResult() {
        val command = listOf("nodeInfo")
        whenever(cordaRpcOps.nodeInfo()).thenReturn(ALICE_NODE_INFO)

        InteractiveShell.setOutputFormat(InteractiveShell.OutputFormat.YAML)
        InteractiveShell.runRPCFromString(command, printWriter, invocationContext, cordaRpcOps, inputObjectMapper)
        verify(printWriter).println(NODE_INFO_YAML_PAYLOAD)


        InteractiveShell.setOutputFormat(InteractiveShell.OutputFormat.JSON)
        InteractiveShell.runRPCFromString(command, printWriter, invocationContext, cordaRpcOps, inputObjectMapper)
        verify(printWriter).println(NODE_INFO_JSON_PAYLOAD.replace("\n", System.lineSeparator()))
    }

    @Test
    fun runRpcFromStringWithCollectionsResult() {
        val command = listOf("networkMapSnapshot")
        whenever(cordaRpcOps.networkMapSnapshot()).thenReturn(listOf(ALICE_NODE_INFO, BOB_NODE_INFO))

        InteractiveShell.setOutputFormat(InteractiveShell.OutputFormat.YAML)
        InteractiveShell.runRPCFromString(command, printWriter, invocationContext, cordaRpcOps, inputObjectMapper)
        verify(printWriter).println(NETWORK_MAP_YAML_PAYLOAD)

        InteractiveShell.setOutputFormat(InteractiveShell.OutputFormat.JSON)
        InteractiveShell.runRPCFromString(command, printWriter, invocationContext, cordaRpcOps, inputObjectMapper)
        verify(printWriter).println(NETWORK_MAP_JSON_PAYLOAD.replace("\n", System.lineSeparator()))
    }

    @Test
    fun killFlowWithNonsenseID() {
        InteractiveShell.killFlowById("nonsense", printWriter, cordaRpcOps, om)
        verify(printWriter).println("Cannot parse flow ID of 'nonsense' - expecting a UUID.", Color.red)
        verify(printWriter).flush()
    }

    @Test
    fun killFlowFailure() {
        val runId = StateMachineRunId.createRandom()
        whenever(cordaRpcOps.killFlow(any())).thenReturn(false)

        InteractiveShell.killFlowById(runId.uuid.toString(), printWriter, cordaRpcOps, om)
        verify(cordaRpcOps).killFlow(runId)
        verify(printWriter).println("Failed to kill flow $runId", Color.red)
        verify(printWriter).flush()
    }

    @Test
    fun killFlowSuccess() {
        val runId = StateMachineRunId.createRandom()
        whenever(cordaRpcOps.killFlow(any())).thenReturn(true)

        InteractiveShell.killFlowById(runId.uuid.toString(), printWriter, cordaRpcOps, om)
        verify(cordaRpcOps).killFlow(runId)
        verify(printWriter).println("Killed flow $runId", Color.yellow)
        verify(printWriter).flush()
    }
}

@ToStringSerialize
data class UserValue(@JsonProperty("label") val label: String) {
    override fun toString() = label
}

@Suppress("UNUSED")
class FlowA(val a: String) : FlowLogic<String>() {
    constructor(b: Int?) : this(b.toString())
    constructor(b: Int?, c: String) : this(b.toString() + c)
    constructor(amount: Amount<Currency>) : this(amount.toString())
    constructor(pair: Pair<Amount<Currency>, SecureHash.SHA256>) : this(pair.toString())
    constructor(party: Party) : this(party.name.toString())
    constructor(b: Int?, amount: Amount<UserValue>) : this("${(b ?: 0) + amount.quantity} ${amount.token}")
    constructor(c: Array<String>) : this(c.joinToString("+"))
    constructor(amounts: Array<Amount<UserValue>>) : this(amounts.joinToString("++", transform = Amount<UserValue>::toString))

    override val progressTracker = ProgressTracker()
    override fun call() = a
}
