package net.corda.tools

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.strands.Strand
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import javassist.ClassPool
import javassist.CtClass
import net.corda.tools.CheckpointAgent.Companion.fiberName
import net.corda.tools.CheckpointAgent.Companion.instrumentClassname
import net.corda.tools.CheckpointAgent.Companion.instrumentType
import net.corda.tools.CheckpointAgent.Companion.maximumSize
import net.corda.tools.CheckpointAgent.Companion.minimumSize
import net.corda.tools.CheckpointAgent.Companion.strand
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class CheckpointAgent {

    // whether to instrument serialized object reads/writes or both
    enum class InstrumentationType {
        WRITE, READ, READ_WRITE
    }

    companion object {
        // custom argument defaults
        val DEFAULT_INSTRUMENT_CLASSNAME = "net.corda.node.services.statemachine.FlowStateMachineImpl"
        val DEFAULT_MINIMUM_SIZE = 8 * 1024
        val DEFAULT_MAXIMUM_SIZE = 1000 * 1024
        val DEFAULT_INSTRUMENT_TYPE = InstrumentationType.READ_WRITE

        // startup arguments
        var instrumentClassname = DEFAULT_INSTRUMENT_CLASSNAME
        var minimumSize = DEFAULT_MINIMUM_SIZE
        var maximumSize = DEFAULT_MAXIMUM_SIZE
        var instrumentType = DEFAULT_INSTRUMENT_TYPE
        var fiberName : UUID? = null

        // Global static variable that can be set programmatically by 3rd party tools/code (eg. CheckpointDumper)
        var strand : Strand? = null
            set(value) {
                println("Instrumenting strand: ${strand?.id}")
                strand = value
            }

        val log by lazy {
            LoggerFactory.getLogger("CheckpointAgent")
        }

        @JvmStatic
        fun premain(argumentsString: String?, instrumentation: Instrumentation) {
            parseArguments(argumentsString)
            instrumentation.addTransformer(CheckpointHook)
        }

        fun parseArguments(argumentsString: String?) {
            argumentsString?.let {
                val nvpList = it.split(",")
                nvpList.forEach {
                    val nvpItem = it.split("=")
                    if (nvpItem.size == 2) {
                        when (nvpItem[0].trim()) {
                            "instrumentClassname" -> instrumentClassname = nvpItem[1]
                            "minimumSize" -> try { minimumSize = nvpItem[1].toInt() } catch (e: NumberFormatException) { println("Invalid value: ${nvpItem[1]}") }
                            "maximumSize" -> try { maximumSize = nvpItem[1].toInt() } catch (e: NumberFormatException) { println("Invalid value: ${nvpItem[1]}") }
                            "instrumentType" -> try { instrumentType = InstrumentationType.valueOf(nvpItem[1].toUpperCase()) } catch (e: Exception) { println("Invalid value: ${nvpItem[1]}") }
                            "fiberName" -> try { fiberName = UUID.fromString(nvpItem[1]) } catch (e: Exception) { println("Invalid value: ${nvpItem[1]}. Must be a valid UUID string matching one or more checkpointed flows") }
                            else -> println("Invalid argument: $nvpItem")
                        }
                    }
                    else println("Missing value for argument: $nvpItem")
                }
            }
            println("Running Checkpoint agent with following arguments: instrumentClassname = $instrumentClassname, instrumentType = $instrumentType, minimumSize = $minimumSize, maximumSize = $maximumSize")
            fiberName?.let { println("Diagnosing checkpoints for fiberName: $fiberName") }
        }
    }
}

/**
 * The hook simply records the write() entries and exits together with the output offset at the time of the call.
 * This is recorded in a StrandID -> List<StatsEvent> map.
 *
 * Later we "parse" these lists into a tree.
 */
object CheckpointHook : ClassFileTransformer {
    val classPool = ClassPool.getDefault()

    val hookClassName = javaClass.name

    override fun transform(
            loader: ClassLoader?,
            className: String,
            classBeingRedefined: Class<*>?,
            protectionDomain: ProtectionDomain?,
            classfileBuffer: ByteArray
    ): ByteArray? {
        if (className.startsWith("java") || className.startsWith("javassist") || className.startsWith("kotlin")) {
            return null
        }
        return try {
            val clazz = classPool.makeClass(ByteArrayInputStream(classfileBuffer))
            instrumentClass(clazz)?.toBytecode()
        } catch (throwable: Throwable) {
            println("SOMETHING WENT WRONG")
            throwable.printStackTrace(System.out)
            null
        }
    }

    private fun instrumentClass(clazz: CtClass): CtClass? {
        for (method in clazz.declaredBehaviors) {
            if (instrumentType == CheckpointAgent.InstrumentationType.READ_WRITE || instrumentType == CheckpointAgent.InstrumentationType.WRITE) {
                if (method.name == "write") {
                    val parameterTypeNames = method.parameterTypes.map { it.name }
                    if (parameterTypeNames == listOf("com.esotericsoftware.kryo.Kryo", "com.esotericsoftware.kryo.io.Output", "java.lang.Object")) {
                        if (method.isEmpty) continue
//                        println("Instrumenting on write: ${clazz.name}")
                        method.insertBefore("$hookClassName.${this::writeEnter.name}($1, $2, $3);")
                        method.insertAfter("$hookClassName.${this::writeExit.name}($1, $2, $3);")
                        return clazz
                    }
                }
            }
            if (instrumentType == CheckpointAgent.InstrumentationType.READ_WRITE || instrumentType == CheckpointAgent.InstrumentationType.READ) {
                if (method.name == "read") {
                    val parameterTypeNames = method.parameterTypes.map { it.name }
                    if (parameterTypeNames == listOf("com.esotericsoftware.kryo.Kryo", "com.esotericsoftware.kryo.io.Input", "java.lang.Class")) {
                        if (method.isEmpty) continue
//                        println("Instrumenting on read: ${clazz.name}")
                        method.insertBefore("$hookClassName.${this::readEnter.name}($1, $2, $3);")
                        method.insertAfter("$hookClassName.${this::readExit.name}($1, $2, $3);")
                        return clazz
                    }
                }
            }
        }
        return null
    }

