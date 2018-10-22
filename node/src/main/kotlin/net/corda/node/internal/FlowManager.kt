package net.corda.node.internal

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.node.internal.classloading.requireAnnotation
import net.corda.node.services.config.FlowOverrideConfig
import net.corda.node.services.statemachine.appName
import net.corda.node.services.statemachine.flowVersionAndInitiatingClass
import javax.annotation.concurrent.ThreadSafe
import kotlin.reflect.KClass

/**
 *
 * This class is responsible for organising which flow should respond to a specific @InitiatingFlow
 *
 * There are two main ways to modify the behaviour of a cordapp with regards to responding with a different flow
 *
 *    1.) implementing a new subclass. For example, if we have a ResponderFlow similar to  @InitiatedBy(Sender) MyBaseResponder : FlowLogic
 *        If we subclassed a new Flow with specific logic for DB2, it would be similar to IBMB2Responder() : MyBaseResponder
 *        When these two flows are encountered by the classpath scan for @InitiatedBy, they will both be selected for responding to Sender
 *        This implementation will sort them for responding in order of their "depth" from FlowLogic - see: FlowWeightComparator
 *        So IBMB2Responder would win and it would be selected for responding
 *
 *    2.) It is possible to specify a flowOverride key in the node configuration. Say we configure a node to have
 *        flowOverrides{
 *          "Sender" = "MyBaseResponder"
 *        }
 *        In this case, FlowWeightComparator would detect that there is an override in action, and it will assign MyBaseResponder a maximum weight
 *        This will result in MyBaseResponder being selected for responding to Sender
 *
 *
 */
interface FlowManager {

    fun registerInitiatedCoreFlowFactory(initiatingFlowClass: KClass<out FlowLogic<*>>, flowFactory: (FlowSession) -> FlowLogic<*>)
    fun registerInitiatedCoreFlowFactory(initiatingFlowClass: KClass<out FlowLogic<*>>, initiatedFlowClass: KClass<out FlowLogic<*>>?, flowFactory: (FlowSession) -> FlowLogic<*>)
    fun registerInitiatedCoreFlowFactory(initiatingFlowClass: KClass<out FlowLogic<*>>, initiatedFlowClass: KClass<out FlowLogic<*>>?, flowFactory: InitiatedFlowFactory.Core<FlowLogic<*>>)

    fun <F : FlowLogic<*>> registerInitiatedFlow(initiator: Class<out FlowLogic<*>>, responder: Class<F>)
    fun <F : FlowLogic<*>> registerInitiatedFlow(responder: Class<F>)

    fun getFlowFactoryForInitiatingFlow(initiatedFlowClass: Class<out FlowLogic<*>>): InitiatedFlowFactory<*>?

    fun validateRegistrations()
}

@ThreadSafe
open class NodeFlowManager(flowOverrides: FlowOverrideConfig? = null) : FlowManager {

    private val flowFactories = HashMap<Class<out FlowLogic<*>>, MutableList<RegisteredFlowContainer>>()
    private val flowOverrides = (flowOverrides
            ?: FlowOverrideConfig()).overrides.map { it.initiator to it.responder }.toMutableMap()

    companion object {
        private val log = contextLogger()

    }

    @Synchronized
    override fun getFlowFactoryForInitiatingFlow(initiatedFlowClass: Class<out FlowLogic<*>>): InitiatedFlowFactory<*>? {
        return flowFactories[initiatedFlowClass]?.firstOrNull()?.flowFactory
    }

    @Synchronized
    override fun <F : FlowLogic<*>> registerInitiatedFlow(responder: Class<F>) {
        return registerInitiatedFlow(responder.requireAnnotation<InitiatedBy>().value.java, responder)
    }

