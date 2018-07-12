package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.FiberSchedulerTask
import net.corda.core.internal.FlowStateMachine
import kotlin.math.sign

class FlowStateMachineComparator : Comparator<Runnable> {
    override fun compare(o1: Runnable, o2: Runnable): Int {
        return if (o1 is FiberSchedulerTask) {
            if (o2 is FiberSchedulerTask) {
                (((o1.fiber as? FlowStateMachine<*>)?.creationTime
                        ?: Long.MAX_VALUE) - ((o2.fiber as? FlowStateMachine<*>)?.creationTime
                        ?: Long.MAX_VALUE)).sign
            } else {
                -1
            }
        } else if (o2 is FiberSchedulerTask) {
            1
        } else {
            0
        }
    }
}