    // StrandID -> StatsEvent map
    val events = ConcurrentHashMap<Long, Pair<ArrayList<StatsEvent>, AtomicInteger>>()

    @JvmStatic
    fun writeEnter(kryo: Kryo, output: Output, obj: Any) {
        if (obj is Fiber<*>) {
            println("Fiber id ${obj.id}, name ${obj.name}")
            if (fiberName != null && fiberName.toString() != obj.name)
                return
            if (strand != null && strand != Strand.currentStrand())
                return
            val (list, count) = events.getOrPut(Strand.currentStrand().id) { Pair(ArrayList(), AtomicInteger(0)) }
            list.add(StatsEvent.Enter(obj.javaClass.name, output.total()))
            count.incrementAndGet()
        }
    }
    @JvmStatic
    fun writeExit(kryo: Kryo, output: Output, obj: Any) {
        if (obj is Fiber<*>) {
            println("Fiber id ${obj.id}, name ${obj.name}")
            if ((fiberName != null && fiberName.toString() != obj.name))
                return
            if (strand != null && strand != Strand.currentStrand())
                return
            val (list, count) = events[Strand.currentStrand().id]!!
            list.add(StatsEvent.Exit(obj.javaClass.name, output.total()))
            if ((count.decrementAndGet() == 0) &&
                    (obj.javaClass.name == instrumentClassname) &&
                    (output.total() >= minimumSize) &&
                    (output.total() <= maximumSize)) {
                val sb = StringBuilder()
                prettyStatsTree(0, readTree(list, 0).second, sb)
                CheckpointAgent.log.info("$obj\n$sb")
                list.clear()
            }
        }
    }

    @JvmStatic
    fun readEnter(kryo: Kryo, input: Input, clazz: Class<*>) {
        if (strand != null && strand != Strand.currentStrand())
            return
        val (list, count) = events.getOrPut(Strand.currentStrand().id) { Pair(ArrayList(), AtomicInteger(0)) }
//        println("readEnter: $clazz, ${input.total()}")
        list.add(StatsEvent.Enter(clazz.name, input.total()))
        count.incrementAndGet()
    }
    @JvmStatic
    fun readExit(kryo: Kryo, input: Input, clazz: Class<*>) {
        if (strand != null && strand != Strand.currentStrand())
            return
        val (list, count) = events[Strand.currentStrand().id]!!
        list.add(StatsEvent.Exit(clazz.name, input.total()))
        println("readExit: $clazz, ${input.total()}")
        if ((count.decrementAndGet() == 0) &&
                (clazz.name == instrumentClassname) &&
                (input.total() >= minimumSize) &&
                (input.total() <= maximumSize)) {
            val sb = StringBuilder()
            prettyStatsTree(0, readTree(list, 0).second, sb)
            CheckpointAgent.log.info("$clazz\n$sb")
            list.clear()
        }
    }

    private fun prettyStatsTree(indent: Int, statsTree: StatsTree, builder: StringBuilder) {
        when (statsTree) {
            is StatsTree.Object -> {
                builder.append(String.format("%03d:", indent / 2))
                builder.append(CharArray(indent) { ' ' })
                builder.append(statsTree.className)
                builder.append(" ")
                builder.append(String.format("%,d", statsTree.size))
                builder.append("\n")
                for (child in statsTree.children) {
                    prettyStatsTree(indent + 2, child, builder)
                }
            }
        }
    }
}

/**
 * TODO we could add events on entries/exits to field serializers to get more info on what's being serialised.
 */
sealed class StatsEvent {
    data class Enter(val className: String, val offset: Long) : StatsEvent()
    data class Exit(val className: String, val offset: Long) : StatsEvent()
}

/**
 * TODO add Field constructor.
 */
sealed class StatsTree {
    data class Object(
            val className: String,
            val size: Long,
            val children: List<StatsTree>
    ) : StatsTree()
}

fun readTree(events: List<StatsEvent>, index: Int): Pair<Int, StatsTree> {
    val event = events[index]
    when (event) {
        is StatsEvent.Enter -> {
            val (nextIndex, children) = readTrees(events, index + 1)
            val exit = events[nextIndex] as StatsEvent.Exit
            require(event.className == exit.className)
            return Pair(nextIndex + 1, StatsTree.Object(event.className, exit.offset - event.offset, children))
        }
        is StatsEvent.Exit -> {
            throw IllegalStateException("Wasn't expecting Exit")
        }
    }
}

fun readTrees(events: List<StatsEvent>, index: Int): Pair<Int, List<StatsTree>> {
    val trees = ArrayList<StatsTree>()
    var i = index
    while (true) {
        val event = events.getOrNull(i)
        when (event) {
            is StatsEvent.Enter -> {
                val (nextIndex, tree) = readTree(events, i)
                trees.add(tree)
                i = nextIndex
            }
            is StatsEvent.Exit -> {
                return Pair(i, trees)
            }
            null -> {
                return Pair(i, trees)
            }
        }
    }
}