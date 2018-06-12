package net.corda.netmap

import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Platform
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
import javafx.scene.input.ZoomEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.Polygon
import javafx.scene.text.Font
import javafx.stage.Stage
import javafx.util.Duration
import net.corda.core.utilities.ProgressTracker
import net.corda.netmap.VisualiserViewModel.Style

data class TrackerWidget(val vbox: VBox, val cursorBox: Pane, val label: Label, val cursor: Polygon)

internal class VisualiserView {
    lateinit var root: Pane
    lateinit var stage: Stage
    lateinit var splitter: SplitPane
    lateinit var sidebar: VBox
    lateinit var resetButton: Button
    lateinit var nextButton: Button
    lateinit var runPauseButton: Button
    lateinit var simulateInitialisationCheckbox: CheckBox
    lateinit var styleChoice: ChoiceBox<Style>

    var dateLabel = Label("")
    var scrollPane: ScrollPane? = null
    var hideButton = Button("«").apply { styleClass += "hide-sidebar-button" }


    // -23.2031,29.8406,33.0469,64.3209
    val mapImage = ImageView(Image(NetworkMapVisualiser::class.java.getResourceAsStream("Europe.jpg")))

    // val iconImage = Image(NetworkMapVisualiser::class.java.getResourceAsStream("Corda logo.png"))

    // val titleString = "Corda Network Simulator"

    val backgroundColor: Color = mapImage.image.pixelReader.getColor(0, 0)

    val stageWidth = 1024.0
    val stageHeight = 768.0
    var defaultZoom = 0.7

    val bitmapWidth = 1900.0
    val bitmapHeight = 1900.0

    // This row height is controlled in the CSS and needs to match.
    val sideBarStepHeight = 40.0

    fun setup(runningPausedState: NetworkMapVisualiser.RunningPausedState,
              displayStyle: Style,
              presentationMode: Boolean) {
        NetworkMapVisualiser::class.java.getResourceAsStream("SourceSansPro-Regular.otf").use {
            Font.loadFont(it, 120.0)
        }
        if (displayStyle == Style.MAP) {
            mapImage.onZoom = EventHandler<javafx.scene.input.ZoomEvent> { event ->
                event.consume()
                mapImage.fitWidth = mapImage.fitWidth * event.zoomFactor
                mapImage.fitHeight = mapImage.fitHeight * event.zoomFactor
                //repositionNodes()
            }
        }
        scaleMap(displayStyle)
        root = Pane(mapImage)
        root.background = Background(BackgroundFill(backgroundColor, CornerRadii.EMPTY, Insets.EMPTY))
        scrollPane = buildScrollPane(backgroundColor, displayStyle)

        val vbox = makeTopBar(runningPausedState, displayStyle, presentationMode)
        StackPane.setAlignment(vbox, Pos.TOP_CENTER)

        // Now build the sidebar
        val defaultSplitterPosition = 0.3
        splitter = SplitPane(makeSidebar(), scrollPane)
        splitter.styleClass += "splitter"
        Platform.runLater {
            splitter.dividers[0].position = defaultSplitterPosition
        }
        VBox.setVgrow(splitter, Priority.ALWAYS)

        // And the left hide button.
        hideButton = makeHideButton(defaultSplitterPosition)

        val screenStack = VBox(vbox, StackPane(splitter, hideButton))
        screenStack.styleClass += "root-pane"
        stage.scene = Scene(screenStack, backgroundColor)
        stage.width = 1024.0
        stage.height = 768.0

        // Apply icon to window bar
        // stage.getIcons().add(iconImage)

        // Add title to window bar
        // stage.setTitle(titleString)
    }

