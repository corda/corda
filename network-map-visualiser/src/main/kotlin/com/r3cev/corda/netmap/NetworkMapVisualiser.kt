/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package com.r3cev.corda.netmap

import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.then
import com.r3corda.core.utilities.BriefLogFormatter
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.node.internal.testing.IRSSimulation
import com.r3corda.node.internal.testing.MockNetwork
import com.r3corda.node.internal.testing.Simulation
import com.r3corda.node.services.network.InMemoryMessagingNetwork
import com.r3corda.node.services.network.NetworkMapService
import javafx.animation.*
import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.value.WritableValue
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.shape.Line
import javafx.scene.shape.Polygon
import javafx.scene.text.Font
import javafx.stage.Stage
import javafx.util.Duration
import rx.Scheduler
import rx.schedulers.Schedulers
import java.nio.file.Files
import java.nio.file.Paths
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import kotlin.concurrent.schedule
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.system.exitProcess

fun <T : Any> WritableValue<T>.keyValue(endValue: T, interpolator: Interpolator = Interpolator.EASE_OUT) = KeyValue(this, endValue, interpolator)

// TODO: This code is all horribly ugly. Refactor to use TornadoFX to clean it up.

class NetworkMapVisualiser : Application() {
    enum class NodeType {
        BANK, SERVICE
    }

    enum class Style {
        MAP, CIRCLE;

        override fun toString(): String {
            return name.toLowerCase().capitalize()
        }
    }

    enum class RunPauseButtonLabel {
        RUN, PAUSE;

        override fun toString(): String {
            return name.toLowerCase().capitalize()
        }
    }

    sealed class RunningPausedState {
        class Running(val tickTimer: TimerTask): RunningPausedState()
        class Paused(): RunningPausedState()

        val buttonLabel: RunPauseButtonLabel
            get() {
                return when (this) {
                    is RunningPausedState.Running -> RunPauseButtonLabel.PAUSE
                    is RunningPausedState.Paused -> RunPauseButtonLabel.RUN
                }
            }
    }

    val stageWidth = 1024.0
    val stageHeight = 768.0
    var defaultZoom = 0.7

    private lateinit var stage: Stage
    private lateinit var root: Pane

    val bitmapWidth = 1900.0
    val bitmapHeight = 1900.0
    var stepDuration = Duration.millis(500.0)

    init {
        BriefLogFormatter.initVerbose(InMemoryMessagingNetwork.MESSAGES_LOG_NAME)
    }

    var simulation = IRSSimulation(true, false, null)   // Manually pumped.

    val timer = Timer()

    val uiThread: Scheduler = Schedulers.from { Platform.runLater(it) }

    var displayStyle: Style = Style.MAP
    var bankCount: Int = 0
    var serviceCount: Int = 0

    override fun start(stage: Stage) {
        this.stage = stage
        buildScene(stage)

        // Update the white-backgrounded label indicating what protocol step it's up to.
        simulation.allProtocolSteps.observeOn(uiThread).subscribe { step: Pair<Simulation.SimulatedNode, ProgressTracker.Change> ->
            val (node, change) = step
            val label = nodesToWidgets[node]!!.statusLabel
            if (change is ProgressTracker.Change.Position) {
                // Fade in the status label if it's our first step.
                if (label.text == "") {
                    with(FadeTransition(Duration(150.0), label)) {
                        fromValue = 0.0
                        toValue = 1.0
                        play()
                    }
                }
                label.text = change.newStep.label
                if (change.newStep == ProgressTracker.DONE && change.tracker == change.tracker.topLevelTracker) {
                    runLater(500, -1) {
                        // Fade out the status label.
                        with(FadeTransition(Duration(750.0), label)) {
                            fromValue = 1.0
                            toValue = 0.0
                            setOnFinished { label.text = "" }
                            play()
                        }
                    }
                }
            } else if (change is ProgressTracker.Change.Rendering) {
                label.text = change.ofStep.label
            }
        }
        // Fire the message bullets between nodes.
        simulation.network.messagingNetwork.sentMessages.observeOn(uiThread).subscribe { msg: InMemoryMessagingNetwork.MessageTransfer ->
            val senderNode: MockNetwork.MockNode = simulation.network.addressToNode(msg.sender.myAddress)
            val destNode: MockNetwork.MockNode = simulation.network.addressToNode(msg.recipients as SingleMessageRecipient)

            if (transferIsInteresting(msg)) {
                nodesToWidgets[senderNode]!!.pulseAnim.play()
                fireBulletBetweenNodes(senderNode, destNode, "bank", "bank")
            }
        }
        // Pulse all parties in a trade when the trade completes
        simulation.doneSteps.observeOn(uiThread).subscribe { nodes: Collection<Simulation.SimulatedNode> ->
            nodes.forEach { nodesToWidgets[it]!!.longPulseAnim.play() }
        }

        if ("--circle" in parameters.raw)
            updateDisplayStyle(Style.CIRCLE)

        stage.setOnCloseRequest { exitProcess(0) }
        //stage.isMaximized = true
        stage.show()
    }