    @Synchronized
    override fun <F : FlowLogic<*>> registerInitiatedFlow(initiator: Class<out FlowLogic<*>>, responder: Class<F>) {
        val constructors = responder.declaredConstructors.associateBy { it.parameterTypes.toList() }
        val flowSessionCtor = constructors[listOf(FlowSession::class.java)]?.apply { isAccessible = true }
        val ctor: (FlowSession) -> F = if (flowSessionCtor == null) {
            // Try to fallback to a Party constructor
            val partyCtor = constructors[listOf(Party::class.java)]?.apply { isAccessible = true }
            if (partyCtor == null) {
                throw IllegalArgumentException("$responder must have a constructor accepting a ${FlowSession::class.java.name}")
            } else {
                log.warn("Installing flow factory for $responder accepting a ${Party::class.java.simpleName}, which is deprecated. " +
                        "It should accept a ${FlowSession::class.java.simpleName} instead")
            }
            { flowSession: FlowSession -> uncheckedCast(partyCtor.newInstance(flowSession.counterparty)) }
        } else {
            { flowSession: FlowSession -> uncheckedCast(flowSessionCtor.newInstance(flowSession)) }
        }
        val (version, classWithAnnotation) = initiator.flowVersionAndInitiatingClass
        require(classWithAnnotation == initiator) {
            "${InitiatedBy::class.java.name} must point to ${classWithAnnotation.name} and not ${initiator.name}"
        }
        val flowFactory = InitiatedFlowFactory.CorDapp(version, responder.appName, ctor)
        registerInitiatedFlowFactory(initiator, flowFactory, responder)
        log.info("Registered ${initiator.name} to initiate ${responder.name} (version $version)")
    }

