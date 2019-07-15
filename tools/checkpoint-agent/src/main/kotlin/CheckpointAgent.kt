package net.corda.tools

import co.paralleluniverse.strands.Strand
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.FieldSerializer
import javassist.ClassPool
import javassist.CtClass
import net.corda.core.internal.ThreadBox
import net.corda.tools.CheckpointAgent.Companion.instrumentClassname
import net.corda.tools.CheckpointAgent.Companion.instrumentType
import net.corda.tools.CheckpointAgent.Companion.log
import net.corda.tools.CheckpointAgent.Companion.maximumSize
import net.corda.tools.CheckpointAgent.Companion.minimumSize
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.LinkedHashMap

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
                            else -> println("Invalid argument: $nvpItem")
                        }
                    }
                    else println("Missing value for argument: $nvpItem")
                }
            }
            println("Running Checkpoint agent with following arguments: instrumentClassname = $instrumentClassname, instrumentType = $instrumentType, minimumSize = $minimumSize, maximumSize = $maximumSize\n")
        }
    }
}

/**
 * The hook simply records the read() and write() entries and exits together with the output offset at the time of the call.
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
                        log.debug("Instrumenting on write: ${clazz.name}")
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
                        log.debug("Instrumenting on read: ${clazz.name}")
                        method.insertBefore("$hookClassName.${this::readEnter.name}($1, $2, $3);")
                        method.insertAfter("$hookClassName.${this::readExit.name}($1, $2, $3);")
                        return clazz
                    }
                    else if (parameterTypeNames == listOf("com.esotericsoftware.kryo.io.Input", "java.lang.Object")) {
                        if (method.isEmpty) continue
                        log.debug("Instrumenting on field read: ${clazz.name}")
                        method.insertBefore("$hookClassName.${this::readFieldEnter.name}($1, $2, (java.lang.Object)this);")
                        method.insertAfter("$hookClassName.${this::readFieldExit.name}($1, $2, (java.lang.Object)this);")
                        return clazz
                    }
                }
            }
        }
        return null
    }

    // StrandID -> StatsEvent map
    val events = ConcurrentHashMap<Long, Pair<ArrayList<StatsEvent>, AtomicInteger>>()

    // Global static variable that can be set programmatically by 3rd party tools/code (eg. CheckpointDumper)
    // Only relevant to READ operations (as the checkpoint id must have previously been generated)
    var checkpointId : UUID? = null
        set(value) {
            if (value != null)
                log.debug("Diagnosing checkpoint id: $value")
            mutex.locked {
                field = value
            }
        }
    private val mutex = ThreadBox(checkpointId)

    @JvmStatic
    fun readFieldEnter(input: Input, obj: Any?, that: Object) {
        if (that is FieldSerializer.CachedField<*>) {
            val (list, count) = events.getOrPut(Strand.currentStrand().id) { Pair(ArrayList(), AtomicInteger(0)) }
            if (that.`class`.name.endsWith("ObjectField") &&
                    that.field.type != java.lang.String::class.java &&
                    !that.field.type.isArray) {
                log.debug("readFieldEnter object: ${that.field.name}:${that.field.type}")
                list.add(StatsEvent.EnterField(that.field.name, that.field.type))
            }
            count.incrementAndGet()
        }
    }

    @JvmStatic
    fun readFieldExit(input: Input, obj: Any?, that: Object) {
        if (that is FieldSerializer.CachedField<*>) {
            val (list, count) = events.getOrPut(Strand.currentStrand().id) { Pair(ArrayList(), AtomicInteger(0)) }
            if (!that.`class`.name.endsWith("ObjectField") ||
                    that.field.type.isArray ||
                    that.field.type == java.lang.String::class.java) {
                val basicTypeValue =
                    if (that.field.type == ByteArray::class.java) {
                        val arrayValue = that.field.get(obj) as ByteArray
                        log.debug("readFieldExit byte array: ${that.field.name}:${that.field.type} = ${byteArrayToHex(arrayValue)}")
                        byteArrayToHex(arrayValue)
                    }
                    else if (that.field.type == CharArray::class.java) {
                        val arrayValue = that.field.get(obj) as CharArray
                        log.debug("readFieldExit char array: ${that.field.name}:${that.field.type} = ${arrayValue.joinToString("")}")
                        arrayValue.joinToString("")
                    }
                    else if (that.field.type.isPrimitive){
                        val value = that.field.get(obj)
                        log.debug("readFieldExit primitive: ${that.field.name}:${that.field.type} = $value")
                        value
                    }
                    else {
                        null
                    }
                if (basicTypeValue != null) {
                    log.debug("readFieldExit basic type value: ${that.field.name}:${that.field.type} = $basicTypeValue")
                    list.add(StatsEvent.BasicTypeField(that.field.name, that.field.type, basicTypeValue))
                }
            }
            else {
                list.add(StatsEvent.ObjectField(that.field.name, that.field.type))
            }
            count.incrementAndGet()
        }
    }

    // https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
    private fun byteArrayToHex(a: ByteArray): String {
        val sb = StringBuilder(a.size * 2)
        for (b in a)
            sb.append(String.format("%02x", b))
        return sb.toString()
    }

    @JvmStatic
    fun readEnter(kryo: Kryo, input: Input, clazz: Class<*>) {
        val (list, count) = events.getOrPut(Strand.currentStrand().id) { Pair(ArrayList(), AtomicInteger(0)) }
        log.debug("readEnter: adding event for clazz: ${clazz.name} (strandId: ${Strand.currentStrand().id})")
        list.add(StatsEvent.Enter(clazz.name, input.total()))
        count.incrementAndGet()
    }

    @JvmStatic
    fun readExit(kryo: Kryo, input: Input, clazz: Class<*>) {
        val (list, count) = events[Strand.currentStrand().id]!!
        list.add(StatsEvent.Exit(clazz.name, input.total()))
        log.debug("readExit: strandId[${Strand.currentStrand().id}], eventCount[$count]")
        if (count.decrementAndGet() == 0) {
            if ((checkpointId != null) ||
                    ((clazz.name == instrumentClassname) &&
                            (input.total() >= minimumSize) &&
                            (input.total() <= maximumSize))) {
                val sb = StringBuilder()
                if (checkpointId != null)
                    sb.append("Checkpoint id: $checkpointId\n")
                prettyStatsTree(0, "", readTree(list, 0).second, sb)

                log.info("[READ] $clazz\n$sb")
                checkpointId = null
            }
            log.debug("readExit: clearing event for clazz: $clazz (strandId: ${Strand.currentStrand().id})")
            events.remove(Strand.currentStrand().id)
        }
    }

    @JvmStatic
    fun writeEnter(kryo: Kryo, output: Output, obj: Any) {
        val (list, count) = events.getOrPut(Strand.currentStrand().id) { Pair(ArrayList(), AtomicInteger(0)) }
        log.debug("writeEnter: adding event for clazz: ${obj.javaClass.name} (strandId: ${Strand.currentStrand().id})")
        list.add(StatsEvent.Enter(obj.javaClass.name, output.total()))
        count.incrementAndGet()
    }

    @JvmStatic
    fun writeExit(kryo: Kryo, output: Output, obj: Any) {
        val (list, count) = events[Strand.currentStrand().id]!!
        list.add(StatsEvent.Exit(obj.javaClass.name, output.total()))
        if (count.decrementAndGet() == 0) {
            // always log diagnostics for explicit checkpoint ids (eg. set dumpCheckpoints)
            if ((checkpointId != null) ||
                ((obj.javaClass.name == instrumentClassname) &&
                (output.total() >= minimumSize) &&
                (output.total() <= maximumSize))) {
                val sb = StringBuilder()
                prettyStatsTree(0, "", readTree(list, 0).second, sb)
                log.info("[WRITE] $obj\n$sb")
                checkpointId = null
            }
            log.debug("writeExit: clearing event for clazz: ${obj.javaClass.name} (strandId: ${Strand.currentStrand().id})")
            events.remove(Strand.currentStrand().id)
        }
    }

    private fun prettyStatsTree(indent: Int, field: String, statsTree: StatsTree, builder: StringBuilder) {
        when (statsTree) {
            is StatsTree.Object -> {
                builder.append(String.format("%03d:", indent / 2))
                builder.append(CharArray(indent) { ' ' })
                builder.append(" $field ")
                builder.append(statsTree.className)
                builder.append(" ")
                builder.append(String.format("%,d", statsTree.size))
                builder.append("\n")
                for (child in statsTree.children) {
                    prettyStatsTree(indent + 2, child.key, child.value, builder)
                }
            }
            is StatsTree.Primitive -> {
                builder.append(String.format("%03d:", indent / 2))
                builder.append(CharArray(indent) { ' ' })
                builder.append(" $field ")
                builder.append("${statsTree.value}")
                builder.append("\n")
            }
        }
    }
}

sealed class StatsEvent {
    data class Enter(val className: String, val offset: Long) : StatsEvent()
    data class Exit(val className: String, val offset: Long) : StatsEvent()
    data class BasicTypeField(val fieldName: String, val fieldType: Class<*>?, val fieldValue: Any?) : StatsEvent()
    data class EnterField(val fieldName: String, val fieldType: Class<*>?) : StatsEvent()
    data class ObjectField(val fieldName: String, val fieldType: Class<*>?) : StatsEvent()
}

sealed class StatsTree {
    data class Object(
            val className: String,
            val size: Long,
            val children: Map<String,StatsTree>
    ) : StatsTree()

    data class Primitive(
            val value: Any?
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
        else -> {
            throw IllegalStateException("Wasn't expecting event: $event")
        }
    }
}

fun readTrees(events: List<StatsEvent>, index: Int): Pair<Int, Map<String,StatsTree>> {
    val trees = LinkedHashMap<String,StatsTree>()
    var i = index
    var namedTree: StatsTree? = null
    while (true) {
        val event = events.getOrNull(i)
        when (event) {
            is StatsEvent.Enter -> {
                val (nextIndex, tree) = readTree(events, i)
                namedTree = tree
                i = nextIndex
            }
            is StatsEvent.EnterField -> {
                i++
            }
            is StatsEvent.Exit -> {
                return Pair(i, trees)
            }
            is StatsEvent.BasicTypeField -> {
                trees["${event.fieldName}:${event.fieldType}"] = StatsTree.Primitive(event.fieldValue)
                i++
            }
            is StatsEvent.ObjectField -> {
                if (namedTree != null)
                    trees["${event.fieldName}:${event.fieldType}"] = namedTree
                i++
            }
            null -> {
                return Pair(i, trees)
            }
        }
    }
}