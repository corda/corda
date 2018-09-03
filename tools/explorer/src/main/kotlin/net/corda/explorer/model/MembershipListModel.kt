package net.corda.explorer.model

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import net.corda.client.jfx.model.NodeMonitorModel
import net.corda.client.jfx.model.observableValue
import net.corda.client.jfx.utils.ChosenList
import net.corda.client.jfx.utils.map
import net.corda.core.identity.AbstractParty
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.sample.businessnetwork.iou.IOUFlow
import net.corda.sample.businessnetwork.membership.flow.ObtainMembershipListContentFlow

class MembershipListModel {
    private val proxy by observableValue(NodeMonitorModel::proxyObservable)
    private val members = proxy.map { it?.cordaRPCOps?.startFlow(::ObtainMembershipListContentFlow, IOUFlow.allowedMembershipName)?.returnValue?.getOrThrow() }
    private val observableValueOfParties = members.map {
        FXCollections.observableList(it?.toList() ?: emptyList<AbstractParty>())
    }
    val allParties: ObservableList<AbstractParty> = ChosenList(observableValueOfParties)
}