    fun runLater(startAfter: Int, delayBetween: Int, body: () -> Unit) {
        if (delayBetween != -1) {
            timer.scheduleAtFixedRate(startAfter.toLong(), delayBetween.toLong()) {
                Platform.runLater {
                    body()
                }
            }
        } else {
            timer.schedule(startAfter.toLong()) {
                Platform.runLater {
                    body()
                }
            }
        }
    }

    // -23.2031,29.8406,33.0469,64.3209
    private val mapImage = ImageView(Image(NetworkMapVisualiser::class.java.getResourceAsStream("Europe.jpg")))
    private var scrollPane: ScrollPane? = null
    private lateinit var splitter: SplitPane

    private fun buildScene(stage: Stage) {
        NetworkMapVisualiser::class.java.getResourceAsStream("SourceSansPro-Regular.otf").use {
            Font.loadFont(it, 120.0)
        }
        when (displayStyle) {
            Style.MAP -> {
                mapImage.fitWidth = bitmapWidth * defaultZoom
                mapImage.fitHeight = bitmapHeight * defaultZoom
                mapImage.onZoom = EventHandler<javafx.scene.input.ZoomEvent> { event ->
                    event.consume()
                    mapImage.fitWidth = mapImage.fitWidth * event.zoomFactor
                    mapImage.fitHeight = mapImage.fitHeight * event.zoomFactor
                    repositionNodes()
                }
            }
            Style.CIRCLE -> {
                val scaleRatio = Math.min(stageWidth / bitmapWidth, stageHeight / bitmapHeight)
                mapImage.fitWidth = bitmapWidth * scaleRatio
                mapImage.fitHeight = bitmapHeight * scaleRatio
            }
        }

        val backgroundColor: Color = mapImage.image.pixelReader.getColor(0, 0)
        root = Pane(mapImage)
        root.background = Background(BackgroundFill(backgroundColor, CornerRadii.EMPTY, Insets.EMPTY))
        createNodes()

        scrollPane = buildScrollPane(backgroundColor)

        val vbox = makeTopBar()

        StackPane.setAlignment(vbox, Pos.TOP_CENTER)

        // Now build the sidebar
        val defaultSplitterPosition = 0.3
        splitter = SplitPane(buildSidebar(), scrollPane)
        splitter.styleClass += "splitter"
        Platform.runLater {
            splitter.dividers[0].position = defaultSplitterPosition
        }
        VBox.setVgrow(splitter, Priority.ALWAYS)

        // And the left hide button.
        val hideButton = makeHideButton(defaultSplitterPosition)

        val screenStack = VBox(vbox, StackPane(splitter, hideButton))
        screenStack.styleClass += "root-pane"
        stage.scene = Scene(screenStack, backgroundColor)

        // Spacebar advances simulation by one step.
        stage.scene.accelerators[KeyCodeCombination(KeyCode.SPACE)] = Runnable { onNextInvoked() }

        reloadStylesheet(stage)

        stage.focusedProperty().addListener { value, old, new ->
            if (new)
                reloadStylesheet(stage)
        }

        stage.width = 1024.0
        stage.height = 768.0
    }

