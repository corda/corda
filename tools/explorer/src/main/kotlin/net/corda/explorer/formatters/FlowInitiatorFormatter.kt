/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.explorer.formatters

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import net.corda.core.flows.FlowInitiator

object FlowInitiatorFormatter : Formatter<FlowInitiator> {
    override fun format(value: FlowInitiator): String {
        return when (value) {
            is FlowInitiator.Scheduled ->  value.scheduledState.ref.toString() // TODO How do we want to format that?
            is FlowInitiator.Shell -> "Shell" // TODO We don't have much information about that user.
            is FlowInitiator.Peer -> PartyNameFormatter.short.format(value.party.name)
            is FlowInitiator.RPC -> value.username
            is FlowInitiator.Service -> value.name
        }
    }

    fun withIcon(value: FlowInitiator): Pair<FontAwesomeIcon, String> {
        val text = format(value)
        return when (value) {
            is FlowInitiator.Scheduled ->  Pair(FontAwesomeIcon.CALENDAR, text)
            is FlowInitiator.Shell -> Pair(FontAwesomeIcon.TERMINAL, text)
            is FlowInitiator.Peer -> Pair(FontAwesomeIcon.GROUP, text)
            is FlowInitiator.RPC -> Pair(FontAwesomeIcon.SHARE, text)
            is FlowInitiator.Service -> Pair(FontAwesomeIcon.SERVER, text)
        }
    }
}
