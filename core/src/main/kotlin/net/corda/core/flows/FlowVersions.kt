package net.corda.core.flows

import java.lang.annotation.Inherited
import java.util.*
import net.corda.core.crypto.Party
import net.corda.core.serialization.CordaSerializable

val versionRegex = Regex("^(\\d+\\.\\d+)$") // Format: Major.Minor

// Get rid of number at the end of class name if present.
fun String.stripNumber(): String {
    return dropLastWhile { it.isDigit() }
}

//TODO Do want to have inherited annotation of sublcasses? It makes life easier when testing + defaults, but it's hard to debug when you don't annotate your flow.
/**
 * Annotation used for flow to indicate it's general flow it belongs to (for example, if you have Sender-Receiver classes)
 * that belong to one general flow "SendReceive" - it would be the name of flow, and this name would be registered as
 * flow initiator on the node, also it can be advertised in [NetworkMapService].
 * @param version version of this [FlowLogic] class
 * @param genericName name of the flow that [FlowLogic] belongs to
 * @param preference what versions we accept when negotiating connection
 * @param advertise do we want to advertise this [FlowLogic] in [NetworkMapService]
 */
// Retention is default true at runtime.
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
@Inherited
annotation class FlowVersion(val version: String, val preference: Array<String> = arrayOf("")) //TODO min max version, TODO default emptyArray
// But... it's highly impossible that we will keep lots of flow versions on the node.

fun majorVersionMatch(version1: String, version2: String): Boolean {
    require(version1.matches(versionRegex) && version2.matches(versionRegex)) { "Incorrect version formats" }
    return (version1.split(".")[0] == version2.split(".")[0])
}

// I needed whole flow metadata stored, later it can be useful with encoding flow backward compatibility etc.
// Problem with flows on our platform is that on incoming connection we can register as an initiator whatever we want.
// With that solution it's easier to hold some data and have control over that process.
// Used if we have more than one flow version on the node, we can specify how it will be advertised.
/**
 * Way of registering multiple versions of [FlowLogic] at once. Also makes specifying of flow metadata much easier.
 * That metadata is later used in [NodeInfo] for advertising purposes.
 * @param preferred version of this general flow
 * @param deprecated other versions we support on the node
 * @param genericFlowName name of the flow that [FlowLogic]s in that set belong to
 * @param toAdvertise do we want to advertise this flows in [NetworkMapService]
 * TODO more documentation
 */
interface FlowFactory {
    val preferred: String // TODO defaults
    val deprecated: Array<String>
    val toAdvertise: Boolean // We may wish not to advertise that flow in NMS but still register it. For example if we want to have private communication between nodes.
    val genericFlowName: String
    fun getFlow(version: String, party: Party): FlowLogic<*>?
}

// Used when registering flow initiators
class FlowVersionInfo(
        val genericName: String,
        val version: String, // With default genericFlowName/ highest version
        val preference: Array<String> = emptyArray(),
        val advertise: Boolean = true // Do we want to advertise this flow.
) {
    init {
        require(toFullList().all{ it.matches(versionRegex) }) { "Some of version information doesn't match format: major.minor for flow: $genericName" }
    }

    companion object {
        fun getVersionAnnotation(markerClass: Class<*>): FlowVersionInfo {
            val versionAnn = markerClass.annotations.find { it is FlowVersion } as? FlowVersion // It has to be Class not KClass because otherwise inherited annotations won't be seen.
            versionAnn ?: throw IllegalArgumentException("Flow without version annotation: ${markerClass}")
            val flowVersion = versionAnn.version
            // We assume that markerClass has a number in name at the end.
            val flowName = markerClass.name.stripNumber()
            if(flowName.isEmpty()) throw IllegalArgumentException("Invalid flow marker class name: ${markerClass}")
            // TODO Is there a way of handling default empty array in Kotlin annotations?
            val preference = if (Arrays.equals(versionAnn.preference, arrayOf(""))) arrayOf<String>() else versionAnn.preference
            return FlowVersionInfo(flowName, flowVersion, preference)
        }

    }

    fun toFullList(): Array<String> {
        return preference + version
    }
}

// Flow versions that will be advertised through NetworkMapService.
@CordaSerializable
data class AdvertisedFlow(
        val genericFlowName: String,
        val preferred: String, // TODO Default highest version.
        val deprecated: Array<String> = emptyArray() // Flows we still support on the node as a new incoming communication.
) {
    init {
        require(toList().all{ it.matches(versionRegex) }) { "Some of version information doesn't match format: major.minor" }
    }

    fun toList(): Array<String> {
        return deprecated + preferred
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as AdvertisedFlow
        if (genericFlowName != other.genericFlowName) return false
        if (toList().sorted() != other.toList().sorted()) return false // Force ordering.
        return true
    }

    override fun hashCode(): Int = Objects.hash(genericFlowName, preferred, deprecated.sorted())
}