    fun buildScrollPane(backgroundColor: Color, displayStyle: Style): ScrollPane {
        when (displayStyle) {
            Style.MAP -> {
                mapImage.fitWidth = bitmapWidth * defaultZoom
                mapImage.fitHeight = bitmapHeight * defaultZoom
                mapImage.onZoom = EventHandler<ZoomEvent> { event ->
                    event.consume()
                    mapImage.fitWidth = mapImage.fitWidth * event.zoomFactor
                    mapImage.fitHeight = mapImage.fitHeight * event.zoomFactor
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


    fun makeHideButton(defaultSplitterPosition: Double): Button {
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

    fun bindHideButtonPosition() {
        hideButton.translateXProperty().unbind()
        hideButton.translateXProperty().bind(splitter.dividers[0].positionProperty().multiply(splitter.widthProperty()).subtract(hideButton.widthProperty()))
    }

    fun scaleMap(displayStyle: Style) {
        when (displayStyle) {
            Style.MAP -> {
                mapImage.fitWidth = bitmapWidth * defaultZoom
                mapImage.fitHeight = bitmapHeight * defaultZoom
            }
            Style.CIRCLE -> {
                val scaleRatio = Math.min(stageWidth / bitmapWidth, stageHeight / bitmapHeight)
                mapImage.fitWidth = bitmapWidth * scaleRatio
                mapImage.fitHeight = bitmapHeight * scaleRatio
            }
        }
    }

    fun makeSidebar(): Node {
        sidebar = VBox()
        sidebar.styleClass += "sidebar"
        sidebar.isFillWidth = true
        val sp = ScrollPane(sidebar)
        sp.isFitToWidth = true
        sp.isFitToHeight = true
        sp.styleClass += "sidebar"
        sp.hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        sp.vbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
        sp.minWidth = 0.0
        return sp
    }

    fun makeTopBar(runningPausedState: NetworkMapVisualiser.RunningPausedState,
                   displayStyle: Style,
                   presentationMode: Boolean): VBox {
        nextButton = Button("Next").apply {
            styleClass += "button"
            styleClass += "next-button"
        }
        runPauseButton = Button(runningPausedState.buttonLabel.toString()).apply {
            styleClass += "button"
            styleClass += "run-button"
        }
        simulateInitialisationCheckbox = CheckBox("Simulate initialisation")
        resetButton = Button("Reset").apply {
            styleClass += "button"
            styleClass += "reset-button"
        }

        val displayStyles = FXCollections.observableArrayList<Style>()
        Style.values().forEach { displayStyles.add(it) }

        styleChoice = ChoiceBox(displayStyles).apply {
            styleClass += "choice"
            styleClass += "style-choice"
        }
        styleChoice.value = displayStyle

        val dropShadow = Pane().apply { styleClass += "drop-shadow-pane-horizontal"; minHeight = 8.0 }
        val logoImage = ImageView(javaClass.getResource("Corda logo.png").toExternalForm())
        logoImage.fitHeight = 65.0
        logoImage.isPreserveRatio = true
        val logoLabel = HBox(logoImage,
                Label("Network Simulator").apply { styleClass += "logo-label" }
        )
        logoLabel.spacing = 10.0
        logoLabel.alignment = Pos.CENTER_LEFT
        HBox.setHgrow(logoLabel, Priority.ALWAYS)
        logoLabel.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_PREF_SIZE)
        dateLabel = Label("").apply { styleClass += "date-label" }

        // Buttons area. In presentation mode there are no controls visible and you must use the keyboard.
        val hbox = if (presentationMode) {
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

    // TODO: Extract this to a real widget.
    fun buildProgressTrackerWidget(label: String, tracker: ProgressTracker): TrackerWidget {
        val allSteps: List<Pair<Int, ProgressTracker.Step>> = tracker.allSteps
        val stepsBox = VBox().apply {
            styleClass += "progress-tracker-widget-steps"
        }
        for ((indent, step) in allSteps) {
            val stepLabel = Label(step.label).apply { padding = Insets(0.0, 0.0, 0.0, indent * 15.0) }
            stepsBox.children += StackPane(stepLabel)
        }
        val trackerCurrentStep = tracker.currentStepRecursive
        val curStep = allSteps.indexOfFirst { it.second == trackerCurrentStep }
        val arrowSize = 7.0
        val cursor = Polygon(-arrowSize, -arrowSize, arrowSize, 0.0, -arrowSize, arrowSize).apply {
            styleClass += "progress-tracker-cursor"
            translateY = (Math.max(0, curStep - 1) * sideBarStepHeight) + (sideBarStepHeight / 2.0)
        }
        val cursorBox = Pane(cursor).apply {
            styleClass += "progress-tracker-cursor-box"
            minWidth = 25.0
        }
        val vbox: VBox?
        HBox.setHgrow(stepsBox, Priority.ALWAYS)
        val content = HBox(cursorBox, stepsBox)
        // Make the title bar
        val title = Label(label).apply { styleClass += "sidebar-title-label" }
        StackPane.setAlignment(title, Pos.CENTER_LEFT)
        vbox = VBox(StackPane(title), content)
        vbox.padding = Insets(0.0, 0.0, 25.0, 0.0)
        return TrackerWidget(vbox, cursorBox, title, cursor)
    }

    /**
     * Update the current display style. MUST only be called on the UI
     * thread.
     */
    fun updateDisplayStyle(displayStyle: Style) {
        requireNotNull(splitter)
        splitter.items.remove(scrollPane!!)
        scrollPane = buildScrollPane(backgroundColor, displayStyle)
        splitter.items.add(scrollPane!!)
        splitter.dividers[0].position = 0.3
        mapImage.isVisible = when (displayStyle) {
            Style.MAP -> true
            Style.CIRCLE -> false
        }
        // TODO: Can any current bullets be re-routed in flight?
    }
}