    private val hideButton = Button("«").apply { styleClass += "hide-sidebar-button" }
    fun bindHideButtonPosition() {
        hideButton.translateXProperty().unbind()
        hideButton.translateXProperty().bind(splitter.dividers[0].positionProperty().multiply(splitter.widthProperty()).subtract(hideButton.widthProperty()))
    }

    private fun makeHideButton(defaultSplitterPosition: Double): Button {
        var hideButtonToggled = false
        hideButton.isFocusTraversable = false
        hideButton.setOnAction {
            if (!hideButtonToggled) {
                hideButton.translateXProperty().unbind()
                Timeline(
                        KeyFrame(Duration.millis(500.0),
                                splitter.dividers[0].positionProperty().keyValue(0.0),
                                hideButton.translateXProperty().keyValue(0.0),
                                hideButton.rotateProperty().keyValue(180.0)
                        )
                ).play()
            } else {
                bindHideButtonPosition()
                Timeline(
                        KeyFrame(Duration.millis(500.0),
                                splitter.dividers[0].positionProperty().keyValue(defaultSplitterPosition),
                                hideButton.rotateProperty().keyValue(0.0)
                        )
                ).play()
            }
            hideButtonToggled = !hideButtonToggled
        }
        bindHideButtonPosition()
        StackPane.setAlignment(hideButton, Pos.TOP_LEFT)
        return hideButton
    }

    var started = false
    private fun startSimulation() {
        if (!started) {
            simulation.start()
            started = true
        }
    }

    private fun makeTopBar(): VBox {
        val nextButton = Button("Next").apply {
            styleClass += "button"
            styleClass += "next-button"
        }

        var runningPausedState: RunningPausedState = RunningPausedState.Paused()
        val runPauseButton = Button(runningPausedState.buttonLabel.toString()).apply {
            styleClass += "button"
            styleClass += "run-button"
        }

        val simulateInitialisationCheckbox = CheckBox("Simulate initialisation")

        val resetButton = Button("Reset").apply {
            setOnAction {
                reset()
            }
            styleClass += "button"
            styleClass += "reset-button"
        }

        nextButton.setOnAction {
            if (!simulateInitialisationCheckbox.isSelected && !simulation.networkInitialisationFinished.isDone) {
                skipNetworkInitialisation()
            } else {
                onNextInvoked()
            }
        }

        simulation.networkInitialisationFinished.then {
            simulateInitialisationCheckbox.isVisible = false
        }

        runPauseButton.setOnAction {
            val oldRunningPausedState = runningPausedState
            val newRunningPausedState = when (oldRunningPausedState) {
                is RunningPausedState.Running -> {
                    oldRunningPausedState.tickTimer.cancel()

                    nextButton.isDisable = false
                    resetButton.isDisable = false

                    RunningPausedState.Paused()
                }
                is RunningPausedState.Paused -> {
                    val tickTimer = timer.scheduleAtFixedRate(stepDuration.toMillis().toLong(), stepDuration.toMillis().toLong()) {
                        Platform.runLater {
                            onNextInvoked()
                        }
                    }

                    nextButton.isDisable = true
                    resetButton.isDisable = true

                    if (!simulateInitialisationCheckbox.isSelected && !simulation.networkInitialisationFinished.isDone) {
                        skipNetworkInitialisation()
                    }

                    RunningPausedState.Running(tickTimer)
                }
            }

            runPauseButton.text = newRunningPausedState.buttonLabel.toString()
            runningPausedState = newRunningPausedState

        }


        val displayStyles = FXCollections.observableArrayList<Style>()
        Style.values().forEach { displayStyles.add(it) }

        val styleChoice = ChoiceBox(displayStyles).apply {
            styleClass += "choice"
            styleClass += "style-choice"
        }
        styleChoice.value = displayStyle
        styleChoice.selectionModel.selectedItemProperty()
                .addListener { ov, value, newValue -> updateDisplayStyle(newValue) }

        val dropShadow = Pane().apply { styleClass += "drop-shadow-pane-horizontal"; minHeight = 8.0 }
        val logoImage = ImageView(javaClass.getResource("R3 logo.png").toExternalForm())
        logoImage.fitHeight = 65.0
        logoImage.isPreserveRatio = true
        val logoLabel = HBox(logoImage, VBox(
                Label("D I S T R I B U T E D   L E D G E R   G R O U P").apply { styleClass += "dlg-label" },
                Label("Network Simulator").apply { styleClass += "logo-label" }
        ))
        logoLabel.spacing = 10.0
        HBox.setHgrow(logoLabel, Priority.ALWAYS)
        logoLabel.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_PREF_SIZE)
        val dateLabel = Label("").apply { styleClass += "date-label" }
        simulation.dateChanges.observeOn(uiThread).subscribe { dateLabel.text = it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }

        // Buttons area. In presentation mode there are no controls visible and you must use the keyboard.
        val hbox = if ("--presentation-mode" in parameters.raw) {
            HBox(logoLabel, dateLabel).apply { styleClass += "controls-hbox" }
        } else {
            HBox(logoLabel, dateLabel, simulateInitialisationCheckbox, runPauseButton, nextButton, resetButton, styleChoice).apply { styleClass += "controls-hbox" }
        }
        hbox.styleClass += "fat-buttons"
        hbox.spacing = 20.0
        hbox.alignment = Pos.CENTER_RIGHT
        hbox.padding = Insets(10.0, 20.0, 10.0, 20.0)
        val vbox = VBox(hbox, dropShadow)
        vbox.styleClass += "controls-vbox"
        vbox.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE)
        vbox.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_PREF_SIZE)
        return vbox
    }

    private fun reset() {
        simulation.stop()
        simulation = IRSSimulation(true, false, null)
        started = false
        start(this.stage)
    }

    private fun skipNetworkInitialisation() {
        startSimulation()
        while (!simulation.networkInitialisationFinished.isDone) {
            iterateSimulation()
        }
    }

    private fun onNextInvoked() {
        if (started) {
            iterateSimulation()
        } else {
            startSimulation()
        }
    }

    private fun iterateSimulation() {
        // Loop until either we ran out of things to do, or we sent an interesting message.
        while (true) {
            val transfer: InMemoryMessagingNetwork.MessageTransfer = simulation.iterate() ?: break
            if (transferIsInteresting(transfer))
                break
            else
                System.err.println("skipping boring $transfer")
        }
    }

    private fun transferIsInteresting(transfer: InMemoryMessagingNetwork.MessageTransfer): Boolean {
        // Loopback messages are boring.
        if (transfer.sender.myAddress == transfer.recipients) return false
        // Network map push acknowledgements are boring.
        if (NetworkMapService.PUSH_ACK_PROTOCOL_TOPIC in transfer.message.topic) return false

        return true
    }

    private fun buildScrollPane(backgroundColor: Color): ScrollPane {
        when (displayStyle) {
            Style.MAP -> {
                mapImage.fitWidth = bitmapWidth * defaultZoom
                mapImage.fitHeight = bitmapHeight * defaultZoom
                mapImage.onZoom = EventHandler<javafx.scene.input.ZoomEvent> { event ->
                    event.consume()
                    mapImage.fitWidth = mapImage.fitWidth * event.zoomFactor
                    mapImage.fitHeight = mapImage.fitHeight * event.zoomFactor
                    repositionNodes()
                }
            }
            Style.CIRCLE -> {
                val scaleRatio = Math.min(stageWidth / bitmapWidth, stageHeight / bitmapHeight)
                mapImage.fitWidth = bitmapWidth * scaleRatio
                mapImage.fitHeight = bitmapHeight * scaleRatio
            }
        }
        return ScrollPane(Group(root)).apply {
            when (displayStyle) {
                Style.MAP -> {
                    hvalue = 0.4
                    vvalue = 0.7
                }
                Style.CIRCLE -> {
                    hvalue = 0.0
                    vvalue = 0.0
                }
            }
            hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            vbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
            isPannable = true
            isFocusTraversable = false
            style = "-fx-background-color: " + colorToRgb(backgroundColor)
            styleClass += "edge-to-edge"
        }
    }

    private fun colorToRgb(color: Color): String {
        val builder = StringBuilder()

        builder.append("rgb(")
        builder.append(Math.round(color.red * 256))
        builder.append(",")
        builder.append(Math.round(color.green * 256))
        builder.append(",")
        builder.append(Math.round(color.blue * 256))
        builder.append(")")

        return builder.toString()
    }

    private fun repositionNodes() {
        for ((index, bank) in simulation.banks.withIndex()) {
            nodesToWidgets[bank]!!.position(index, when (displayStyle) {
                Style.MAP -> { node, index -> nodeMapCoords(node) }
                Style.CIRCLE -> { node, index -> nodeCircleCoords(NodeType.BANK, index) }
            })
        }
        for ((index, serviceProvider) in (simulation.serviceProviders + simulation.regulators).withIndex()) {
            nodesToWidgets[serviceProvider]!!.position(index, when (displayStyle) {
                Style.MAP -> { node, index -> nodeMapCoords(node) }
                Style.CIRCLE -> { node, index -> nodeCircleCoords(NodeType.SERVICE, index) }
            })
        }
    }

    private val trackerBoxes = HashMap<ProgressTracker, Pane>()
    private val doneTrackers = ArrayList<ProgressTracker>()
    private fun buildSidebar(): Node {
        val sidebar = VBox()
        sidebar.styleClass += "sidebar"
        sidebar.isFillWidth = true
        simulation.allProtocolSteps.observeOn(uiThread).subscribe { step: Pair<Simulation.SimulatedNode, ProgressTracker.Change> ->
            val (node, change) = step

            if (change is ProgressTracker.Change.Position) {
                val tracker = change.tracker.topLevelTracker
                if (change.newStep == ProgressTracker.DONE) {
                    if (change.tracker == tracker) {
                        // Protocol done; schedule it for removal in a few seconds. We batch them up to make nicer
                        // animations.
                        println("Protocol done for ${node.info.identity.name}")
                        doneTrackers += tracker
                    } else {
                        // Subprotocol is done; ignore it.
                    }
                } else if (!trackerBoxes.containsKey(tracker)) {
                    // New protocol started up; add.
                    val extraLabel = simulation.extraNodeLabels[node]
                    val label = if (extraLabel != null) "${node.storage.myLegalIdentity.name}: $extraLabel" else node.storage.myLegalIdentity.name
                    val widget = buildProgressTrackerWidget(label, tracker.topLevelTracker)
                    trackerBoxes[tracker] = widget
                    sidebar.children += widget
                }
            }
        }

        Timer().scheduleAtFixedRate(0, 500) {
            Platform.runLater {
                for (tracker in doneTrackers) {
                    val pane = trackerBoxes[tracker]!!
                    // Slide the other tracker widgets up and over this one.
                    val slideProp = SimpleDoubleProperty(0.0)
                    slideProp.addListener { obv -> pane.padding = Insets(0.0, 0.0, slideProp.value, 0.0) }
                    val timeline = Timeline(
                            KeyFrame(Duration(250.0),
                                    KeyValue(pane.opacityProperty(), 0.0),
                                    KeyValue(slideProp, -pane.height - 50.0)  // Subtract the bottom padding gap.
                            )
                    )
                    timeline.setOnFinished {
                        val vbox = trackerBoxes.remove(tracker)
                        sidebar.children.remove(vbox)
                    }
                    timeline.play()
                }
                doneTrackers.clear()
            }
        }
        val sp = ScrollPane(sidebar)
        sp.isFitToWidth = true
        sp.isFitToHeight = true
        sp.styleClass += "sidebar"
        sp.hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        sp.vbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        sp.minWidth = 0.0
        return sp
    }

    // TODO: Extract this to a real widget.
    private fun buildProgressTrackerWidget(label: String, tracker: ProgressTracker): Pane {
        val allSteps: List<Pair<Int, ProgressTracker.Step>> = tracker.allSteps
        val stepsBox = VBox().apply {
            styleClass += "progress-tracker-widget-steps"
        }
        for ((indent, step) in allSteps) {
            val stepLabel = Label(step.label).apply { padding = Insets(0.0, 0.0, 0.0, indent * 15.0) }
            stepsBox.children += StackPane(stepLabel)
        }
        val arrowSize = 7.0
        val cursor = Polygon(-arrowSize, -arrowSize, arrowSize, 0.0, -arrowSize, arrowSize).apply {
            styleClass += "progress-tracker-cursor"
        }
        val cursorBox = Pane(cursor).apply {
            styleClass += "progress-tracker-cursor-box"
            minWidth = 25.0
        }
        var curStep = allSteps.indexOfFirst { it.second == tracker.currentStep }
        Platform.runLater {
            val stepHeight = cursorBox.height / allSteps.size
            cursor.translateY = (curStep * stepHeight) + 20.0
        }
        val vbox: VBox?
        tracker.changes.observeOn(uiThread).subscribe { step: ProgressTracker.Change ->
            val stepHeight = cursorBox.height / allSteps.size
            if (step is ProgressTracker.Change.Position) {
                // Figure out the index of the new step.
                curStep = allSteps.indexOfFirst { it.second == step.newStep }
                // Animate the cursor to the right place.
                with(TranslateTransition(Duration(350.0), cursor)) {
                    fromY = cursor.translateY
                    toY = (curStep * stepHeight) + 22.5
                    play()
                }
            } else if (step is ProgressTracker.Change.Structural) {
                val new = buildProgressTrackerWidget(label, tracker)
                val prevWidget = trackerBoxes[step.tracker] ?: throw AssertionError("No previous widget for tracker")
                val i = (prevWidget.parent as VBox).children.indexOf(trackerBoxes[step.tracker])
                (prevWidget.parent as VBox).children[i] = new
                trackerBoxes[step.tracker] = new
            }
        }
        HBox.setHgrow(stepsBox, Priority.ALWAYS)
        val content = HBox(cursorBox, stepsBox)
        // Make the title bar
        val title = Label(label).apply { styleClass += "sidebar-title-label" }
        StackPane.setAlignment(title, Pos.CENTER_LEFT)
        vbox = VBox(StackPane(title), content)
        vbox.padding = Insets(0.0, 0.0, 25.0, 0.0)
        return vbox
    }

    fun nodeMapCoords(node: MockNetwork.MockNode): Pair<Double, Double> {
        // For an image of the whole world, we use:
        // return node.place.coordinate.project(mapImage.fitWidth, mapImage.fitHeight, 85.0511, -85.0511, -180.0, 180.0)

        // For Europe, our bounds are: (lng,lat)
        // bottom left: -23.2031,29.8406
        // top right: 33.0469,64.3209
        try {
            return node.place.coordinate.project(mapImage.fitWidth, mapImage.fitHeight, 64.3209, 29.8406, -23.2031, 33.0469)
        } catch(e: Exception) {
            throw Exception("Cannot project ${node.info.identity}", e)
        }
    }

    fun nodeCircleCoords(type: NodeType, index: Int): Pair<Double, Double> {
        val stepRad: Double = when(type) {
            NodeType.BANK -> 2 * Math.PI / bankCount
            NodeType.SERVICE -> (2 * Math.PI / serviceCount)
        }
        val tangentRad: Double = stepRad * index + when(type) {
            NodeType.BANK -> 0.0
            NodeType.SERVICE -> Math.PI / 2
        }
        val radius = when (type) {
            NodeType.BANK -> Math.min(stageWidth, stageHeight) / 3.5
            NodeType.SERVICE -> Math.min(stageWidth, stageHeight) / 8
        }
        val xOffset = -220
        val yOffset = -80
        val circleX = stageWidth / 2 + xOffset
        val circleY = stageHeight / 2 + yOffset
        val x: Double = radius * Math.cos(tangentRad) + circleX;
        val y: Double = radius * Math.sin(tangentRad) + circleY;
        return Pair(x, y)
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

    private val nodesToWidgets = HashMap<MockNetwork.MockNode, NodeWidget>()

    private fun createNodes() {
        bankCount = simulation.banks.size
        serviceCount = simulation.serviceProviders.size + simulation.regulators.size
        for ((index, bank) in simulation.banks.withIndex()) {
            nodesToWidgets[bank] = makeNodeWidget(bank, "bank", bank.configuration.myLegalName, NodeType.BANK, index)
        }
        for ((index, service) in simulation.serviceProviders.withIndex()) {
            nodesToWidgets[service] = makeNodeWidget(service, "network-service", service.configuration.myLegalName, NodeType.SERVICE, index)
        }
        for ((index, service) in simulation.regulators.withIndex()) {
            nodesToWidgets[service] = makeNodeWidget(service, "regulator", service.configuration.myLegalName, NodeType.SERVICE, index + simulation.serviceProviders.size)
        }
    }

    fun makeNodeWidget(forNode: MockNetwork.MockNode, type: String, label: String = "Bank of Bologna",
                       nodeType: NodeType, index: Int): NodeWidget {
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
        root.children += outerDot
        root.children += longPulseOuterDot
        root.children += innerDot

        val nameLabel = Label(label)
        val nameLabelRect = StackPane(nameLabel).apply {
            styleClass += "node-label"
            alignment = Pos.CENTER_RIGHT
            // This magic min width depends on the longest label of all nodes we may have, which we aren't calculating.
            // TODO: Dynamically adjust it depending on the longest label to display.
            minWidth = 250.0
        }
        root.children += nameLabelRect

        val statusLabel = Label("")
        val statusLabelRect = StackPane(statusLabel).apply { styleClass += "node-status-label" }
        root.children += statusLabelRect

        val widget = NodeWidget(forNode, innerDot, outerDot, longPulseOuterDot, pulseAnim, longPulseAnim, nameLabel, statusLabel)
        when (displayStyle) {
            Style.CIRCLE -> widget.position(index, { node, index -> nodeCircleCoords(nodeType, index) } )
            Style.MAP -> widget.position(index, { node, index -> nodeMapCoords(node) })
        }
        return widget
    }

    private fun fireBulletBetweenNodes(senderNode: MockNetwork.MockNode, destNode: MockNetwork.MockNode, startType: String, endType: String) {
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

        root.children.add(1, line)
        root.children.add(bullet)
    }

    private fun reloadStylesheet(stage: Stage) {
        stage.scene.stylesheets.clear()

        // Enable hot reload without needing to rebuild.
        val mikesCSS = "/Users/mike/Source/R3/r3dlg-prototyping/network-explorer/src/main/resources/com/r3cev/corda/netmap/styles.css"
        if (Files.exists(Paths.get(mikesCSS)))
            stage.scene.stylesheets.add("file://$mikesCSS")
        else
            stage.scene.stylesheets.add(NetworkMapVisualiser::class.java.getResource("styles.css").toString())
    }

    /**
     * Update the current display style. MUST only be called on the UI
     * thread.
     */
    fun updateDisplayStyle(style: Style) {
        displayStyle = style
        requireNotNull(splitter)
        splitter.items.remove(scrollPane!!)
        val backgroundColor: Color = mapImage.image.pixelReader.getColor(0, 0)
        scrollPane = buildScrollPane(backgroundColor)
        splitter.items.add(scrollPane!!)
        splitter.dividers[0].position = 0.3
        repositionNodes()
        bindHideButtonPosition()
        mapImage.isVisible = when (style) {
            Style.MAP -> true
            Style.CIRCLE -> false
        }
        stage.scene.accelerators[KeyCodeCombination(KeyCode.SPACE)] = Runnable { onNextInvoked() }
        // TODO: Can any current bullets be re-routed in flight?
    }
}

fun main(args: Array<String>) {
    BriefLogFormatter.init()
    Application.launch(NetworkMapVisualiser::class.java, *args)
}