    private fun <F : FlowLogic<*>> registerInitiatedFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>,
                                                                flowFactory: InitiatedFlowFactory<F>,
                                                                initiatedFlowClass: Class<F>?) {

        check(flowFactory !is InitiatedFlowFactory.Core) { "This should only be used for Cordapp flows" }
        val listOfFlowsForInitiator = flowFactories.computeIfAbsent(initiatingFlowClass) { mutableListOf() }
        if (listOfFlowsForInitiator.isNotEmpty() && listOfFlowsForInitiator.first().type == FlowType.CORE) {
            throw IllegalStateException("Attempting to register over an existing platform flow: $initiatingFlowClass")
        }
        synchronized(listOfFlowsForInitiator) {
            val flowToAdd = RegisteredFlowContainer(initiatingFlowClass, initiatedFlowClass, flowFactory, FlowType.CORDAPP)
            val flowWeightComparator = FlowWeightComparator(initiatingFlowClass, flowOverrides)
            listOfFlowsForInitiator.add(flowToAdd)
            listOfFlowsForInitiator.sortWith(flowWeightComparator)
            if (listOfFlowsForInitiator.size > 1) {
                log.warn("Multiple flows are registered for InitiatingFlow: $initiatingFlowClass, currently using: ${listOfFlowsForInitiator.first().initiatedFlowClass}")
            }
        }

    }

    // TODO Harmonise use of these methods - 99% of invocations come from tests.
    @Synchronized
    override fun registerInitiatedCoreFlowFactory(initiatingFlowClass: KClass<out FlowLogic<*>>, initiatedFlowClass: KClass<out FlowLogic<*>>?, flowFactory: (FlowSession) -> FlowLogic<*>) {
        registerInitiatedCoreFlowFactory(initiatingFlowClass, initiatedFlowClass, InitiatedFlowFactory.Core(flowFactory))
    }

    @Synchronized
    override fun registerInitiatedCoreFlowFactory(initiatingFlowClass: KClass<out FlowLogic<*>>, flowFactory: (FlowSession) -> FlowLogic<*>) {
        registerInitiatedCoreFlowFactory(initiatingFlowClass, null, InitiatedFlowFactory.Core(flowFactory))
    }

    @Synchronized
    override fun registerInitiatedCoreFlowFactory(initiatingFlowClass: KClass<out FlowLogic<*>>, initiatedFlowClass: KClass<out FlowLogic<*>>?, flowFactory: InitiatedFlowFactory.Core<FlowLogic<*>>) {
        require(initiatingFlowClass.java.flowVersionAndInitiatingClass.first == 1) {
            "${InitiatingFlow::class.java.name}.version not applicable for core flows; their version is the node's platform version"
        }
        flowFactories.computeIfAbsent(initiatingFlowClass.java) { mutableListOf() }.add(
                RegisteredFlowContainer(
                        initiatingFlowClass.java,
                        initiatedFlowClass?.java,
                        flowFactory,
                        FlowType.CORE)
        )
        log.debug { "Installed core flow ${initiatingFlowClass.java.name}" }
    }

    // To verify the integrity of the current state, it is important that the tip of the responders is a unique weight
    // if there are multiple flows with the same weight as the tip, it means that it is impossible to reliably pick one as the responder
    private fun validateInvariants(toValidate: List<RegisteredFlowContainer>) {
        val currentTip = toValidate.first()
        val flowWeightComparator = FlowWeightComparator(currentTip.initiatingFlowClass, flowOverrides)
        val equalWeightAsCurrentTip = toValidate.map { flowWeightComparator.compare(currentTip, it) to it }.filter { it.first == 0 }.map { it.second }
        if (equalWeightAsCurrentTip.size > 1) {
            val message = "Unable to determine which flow to use when responding to: ${currentTip.initiatingFlowClass.canonicalName}. ${equalWeightAsCurrentTip.map { it.initiatedFlowClass!!.canonicalName }} are all registered with equal weight."
            throw IllegalStateException(message)
        }
    }

    @Synchronized
    override fun validateRegistrations() {
        flowFactories.values.forEach {
            validateInvariants(it)
        }
    }

    private enum class FlowType {
        CORE, CORDAPP
    }

    private data class RegisteredFlowContainer(val initiatingFlowClass: Class<out FlowLogic<*>>,
                                               val initiatedFlowClass: Class<out FlowLogic<*>>?,
                                               val flowFactory: InitiatedFlowFactory<FlowLogic<*>>,
                                               val type: FlowType)

    // this is used to sort the responding flows in order of "importance"
    // the logic is as follows
    // IF responder is a specific lambda (like for notary implementations / testing code) always return that responder
    // ELSE IF responder is present in the overrides list, always return that responder
    // ELSE compare responding flows by their depth from FlowLogic, always return the flow which is most specific (IE, has the most hops to FlowLogic)
    private open class FlowWeightComparator(val initiatingFlowClass: Class<out FlowLogic<*>>, val flowOverrides: Map<String, String>) : Comparator<NodeFlowManager.RegisteredFlowContainer> {

        override fun compare(o1: NodeFlowManager.RegisteredFlowContainer, o2: NodeFlowManager.RegisteredFlowContainer): Int {
            if (o1.initiatedFlowClass == null && o2.initiatedFlowClass != null) {
                return Int.MIN_VALUE
            }
            if (o1.initiatedFlowClass != null && o2.initiatedFlowClass == null) {
                return Int.MAX_VALUE
            }

            if (o1.initiatedFlowClass == null && o2.initiatedFlowClass == null) {
                return 0
            }

            val hopsTo1 = calculateHopsToFlowLogic(initiatingFlowClass, o1.initiatedFlowClass!!)
            val hopsTo2 = calculateHopsToFlowLogic(initiatingFlowClass, o2.initiatedFlowClass!!)
            return hopsTo1.compareTo(hopsTo2) * -1
        }

        private fun calculateHopsToFlowLogic(initiatingFlowClass: Class<out FlowLogic<*>>,
                                             initiatedFlowClass: Class<out FlowLogic<*>>): Int {

            val overriddenClassName = flowOverrides[initiatingFlowClass.canonicalName]
            return if (overriddenClassName == initiatedFlowClass.canonicalName) {
                Int.MAX_VALUE
            } else {
                var currentClass: Class<*> = initiatedFlowClass
                var count = 0
                while (currentClass != FlowLogic::class.java) {
                    currentClass = currentClass.superclass
                    count++
                }
                count;
            }
        }

    }
}

private fun <X, Y> Iterable<Pair<X, Y>>.toMutableMap(): MutableMap<X, Y> {
    return this.toMap(HashMap())
}
