package net.corda.networkbuilder;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.StageStyle;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CountDownLatch;

public class GuiUtils {

    public static void showException(String title, String message, Throwable exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initStyle(StageStyle.UTILITY);
        alert.setTitle("Exception");
        alert.setHeaderText(title);
        alert.setContentText(message);
        if (exception != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            exception.printStackTrace(pw);
            String exceptionText = sw.toString();
            TextArea textArea = new TextArea(exceptionText);
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);
            Label label = new Label("Details:");
            GridPane.setVgrow(textArea, Priority.ALWAYS);
            GridPane.setHgrow(textArea, Priority.ALWAYS);
            GridPane expContent = new GridPane();
            expContent.setMaxWidth(Double.MAX_VALUE);
            expContent.add(label, 0, 0);
            expContent.add(textArea, 0, 1);
            alert.getDialogPane().setExpandableContent(expContent);
        }
        alert.showAndWait();
    }

    public static void showAndQuit(String title, String message, Throwable exception) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        new JFXPanel();
        Platform.runLater(() -> {
            showException(title, message, exception);
            countDownLatch.countDown();
            System.exit(1);
        });
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
        }
    }
}