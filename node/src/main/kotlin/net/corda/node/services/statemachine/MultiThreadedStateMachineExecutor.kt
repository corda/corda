package net.corda.node.services.statemachine

import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.netty.util.concurrent.FastThreadLocalThread
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class MultiThreadedStateMachineExecutor(poolSize: Int) : ThreadPoolExecutor(poolSize, poolSize,
        0L, TimeUnit.MILLISECONDS,
        PriorityBlockingQueue<Runnable>(poolSize * 4, FlowStateMachineComparator()),
        ThreadFactoryBuilder().setNameFormat("flow-worker").setThreadFactory(::FastThreadLocalThread).build())