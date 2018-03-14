/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bridge

import net.corda.bridge.services.api.ServiceStateSupport
import net.corda.bridge.services.util.ServiceStateCombiner
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.utilities.contextLogger
import org.junit.Assert.assertEquals
import org.junit.Test
import rx.Observable

class ServiceStateTest {
    interface ServiceA : ServiceStateSupport {
        fun changeStatus(status: Boolean)
    }


    class ServiceAImpl(private val stateSupport: ServiceStateHelper = ServiceStateHelper(log)) : ServiceA, ServiceStateSupport by stateSupport {
        companion object {
            val log = contextLogger()
        }

        override fun changeStatus(status: Boolean) {
            stateSupport.active = status
        }
    }

    interface ServiceB : ServiceStateSupport {
        fun changeStatus(status: Boolean)
    }


    class ServiceBImpl(private val stateSupport: ServiceStateHelper = ServiceStateHelper(log)) : ServiceB, ServiceStateSupport by stateSupport {
        companion object {
            val log = contextLogger()
        }

        override fun changeStatus(status: Boolean) {
            stateSupport.active = status
        }
    }

    interface ServiceC : ServiceStateSupport {
    }


    class ServiceCImpl(servA: ServiceA, servB: ServiceB) : ServiceC {
        private val combiner = ServiceStateCombiner(listOf(servA, servB))

        override val active: Boolean
            get() = combiner.active

        override val activeChange: Observable<Boolean>
            get() = combiner.activeChange
    }

    @Test
    fun `Test state helper`() {
        val servA = ServiceAImpl()
        var upA = 0
        var downA = 0
        val subsA = servA.activeChange.subscribe {
            if (it) ++upA else ++downA
        }
        assertEquals(0, upA)
        assertEquals(1, downA)

        servA.changeStatus(true)
        assertEquals(true, servA.active)
        assertEquals(1, upA)
        assertEquals(1, downA)

        servA.changeStatus(true)
        assertEquals(true, servA.active)
        assertEquals(1, upA)
        assertEquals(1, downA)

        servA.changeStatus(false)
        assertEquals(false, servA.active)
        assertEquals(1, upA)
        assertEquals(2, downA)

        servA.changeStatus(false)
        assertEquals(false, servA.active)
        assertEquals(1, upA)
        assertEquals(2, downA)

        // Should stop alerting, but keep functioning after unsubscribe
        subsA.unsubscribe()

        servA.changeStatus(true)
        assertEquals(true, servA.active)
        assertEquals(1, upA)
        assertEquals(2, downA)

        servA.changeStatus(false)
        assertEquals(false, servA.active)
        assertEquals(1, upA)
        assertEquals(2, downA)

    }

    @Test
    fun `Test basic domino behaviour of combiner`() {
        val servA = ServiceAImpl()
        val servB = ServiceBImpl()
        val servC = ServiceCImpl(servA, servB)
        var upA = 0
        var downA = 0
        var upB = 0
        var downB = 0
        var upC = 0
        var downC = 0
        val subsA = servA.activeChange.subscribe {
            if (it) ++upA else ++downA
        }
        val subsB = servB.activeChange.subscribe {
            if (it) ++upB else ++downB
        }
        val subsC = servC.activeChange.subscribe {
            if (it) ++upC else ++downC
        }
        // Get one automatic down event at subscribe
        assertEquals(false, servA.active)
        assertEquals(false, servB.active)
        assertEquals(false, servC.active)
        assertEquals(0, upA)
        assertEquals(1, downA)
        assertEquals(0, upB)
        assertEquals(1, downB)
        assertEquals(0, upC)
        assertEquals(1, downC)

        // Rest of sequence should only signal on change and C should come up if A.active && B.active else it is false
        servA.changeStatus(true)
        assertEquals(true, servA.active)
        assertEquals(false, servB.active)
        assertEquals(false, servC.active)
        assertEquals(1, upA)
        assertEquals(1, downA)
        assertEquals(0, upB)
        assertEquals(1, downB)
        assertEquals(0, upC)
        assertEquals(1, downC)

        servB.changeStatus(false)
        assertEquals(true, servA.active)
        assertEquals(false, servB.active)
        assertEquals(false, servC.active)
        assertEquals(1, upA)
        assertEquals(1, downA)
        assertEquals(0, upB)
        assertEquals(1, downB)
        assertEquals(0, upC)
        assertEquals(1, downC)

        servB.changeStatus(true)
        assertEquals(true, servA.active)
        assertEquals(true, servB.active)
        assertEquals(true, servC.active)
        assertEquals(1, upA)
        assertEquals(1, downA)
        assertEquals(1, upB)
        assertEquals(1, downB)
        assertEquals(1, upC)
        assertEquals(1, downC)

        servA.changeStatus(false)
        assertEquals(false, servA.active)
        assertEquals(true, servB.active)
        assertEquals(false, servC.active)
        assertEquals(1, upA)
        assertEquals(2, downA)
        assertEquals(1, upB)
        assertEquals(1, downB)
        assertEquals(1, upC)
        assertEquals(2, downC)

        servB.changeStatus(false)
        assertEquals(false, servA.active)
        assertEquals(false, servB.active)
        assertEquals(false, servC.active)
        assertEquals(1, upA)
        assertEquals(2, downA)
        assertEquals(1, upB)
        assertEquals(2, downB)
        assertEquals(1, upC)
        assertEquals(2, downC)

        servB.changeStatus(true)
        assertEquals(false, servA.active)
        assertEquals(true, servB.active)
        assertEquals(false, servC.active)
        assertEquals(1, upA)
        assertEquals(2, downA)
        assertEquals(2, upB)
        assertEquals(2, downB)
        assertEquals(1, upC)
        assertEquals(2, downC)

        servA.changeStatus(true)
        assertEquals(true, servA.active)
        assertEquals(true, servB.active)
        assertEquals(true, servC.active)
        assertEquals(2, upA)
        assertEquals(2, downA)
        assertEquals(2, upB)
        assertEquals(2, downB)
        assertEquals(2, upC)
        assertEquals(2, downC)

        subsC.unsubscribe()
        subsA.unsubscribe()
        subsB.unsubscribe()

        servA.changeStatus(false)
        assertEquals(false, servA.active)
        assertEquals(true, servB.active)
        assertEquals(false, servC.active)
        assertEquals(2, upA)
        assertEquals(2, downA)
        assertEquals(2, upB)
        assertEquals(2, downB)
        assertEquals(2, upC)
        assertEquals(2, downC)

    }
}