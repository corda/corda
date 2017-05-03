package net.corda.netmap

import javafx.animation.*
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.scene.shape.Circle
import javafx.scene.shape.Line
import javafx.util.Duration
import net.corda.core.crypto.commonName
import net.corda.core.utilities.ProgressTracker
import net.corda.node.services.config.NodeConfiguration
import net.corda.simulation.IRSSimulation
import net.corda.testing.node.MockNetwork
import org.bouncycastle.asn1.x500.X500Name
import java.util.*

class VisualiserViewModel {
    enum class Style {
        MAP, CIRCLE;

        override fun toString(): String {
            return name.toLowerCase().capitalize()
        }
    }

    inner class NodeWidget(val node: MockNetwork.MockNode, val innerDot: Circle, val outerDot: Circle, val longPulseDot: Circle,
                           val pulseAnim: Animation, val longPulseAnim: Animation,
                           val nameLabel: Label, val statusLabel: Label) {
        fun position(index: Int, nodeCoords: (node: MockNetwork.MockNode, index: Int) -> Pair<Double, Double>) {
            val (x, y) = nodeCoords(node, index)
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
    val nodesToWidgets = HashMap<MockNetwork.MockNode, NodeWidget>()

    var bankCount: Int = 0
    var serviceCount: Int = 0

    var stepDuration = Duration.millis(500.0)
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
            nodesToWidgets[bank]!!.position(index, when (displayStyle) {
                Style.MAP -> { node, _ -> nodeMapCoords(node) }
                Style.CIRCLE -> { _, index -> nodeCircleCoords(NetworkMapVisualiser.NodeType.BANK, index) }
            })
        }
        for ((index, serviceProvider) in (simulation.serviceProviders + simulation.regulators).withIndex()) {
            nodesToWidgets[serviceProvider]!!.position(index, when (displayStyle) {
                Style.MAP -> { node, _ -> nodeMapCoords(node) }
                Style.CIRCLE -> { _, index -> nodeCircleCoords(NetworkMapVisualiser.NodeType.SERVICE, index) }
            })
        }
    }

    fun nodeMapCoords(node: MockNetwork.MockNode): Pair<Double, Double> {
        // For an image of the whole world, we use:
        // return node.place.coordinate.project(mapImage.fitWidth, mapImage.fitHeight, 85.0511, -85.0511, -180.0, 180.0)

        // For Europe, our bounds are: (lng,lat)
        // bottom left: -23.2031,29.8406
        // top right: 33.0469,64.3209
        try {
            return node.place.coordinate.project(view.mapImage.fitWidth, view.mapImage.fitHeight, 64.3209, 29.8406, -23.2031, 33.0469)
        } catch(e: Exception) {
            throw Exception("Cannot project ${node.info.legalIdentity}", e)
        }
    }

    fun nodeCircleCoords(type: NetworkMapVisualiser.NodeType, index: Int): Pair<Double, Double> {
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
        return Pair(x, y)
    }

    fun createNodes() {
        bankCount = simulation.banks.size
        serviceCount = simulation.serviceProviders.size + simulation.regulators.size
        for ((index, bank) in simulation.banks.withIndex()) {
            nodesToWidgets[bank] = makeNodeWidget(bank, "bank", bank.configuration.displayName, NetworkMapVisualiser.NodeType.BANK, index)
        }
        for ((index, service) in simulation.serviceProviders.withIndex()) {
            nodesToWidgets[service] = makeNodeWidget(service, "network-service", service.configuration.displayName, NetworkMapVisualiser.NodeType.SERVICE, index)
        }
        for ((index, service) in simulation.regulators.withIndex()) {
            nodesToWidgets[service] = makeNodeWidget(service, "regulator", service.configuration.displayName, NetworkMapVisualiser.NodeType.SERVICE, index + simulation.serviceProviders.size)
        }
    }

    fun makeNodeWidget(forNode: MockNetwork.MockNode, type: String, label: X500Name = X500Name("CN=Bank of Bologna,OU=Corda QA Department,O=R3 CEV,L=New York,C=US"),
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

        val nameLabel = Label(label.toString())
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
            Style.CIRCLE -> widget.position(index, { _, index -> nodeCircleCoords(nodeType, index) })
            Style.MAP -> widget.position(index, { node, _ -> nodeMapCoords(node) })
        }
        return widget
    }

    fun fireBulletBetweenNodes(senderNode: MockNetwork.MockNode, destNode: MockNetwork.MockNode, startType: String, endType: String) {
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

private val NodeConfiguration.displayName: String get() = try {
    X500Name(myLegalName).commonName
} catch(ex: IllegalArgumentException) {
    myLegalName
}
