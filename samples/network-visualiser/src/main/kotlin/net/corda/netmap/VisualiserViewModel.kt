package net.corda.netmap

import javafx.animation.*
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.scene.shape.Circle
import javafx.scene.shape.Line
import javafx.util.Duration
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.utils.ScreenCoordinate
import net.corda.netmap.simulation.IRSSimulation
import net.corda.netmap.simulation.place
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.internal.InternalMockNetwork
import java.util.*

class VisualiserViewModel {
    enum class Style {
        MAP, CIRCLE;

        override fun toString(): String {
            return name.toLowerCase().capitalize()
        }
    }

    inner class NodeWidget(val node: InternalMockNetwork.MockNode, val innerDot: Circle, val outerDot: Circle, val longPulseDot: Circle,
                           val pulseAnim: Animation, val longPulseAnim: Animation,
                           val nameLabel: Label, val statusLabel: Label) {
        fun position(nodeCoords: (node: InternalMockNetwork.MockNode) -> ScreenCoordinate) {
            val (x, y) = nodeCoords(node)
            innerDot.centerX = x
            innerDot.centerY = y
            outerDot.centerX = x
            outerDot.centerY = y
            longPulseDot.centerX = x
            longPulseDot.centerY = y
            (nameLabel.parent as StackPane).relocate(x - 270.0, y - 10.0)
            (statusLabel.parent as StackPane).relocate(x + 20.0, y - 10.0)
        }
    }

    internal lateinit var view: VisualiserView
    var presentationMode: Boolean = false
    var simulation = IRSSimulation(true, false, null)   // Manually pumped.

    val trackerBoxes = HashMap<ProgressTracker, TrackerWidget>()
    val doneTrackers = ArrayList<ProgressTracker>()
    val nodesToWidgets = HashMap<InternalMockNetwork.MockNode, NodeWidget>()

    var bankCount: Int = 0
    var serviceCount: Int = 0

    var stepDuration: Duration = Duration.millis(500.0)
    var runningPausedState: NetworkMapVisualiser.RunningPausedState = NetworkMapVisualiser.RunningPausedState.Paused()

    var displayStyle: Style = Style.MAP
        set(value) {
            field = value
            view.updateDisplayStyle(value)
            repositionNodes()
            view.bindHideButtonPosition()
        }

    fun repositionNodes() {
        for ((index, bank) in simulation.banks.withIndex()) {
            nodesToWidgets[bank]!!.position(when (displayStyle) {
                Style.MAP -> { node -> nodeMapCoords(node) }
                Style.CIRCLE -> { _ -> nodeCircleCoords(NetworkMapVisualiser.NodeType.BANK, index) }
            })
        }
        for ((index, serviceProvider) in (simulation.serviceProviders + simulation.regulators).withIndex()) {
            nodesToWidgets[serviceProvider]!!.position(when (displayStyle) {
                Style.MAP -> { node -> nodeMapCoords(node) }
                Style.CIRCLE -> { _ -> nodeCircleCoords(NetworkMapVisualiser.NodeType.SERVICE, index) }
            })
        }
    }

    fun nodeMapCoords(node: InternalMockNetwork.MockNode): ScreenCoordinate {
        // For an image of the whole world, we use:
        // return node.place.coordinate.project(mapImage.fitWidth, mapImage.fitHeight, 85.0511, -85.0511, -180.0, 180.0)

        // For Europe, our bounds are: (lng,lat)
        // bottom left: -23.2031,29.8406
        // top right: 33.0469,64.3209
        try {
            return node.place.coordinate.project(view.mapImage.fitWidth, view.mapImage.fitHeight, 64.3209, 29.8406, -23.2031, 33.0469)
        } catch (e: Exception) {
            throw Exception("Cannot project ${node.started!!.info.singleIdentity()}", e)
        }
    }

    fun nodeCircleCoords(type: NetworkMapVisualiser.NodeType, index: Int): ScreenCoordinate {
        val stepRad: Double = when (type) {
            NetworkMapVisualiser.NodeType.BANK -> 2 * Math.PI / bankCount
            NetworkMapVisualiser.NodeType.SERVICE -> (2 * Math.PI / serviceCount)
        }
        val tangentRad: Double = stepRad * index + when (type) {
            NetworkMapVisualiser.NodeType.BANK -> 0.0
            NetworkMapVisualiser.NodeType.SERVICE -> Math.PI / 2
        }
        val radius = when (type) {
            NetworkMapVisualiser.NodeType.BANK -> Math.min(view.stageWidth, view.stageHeight) / 3.5
            NetworkMapVisualiser.NodeType.SERVICE -> Math.min(view.stageWidth, view.stageHeight) / 8
        }
        val xOffset = -220
        val yOffset = -80
        val circleX = view.stageWidth / 2 + xOffset
        val circleY = view.stageHeight / 2 + yOffset
        val x: Double = radius * Math.cos(tangentRad) + circleX
        val y: Double = radius * Math.sin(tangentRad) + circleY
        return ScreenCoordinate(x, y)
    }

