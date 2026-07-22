package com.netsimx.gui;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Append-only log panel for simulation events (Module 12). Safe to call {@link #append} from any thread. */
public class LogConsole extends TextArea {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_LINES = 500;
    private int lineCount = 0;

    public LogConsole() {
        setEditable(false);
        setWrapText(true);
        setPrefRowCount(8);
        setStyle("-fx-control-inner-background: #10141c; -fx-text-fill: #cfe3f0; -fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");
    }

    public void append(String message) {
        String line = "[" + LocalTime.now().format(TIME_FMT) + "] " + message;
        if (Platform.isFxApplicationThread()) {
            appendInternal(line);
        } else {
            Platform.runLater(() -> appendInternal(line));
        }
    }

    private void appendInternal(String line) {
        appendText(line + "\n");
        lineCount++;
        if (lineCount > MAX_LINES) {
            int firstNewline = getText().indexOf('\n');
            if (firstNewline >= 0) {
                deleteText(0, firstNewline + 1);
                lineCount--;
            }
        }
        setScrollTop(Double.MAX_VALUE);
    }
}
