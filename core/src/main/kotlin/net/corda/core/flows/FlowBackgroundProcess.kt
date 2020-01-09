package net.corda.core.flows

import net.corda.core.internal.FlowAsyncOperation
import net.corda.core.node.ServiceHub

interface FlowBackgroundProcess<R : Any> : FlowAsyncOperation<R>

internal abstract class FlowBackgroundProcessImpl<R : Any>(val serviceHub: ServiceHub) : FlowBackgroundProcess<R>