    fun createNodes() {
        bankCount = simulation.banks.size
        serviceCount = simulation.serviceProviders.size + simulation.regulators.size
        for ((index, bank) in simulation.banks.withIndex()) {
            nodesToWidgets[bank] = makeNodeWidget(bank, "bank", bank.configuration.myLegalName, NetworkMapVisualiser.NodeType.BANK, index)
        }
        for ((index, service) in simulation.serviceProviders.withIndex()) {
            nodesToWidgets[service] = makeNodeWidget(service, "network-service", service.configuration.myLegalName, NetworkMapVisualiser.NodeType.SERVICE, index)
        }
        for ((index, service) in simulation.regulators.withIndex()) {
            nodesToWidgets[service] = makeNodeWidget(service, "regulator", service.configuration.myLegalName, NetworkMapVisualiser.NodeType.SERVICE, index + simulation.serviceProviders.size)
        }
    }

    fun makeNodeWidget(forNode: InternalMockNetwork.MockNode, type: String, label: CordaX500Name = CordaX500Name(organisation = "Bank of Bologna", locality = "Bologna", country = "IT"),
                       nodeType: NetworkMapVisualiser.NodeType, index: Int): NodeWidget {
        fun emitRadarPulse(initialRadius: Double, targetRadius: Double, duration: Double): Pair<Circle, Animation> {
            val pulse = Circle(initialRadius).apply {
                styleClass += "node-$type"
                styleClass += "node-circle-pulse"
            }
            val animation = Timeline(
                    KeyFrame(Duration.seconds(0.0),
                            pulse.radiusProperty().keyValue(initialRadius),
                            pulse.opacityProperty().keyValue(1.0)
                    ),
                    KeyFrame(Duration.seconds(duration),
                            pulse.radiusProperty().keyValue(targetRadius),
                            pulse.opacityProperty().keyValue(0.0)
                    )
            )
            return Pair(pulse, animation)
        }

        val innerDot = Circle(10.0).apply {
            styleClass += "node-$type"
            styleClass += "node-circle-inner"
        }
        val (outerDot, pulseAnim) = emitRadarPulse(10.0, 50.0, 0.45)
        val (longPulseOuterDot, longPulseAnim) = emitRadarPulse(10.0, 100.0, 1.45)
        view.root.children += outerDot
        view.root.children += longPulseOuterDot
        view.root.children += innerDot

        val nameLabel = Label(label.organisation)
        val nameLabelRect = StackPane(nameLabel).apply {
            styleClass += "node-label"
            alignment = Pos.CENTER_RIGHT
            // This magic min width depends on the longest label of all nodes we may have, which we aren't calculating.
            // TODO: Dynamically adjust it depending on the longest label to display.
            minWidth = 250.0
        }
        view.root.children += nameLabelRect

        val statusLabel = Label("")
        val statusLabelRect = StackPane(statusLabel).apply { styleClass += "node-status-label" }
        view.root.children += statusLabelRect

        val widget = NodeWidget(forNode, innerDot, outerDot, longPulseOuterDot, pulseAnim, longPulseAnim, nameLabel, statusLabel)
        when (displayStyle) {
            Style.CIRCLE -> widget.position { _ -> nodeCircleCoords(nodeType, index) }
            Style.MAP -> widget.position { node -> nodeMapCoords(node) }
        }
        return widget
    }

    fun fireBulletBetweenNodes(senderNode: InternalMockNetwork.MockNode, destNode: InternalMockNetwork.MockNode, startType: String, endType: String) {
        val sx = nodesToWidgets[senderNode]!!.innerDot.centerX
        val sy = nodesToWidgets[senderNode]!!.innerDot.centerY
        val dx = nodesToWidgets[destNode]!!.innerDot.centerX
        val dy = nodesToWidgets[destNode]!!.innerDot.centerY

        val bullet = Circle(3.0)
        bullet.styleClass += "bullet"
        bullet.styleClass += "connection-$startType-to-$endType"
        with(TranslateTransition(stepDuration, bullet)) {
            fromX = sx
            fromY = sy
            toX = dx
            toY = dy
            setOnFinished {
                // For some reason removing/adding the bullet nodes causes an annoying 1px shift in the map view, so
                // to avoid visual distraction we just deliberately leak the bullet node here. Obviously this is a
                // memory leak that would break long term usage.
                //
                // TODO: Find root cause and fix.
                //
                // root.children.remove(bullet)
                bullet.isVisible = false
            }
            play()
        }

        val line = Line(sx, sy, dx, dy).apply { styleClass += "message-line" }
        // Fade in quick, then fade out slow.
        with(FadeTransition(stepDuration.divide(5.0), line)) {
            fromValue = 0.0
            toValue = 1.0
            play()
            setOnFinished {
                with(FadeTransition(stepDuration.multiply(6.0), line)) { fromValue = 1.0; toValue = 0.0; play() }
            }
        }

        view.root.children.add(1, line)
        view.root.children.add(bullet)
    }

}