import co.paralleluniverse.strands.Strand
import com.sun.tools.attach.VirtualMachine
import net.corda.core.internal.declaredField
import net.corda.core.internal.objectOrNewInstance
import net.corda.tools.CheckpointAgent
import net.corda.tools.CheckpointHook
import org.junit.Test
import sun.misc.VMSupport
import java.lang.management.ManagementFactory
import java.util.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

class CheckpointAgentTest {

    @Test
    fun argumentParsing() {
        resetDefaults()
        CheckpointAgent.parseArguments("instrumentClassname=net.corda.vega.flows.SimmFlow, minimumSize=1000000, instrumentType=READ,checkpointId=c1694658-5df5-4267-abe7-cd8708da682a")
        resetDefaults()
        CheckpointAgent.parseArguments("instrumentClassname=net.corda.vega.flows.SimmFlow, minimumSize=1000000, instrumentType=READ")
        resetDefaults()
        CheckpointAgent.parseArguments("instrumentClassname=net.corda.vega.flows.SimmFlow, minimumSize=1000000")
        resetDefaults()
        CheckpointAgent.parseArguments("instrumentClassname=net.corda.vega.flows.SimmFlow")
        resetDefaults()
        CheckpointAgent.parseArguments("minimumSize=1000000, instrumentType=READ, instrumentClassname=net.corda.vega.flows.SimmFlow")
        resetDefaults()
        CheckpointAgent.parseArguments("minimumSize=1000000,instrumentClassname=net.corda.vega.flows.SimmFlow")
        resetDefaults()
        CheckpointAgent.parseArguments("minimumSize=1000000")
        resetDefaults()
        CheckpointAgent.parseArguments("minimumSize=1000000, instrumentType=READ")
        resetDefaults()
        CheckpointAgent.parseArguments("instrumentType=READ")
        resetDefaults()
        CheckpointAgent.parseArguments("instrumentClassname,minimumSize=1000000")
        resetDefaults()
        CheckpointAgent.parseArguments("minimumSize=abc")
        resetDefaults()
        CheckpointAgent.parseArguments("instrumentType=WRONG")
        resetDefaults()
        CheckpointAgent.parseArguments("instrumentType=read,checkpointId=c1694658-5df5-4267-abe7-cd8708da682a")
        resetDefaults()
        CheckpointAgent.parseArguments("blablabla=net.corda.vega.flows.SimmFlow, blablabla=1000000, instrumentType=WRITE")
    }

    private fun resetDefaults() {
        CheckpointAgent.instrumentClassname = CheckpointAgent.DEFAULT_INSTRUMENT_CLASSNAME
        CheckpointAgent.minimumSize = CheckpointAgent.DEFAULT_MINIMUM_SIZE
        CheckpointAgent.maximumSize = CheckpointAgent.DEFAULT_MAXIMUM_SIZE
        CheckpointAgent.instrumentType = CheckpointAgent.DEFAULT_INSTRUMENT_TYPE
    }

    @Test
    fun testReflection() {
        try {
            println("CheckpointAgent (C)")
            val checkpointAgent = Class.forName("net.corda.tools.CheckpointAgent").kotlin
            println(checkpointAgent.memberProperties)

            println("CheckpointHook (O)")
            val checkpointHook = Class.forName("net.corda.tools.CheckpointHook").kotlin
            checkpointHook.memberProperties.forEach { println(it) }

            val fieldStrandId = checkpointHook.memberProperties.first { it.name == "strand" }
            println("Field name: ${fieldStrandId.javaField}")

            fieldStrandId.javaField?.isAccessible = true
            println("Field value: ${fieldStrandId.javaField?.get(checkpointHook)}")
            fieldStrandId.javaField?.set(checkpointHook, Strand.of(Thread.currentThread()))
            println("Field value: ${fieldStrandId.javaField?.get(checkpointHook)}")
        }
        catch (e: Exception) {
            println(e)
        }
    }

    @Test
    fun testReflectionUtils() {
        try {
            val instance = CheckpointHook::class.objectOrNewInstance()
            println(instance.checkpointId)
            instance.checkpointId = UUID.randomUUID()
            println(instance.checkpointId)
        }
        catch (e: Exception) {
            println(e)
        }
    }

    @Test
    fun testReflectionUtilsDynamically() {
        try {
            val checkpointHook = Class.forName("net.corda.tools.CheckpointHook").kotlin
            val instance = checkpointHook.objectOrNewInstance()
            val strand = instance.declaredField<Strand>(instance.javaClass, "strand")
            println("${strand.name} = ${strand.value}")
            strand.value = Strand.of(Thread.currentThread())
            println("${strand.name} = ${strand.value}")
        }
        catch (e: Exception) {
            println(e)
        }
    }

    @Test
    fun testAgentRunningInVM() {
        val agentProperties = VMSupport.getAgentProperties()
        val isRunning = agentProperties.values.any { value ->
            (value is String && value.contains("checkpoint-agent.jar"))
        }
        if (isRunning) {
            println("\nAgent is running")
        }
        else {
            println("\nAgent is not running")
        }
    }

    @Test
    fun testAgentRunningInVMwithAttach() {
        val vms = VirtualMachine.list()
        vms.forEach { println(it) }

        val pid = ManagementFactory.getRuntimeMXBean().name.substringBefore("@")
        println(pid)

        val vm = VirtualMachine.attach(pid)
        if (vm.agentProperties.values.any { value ->
                    (value is String && value.contains("checkpoint-agent.jar"))
                })
            println("\nAgent is running")
        else
            println("\nAgent is not running")
    }

}