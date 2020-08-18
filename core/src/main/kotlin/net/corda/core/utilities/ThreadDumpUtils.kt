package net.corda.core.utilities

import java.lang.management.LockInfo
import java.lang.management.ManagementFactory
import java.lang.management.ThreadInfo

fun threadDumpAsString(): String {
    val mxBean = ManagementFactory.getThreadMXBean()
    val threadInfos: Array<ThreadInfo?> = mxBean.getThreadInfo(mxBean.allThreadIds, Integer.MAX_VALUE)
    return "Thread Dump:\n" + threadInfos.filterNotNull().joinToString(separator = "\n") { ti -> ti.asString() }
}

/**
 * Inspired by `ThreadInfo.toString`
 *
 * Returns a string representation of this thread info.
 * The format of this string depends on the implementation.
 * The returned string will typically include
 * the [thread name][.getThreadName],
 * the [thread ID][.getThreadId],
 * its [state][.getThreadState],
 * and a [stack trace][.getStackTrace] if any.
 *
 * @return a string representation of this thread info.
 */
fun ThreadInfo.asString(maxFrames : Int = 256): String {
    val sb = StringBuilder("\"" + threadName + "\"" +
            " Id=" + threadId + " " +
            threadState)
    if (lockName != null) {
        sb.append(" on $lockName")
    }
    if (lockOwnerName != null) {
        sb.append(" owned by \"" + lockOwnerName +
                "\" Id=" + lockOwnerId)
    }
    if (isSuspended) {
        sb.append(" (suspended)")
    }
    if (isInNative) {
        sb.append(" (in native)")
    }
    sb.append('\n')
    var i = 0
    while (i < stackTrace.size && i < maxFrames) {
        val ste: StackTraceElement = stackTrace.get(i)
        sb.append("\tat $ste")
        sb.append('\n')
        if (i == 0 && lockInfo != null) {
            when (threadState) {
                Thread.State.BLOCKED -> {
                    sb.append("\t-  blocked on $lockInfo")
                    sb.append('\n')
                }
                Thread.State.WAITING -> {
                    sb.append("\t-  waiting on $lockInfo")
                    sb.append('\n')
                }
                Thread.State.TIMED_WAITING -> {
                    sb.append("\t-  waiting on $lockInfo")
                    sb.append('\n')
                }
                else -> {
                }
            }
        }
        for (mi in lockedMonitors) {
            if (mi.lockedStackDepth == i) {
                sb.append("\t-  locked $mi")
                sb.append('\n')
            }
        }
        i++
    }
    if (i < stackTrace.size) {
        sb.append("\t...")
        sb.append('\n')
    }
    val locks: Array<LockInfo> = getLockedSynchronizers()
    if (locks.isNotEmpty()) {
        sb.append("""
	Number of locked synchronizers = ${locks.size}""")
        sb.append('\n')
        for (li in locks) {
            sb.append("\t- $li")
            sb.append('\n')
        }
    }
    sb.append('\n')
    return sb.toString()
}