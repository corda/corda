package net.corda.kryohook

import co.paralleluniverse.strands.Strand
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Output
import javassist.ClassPool
import javassist.CtClass
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import java.util.concurrent.ConcurrentHashMap

class KryoHookAgent {
    companion object {
        @JvmStatic
        fun premain(@SuppressWarnings("unused") argumentsString: String?, instrumentation: Instrumentation) {
            Runtime.getRuntime().addShutdownHook(Thread {
                val statsTrees = KryoHook.events.values.flatMap {
                    readTrees(it, 0).second
                }
                val builder = StringBuilder()
                statsTrees.forEach {
                    prettyStatsTree(0, it, builder)
                }
                print(builder.toString())
            })
            instrumentation.addTransformer(KryoHook)
        }
    }
}

fun prettyStatsTree(indent: Int, statsTree: StatsTree, builder: StringBuilder) {
    when (statsTree) {
        is StatsTree.Object -> {
            builder.append(kotlin.CharArray(indent) { ' ' })
            builder.append(statsTree.className)
            builder.append(" ")
            builder.append(statsTree.size)
            builder.append("\n")
            for (child in statsTree.children) {
                prettyStatsTree(indent + 2, child, builder)
            }
        }
    }
}

/**
 * The hook simply records the write() entries and exits together with the output offset at the time of the call.
 * This is recorded in a StrandID -> List<StatsEvent> map.
 *
 * Later we "parse" these lists into a tree.
 */
object KryoHook : ClassFileTransformer {
    val classPool = ClassPool.getDefault()!!

    val hookClassName = javaClass.name!!

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
            val clazz = classPool.makeClass(classfileBuffer.inputStream())
            instrumentClass(clazz)?.toBytecode()
        } catch (throwable: Throwable) {
            println("SOMETHING WENT WRONG")
            throwable.printStackTrace(System.out)
            null
        }
    }

    private fun instrumentClass(clazz: CtClass): CtClass? {
        for (method in clazz.declaredBehaviors) {
            if (method.name == "write") {
                val parameterTypeNames = method.parameterTypes.map { it.name }
                if (parameterTypeNames == listOf("com.esotericsoftware.kryo.Kryo", "com.esotericsoftware.kryo.io.Output", "java.lang.Object")) {
                    if (method.isEmpty) continue
                    println("Instrumenting ${clazz.name}")
                    method.insertBefore("$hookClassName.${this::writeEnter.name}($1, $2, $3);")
                    method.insertAfter("$hookClassName.${this::writeExit.name}($1, $2, $3);")
                    return clazz
                }
            }
        }
        return null
    }

    // StrandID -> StatsEvent map
    val events = ConcurrentHashMap<Long, ArrayList<StatsEvent>>()

    @JvmStatic
    fun writeEnter(@SuppressWarnings("unused") kryo: Kryo, output: Output, obj: Any) {
        events.getOrPut(Strand.currentStrand().id) { ArrayList() }.add(
                StatsEvent.Enter(obj.javaClass.name, output.total())
        )
    }
    @JvmStatic
    fun writeExit(@SuppressWarnings("unused") kryo: Kryo, output: Output, obj: Any) {
        events[Strand.currentStrand().id]!!.add(
                StatsEvent.Exit(obj.javaClass.name, output.total())
        )
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
